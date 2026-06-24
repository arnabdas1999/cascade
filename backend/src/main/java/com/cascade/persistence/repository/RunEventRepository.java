package com.cascade.persistence.repository;

import com.cascade.persistence.entity.RunEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RunEventRepository extends JpaRepository<RunEventEntity, String> {

    List<RunEventEntity> findByRunIdOrderByTimestampAsc(String runId);
}
