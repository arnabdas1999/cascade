package com.cascade.domain.retry;

import java.time.Duration;

public final class ExponentialBackoffRetry implements RetryPolicy {

    private final int maxAttempts;
    private final Duration initialDelay;
    private final double multiplier;
    private final Duration maxDelay;

    public ExponentialBackoffRetry(int maxAttempts, Duration initialDelay, double multiplier, Duration maxDelay) {
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.multiplier = multiplier;
        this.maxDelay = maxDelay;
    }

    @Override
    public boolean shouldRetry(int attemptNumber, Throwable error) {
        return attemptNumber < maxAttempts - 1;
    }

    @Override
    public Duration backoff(int attemptNumber) {
        long ms = (long) (initialDelay.toMillis() * Math.pow(multiplier, attemptNumber));
        return Duration.ofMillis(Math.min(ms, maxDelay.toMillis()));
    }

    @Override
    public int maxAttempts() { return maxAttempts; }
}
