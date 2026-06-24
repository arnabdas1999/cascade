package com.cascade.persistence;

import com.cascade.domain.model.RunStatus;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction over run-state persistence.
 * Postgres is just one implementation — swappable for tests or future stores.
 */
public interface StateStore {

    void saveSnapshot(RunSnapshot snapshot);

    Optional<RunSnapshot> load(String runId);

    /** Used by RecoveryService on startup to resume interrupted runs. */
    List<RunSnapshot> findByStatus(RunStatus status);
}
