package com.cascade.persistence;

import com.cascade.domain.event.RunEvent;
import com.cascade.domain.event.RunEventListener;
import com.cascade.persistence.entity.RunEventEntity;
import com.cascade.persistence.repository.RunEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Observer: writes every RunEvent to the audit log table.
 * Registered as a RunEventListener so the engine fires it automatically.
 */
@Component
public class JpaAuditLogger implements RunEventListener {

    private static final Logger log = LoggerFactory.getLogger(JpaAuditLogger.class);

    private final RunEventRepository repo;

    public JpaAuditLogger(RunEventRepository repo) {
        this.repo = repo;
    }

    @Override
    public void onEvent(RunEvent event) {
        if (event.runId() == null || event.runId().isBlank()) return; // internal engine events without runId
        try {
            repo.save(new RunEventEntity(
                    event.id(),
                    event.runId(),
                    event.type(),
                    event.stepId(),
                    event.timestamp(),
                    event.detail()));
        } catch (Exception e) {
            log.warn("Failed to persist audit event {} for run {}: {}", event.type(), event.runId(), e.getMessage());
        }
    }
}
