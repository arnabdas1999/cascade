package com.cascade.persistence.repository;

import com.cascade.persistence.entity.WorkflowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRepository extends JpaRepository<WorkflowEntity, String> {}
