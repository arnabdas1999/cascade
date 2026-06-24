package com.cascade.persistence.repository;

import com.cascade.domain.model.RunStatus;
import com.cascade.persistence.entity.RunSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RunSnapshotRepository extends JpaRepository<RunSnapshotEntity, String> {

    List<RunSnapshotEntity> findByStatus(RunStatus status);

    List<RunSnapshotEntity> findByWorkflowId(String workflowId);
}
