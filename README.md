# Ticket_fail

A ticket booking service built to break, and then fixed four different ways.

Booking a seat looks like one action, but underneath it is a read, a decision and a write.
Under load, two requests can both read the same seat count, both decide a seat is free, and
both take it. This repository reproduces that failure, then applies pessimistic locking,
optimistic locking, and optimistic locking with retry — keeping every version runnable side
by side so the trade-offs can be compared rather than described.

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

`./gradlew test`, in-memory H2, 10 seats.

| Strategy | Requests | Succeeded | Sold out | Conflicts | Final counter |
|---|---|---|---|---|---|
| No locking | 100 | 100 | 0 | — | 1 |
| Pessimistic lock | 100 | 10 | 90 | — | 10 |
| Optimistic lock, no retry | 100 | 10 | 59 | 31 | 10 |
| Optimistic lock, no retry | 10 | 1 | 0 | 9 | 1 |
| Optimistic lock with retry | 10 | 10 | 0 | 0 | 10 |

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

## The condition that flips the result

Optimistic locking looked healthy at 100 requests: every seat filled, and the 31 rejected
callers were replaced by the queue behind them. At 10 requests against 10 seats that buffer
disappears and the picture inverts — one booking succeeds, nine seats sit empty, and the
test still passes. The data is consistent and the service is useless.

That is the case for retry, and it only appears once demand stops exceeding supply.

## Running it

```bash
./gradlew test
```

Each strategy has its own concurrency test. SQL logging shows the difference directly:
`for update` on the locking query, `where id=? and version=?` on the optimistic updates.

## On the duplication

The four services repeat a three-line method, and optimistic locking uses a separate entity
rather than adding `@Version` to the shared one. A version column on the shared entity would
impose version checks on the unsafe service too, destroying the failure this project exists
to demonstrate. Keeping every strategy independently runnable is the point.

## Still to come

- Postgres in Docker, to observe lock waits directly and make throughput comparisons
  meaningful
- Redis-based distributed locking
