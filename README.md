# Ticket_fail

A ticket booking service built to break, and then fixed four different ways.

Booking a seat looks like one action, but underneath it is a read, a decision and a write.
Under load, two requests can both read the same seat count, both decide a seat is free, and
both take it. This repository reproduces that failure, then applies pessimistic locking,
optimistic locking, optimistic locking with retry, and a Redis distributed lock — keeping
every version runnable side by side so the trade-offs can be compared rather than described.

## The problem

```java
Performance performance = performanceRepository.findById(performanceId).orElseThrow(...);
performance.reserve();
```

`@Transactional` does not make this safe. A transaction hides uncommitted work; it does not
make concurrent callers take turns. Each thread reads a value that is legitimately current
when it reads it, and JPA's dirty checking then writes `SET reserved_seats = <value
computed in Java>` rather than an atomic increment — so writes overwrite each other instead
of accumulating.

## Results

`./gradlew test`, 10 seats. The first five rows ran against in-memory H2; the distributed
lock row ran against Postgres and Redis in Docker.

| Strategy | Requests | Succeeded | Sold out | Conflicts | Final counter |
|---|---|---|---|---|---|
| No locking | 100 | 100 | 0 | — | 1 |
| Pessimistic lock | 100 | 10 | 90 | — | 10 |
| Optimistic lock, no retry | 100 | 10 | 59 | 31 | 10 |
| Optimistic lock, no retry | 10 | 1 | 0 | 9 | 1 |
| Optimistic lock with retry | 10 | 10 | 0 | 0 | 10 |
| Distributed lock (Redis) | 100 | 10 | 90 | — | 10 |

Row one is the bug: 100 bookings recorded, one seat accounted for, and the sold-out check
never fired because the counter never approached the limit. Figures vary between runs —
the same test has also produced a final counter of 3.

## The strategies

**No locking** is kept deliberately broken as the baseline. Its test asserts that the
counter *disagrees* with the number of successful bookings, pinning the bug down rather
than describing it.

**Pessimistic locking** uses `SELECT ... FOR UPDATE`, so the row is locked when read rather
than when written. The unsafe version was also taking a lock — every `UPDATE` locks the row
it touches — but far too late, after an incorrect value had already been computed. This
does not add a lock; it moves the existing one earlier. The cost is throughput: requests
for the same performance are serialised.

**Optimistic locking** locks nothing. A `@Version` column makes JPA append `WHERE id = ? AND
version = ?` to every update; a caller that lost the race updates zero rows and gets an
exception. It is conflict detection after the fact, not mutual exclusion. Correctness holds,
but a rejected caller is turned away while a seat is still free — which is why those
rejections are counted separately from genuine sold-out responses.

**Retry** wraps the above and carries no `@Transactional` of its own. A version conflict
surfaces at commit time, inside the proxy and after the delegate has already returned, so
only a caller outside that transaction can catch it — and each attempt needs a fresh
transaction to read the updated version. Sold-out is final and never retried.

**The distributed lock** moves mutual exclusion out of the database entirely. Redisson takes
a lock keyed on the performance id, and only the holder proceeds to the booking itself.

The ordering is the whole design. `DistributedLockReservationService` carries no
`@Transactional`; it acquires the lock, delegates to `ReservationExecutor`, and releases
only after that call has returned and its transaction has committed. Acquiring and
releasing inside a single transactional method would free the lock before the commit,
letting the next holder read a seat count that the previous one had already changed but not
yet written — a lock that is held throughout and still overbooks. The retry wrapper has the
same shape for the same reason: anything that must observe or outlive a commit has to sit
outside the transaction boundary.

`tryLock` is called with a wait time but no lease time, so Redisson's watchdog extends the
lease while the work is in progress. A fixed lease is the obvious choice and the wrong one:
if the transaction outlives it, the lock expires mid-flight and a second caller enters. With
the watchdog, renewal stops when the process dies, and the lock is released around thirty
seconds later — the same protection against a stuck holder, without the expiry that a fixed
lease invites.

## The condition that flips the result

Optimistic locking looked healthy at 100 requests: every seat filled, and the 31 rejected
callers were replaced by the queue behind them. At 10 requests against 10 seats that buffer
disappears and the picture inverts — one booking succeeds, nine seats sit empty, and the
test still passes. The data is consistent and the service is useless.

That is the case for retry, and it only appears once demand stops exceeding supply.

## What the table cannot separate

Pessimistic and distributed locking produce identical rows, within about 60ms of each other
across 100 requests. On a single instance against a local database, they genuinely are
interchangeable, and no amount of presentation makes them look otherwise.

The difference is what a waiting caller holds. A pessimistic lock is taken by the database,
so a caller queues while holding a connection from the pool; a distributed lock is taken
before the database is touched, so a caller queues holding nothing and takes a connection
only once it is its turn. Under contention, the pessimistic version exhausts the pool while
the distributed one does not.

Reproducing that here would mean shrinking the pool and padding the transaction with a
sleep until the two diverge — numbers manufactured by the test rather than observed from
it. The honest statement is that the difference exists, that this setup is too small to
show it, and that a load test through the HTTP layer with a realistic transaction duration
is what would.

## Infrastructure notes

Two problems during the move from H2 to Postgres, both worth recording because neither was
visible from the application's own error.

An existing Windows PostgreSQL service was bound to port 5432 alongside Docker's port
forward. Windows permits the overlapping bind; the service answered first, and the
application authenticated against a database that had never heard of its user. Every check
passed in isolation — the container was healthy, the credentials matched the compose file,
and `psql` inside the container connected fine over the local socket, which skips password
authentication. `netstat` showed two listeners on the port, which was the only evidence
that pointed anywhere.

`redisson-spring-boot-starter:3.52.0` targets Spring Boot 3.5 and fails against 4.1 with a
`ClassNotFoundException` for `RedisProperties`, an autoconfiguration class that moved
packages between the two versions. Since `RedissonConfig` builds the client explicitly,
the starter contributed nothing that was being used; dropping it and Spring Data Redis in
favour of plain `org.redisson:redisson` removed the autoconfiguration path and the version
coupling with it.

## Running it

```bash
docker compose up -d
./gradlew test
```

Each strategy has its own concurrency test. SQL logging shows the difference directly:
`for update` on the locking query, `where id=? and version=?` on the optimistic updates.

## On the duplication

The five services repeat a three-line method, and optimistic locking uses a separate entity
rather than adding `@Version` to the shared one. A version column on the shared entity would
impose version checks on the unsafe service too, destroying the failure this project exists
to demonstrate. Keeping every strategy independently runnable is the point.

## Still to come

- An HTTP layer, so the services can be driven as an API rather than only from tests
- A k6 load test against that API, to measure p95 under sustained contention and give the
  pessimistic and distributed strategies a load profile that can actually separate them