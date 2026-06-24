package com.cascade.domain.retry;

import java.time.Duration;

public final class NoRetry implements RetryPolicy {

    public static final NoRetry INSTANCE = new NoRetry();

    private NoRetry() {}

    @Override
    public boolean shouldRetry(int attemptNumber, Throwable error) { return false; }

    @Override
    public Duration backoff(int attemptNumber) { return Duration.ZERO; }

    @Override
    public int maxAttempts() { return 1; }
}
