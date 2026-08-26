package com.ticket.ticket_fail.service;

import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OptimisticLockRetryReservationService {

    private static final int MAX_ATTEMPTS = 20;
    private static final int BACKOFF_MILLIS = 5;

    private final OptimisticLockReservationService delegate;

    /**
     * Stage 3b: retry the callers that lose the version race.
     *
     * There is deliberately no @Transactional here. A version conflict surfaces
     * at commit time, which happens inside the proxy around the delegate and
     * therefore after its method has already returned. Only a caller sitting
     * outside that transaction can catch the failure, and each retry must open
     * a fresh transaction so it reads the updated version rather than the stale
     * one it already holds.
     *
     * Being sold out is a final answer, not a race, so it is never retried.
     */
    public void reserve(long performanceId, String userId) {
        ObjectOptimisticLockingFailureException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                delegate.reserve(performanceId, userId);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                lastFailure = e;
                backOff();
            }
        }

        throw lastFailure;
    }

    private void backOff() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(BACKOFF_MILLIS) + 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying", e);
        }
    }
}