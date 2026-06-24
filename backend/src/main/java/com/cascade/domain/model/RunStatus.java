package com.cascade.domain.model;

public enum RunStatus {
    PENDING,
    RUNNING,
    WAITING,       // e.g. mid-delay or waiting for external event
    COMPLETED,
    FAILED,
    COMPENSATING,  // running undo logic
    COMPENSATED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == COMPENSATED;
    }
}
