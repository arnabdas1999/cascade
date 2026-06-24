package com.cascade.domain.retry;

import java.time.Duration;

public final class FixedDelayRetry implements RetryPolicy {

    private final int maxAttempts;
    private final Duration delay;

    public FixedDelayRetry(int maxAttempts, Duration delay) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        this.maxAttempts = maxAttempts;
        this.delay = delay;
    }

    @Override
    public boolean shouldRetry(int attemptNumber, Throwable error) {
        return attemptNumber < maxAttempts - 1;
    }

    @Override
    public Duration backoff(int attemptNumber) { return delay; }

    @Override
    public int maxAttempts() { return maxAttempts; }
}
