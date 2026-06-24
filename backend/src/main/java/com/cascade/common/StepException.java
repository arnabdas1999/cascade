package com.cascade.common;

public class StepException extends Exception {

    private final String stepId;
    private final boolean retryable;

    public StepException(String stepId, String message, boolean retryable) {
        super(message);
        this.stepId = stepId;
        this.retryable = retryable;
    }

    public StepException(String stepId, String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.stepId = stepId;
        this.retryable = retryable;
    }

    public String getStepId() { return stepId; }
    public boolean isRetryable() { return retryable; }
}
