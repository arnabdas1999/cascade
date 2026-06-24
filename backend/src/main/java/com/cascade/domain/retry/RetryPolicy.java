package com.cascade.domain.retry;

import java.time.Duration;

/** Strategy: pluggable retry behavior attached to any Step. */
public interface RetryPolicy {

    /** True if the engine should retry after this failure. */
    boolean shouldRetry(int attemptNumber, Throwable error);

    /** How long to wait before attempt {@code attemptNumber + 1}. */
    Duration backoff(int attemptNumber);

    int maxAttempts();
}
