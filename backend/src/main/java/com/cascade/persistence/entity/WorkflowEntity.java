package com.cascade.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "workflows")
public class WorkflowEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "definition_json", nullable = false, columnDefinition = "TEXT")
    private String definitionJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public WorkflowEntity() {}

    public WorkflowEntity(String id, String name, String definitionJson, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.definitionJson = definitionJson;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDefinitionJson() { return definitionJson; }
    public void setDefinitionJson(String definitionJson) { this.definitionJson = definitionJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
