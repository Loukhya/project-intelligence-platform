package com.dpi.platform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "requirements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Requirement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_id", nullable = false)
    @JsonIgnore
    private Feature feature;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Complexity complexity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequirementStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequirementType type;

    @Column(name = "estimated_effort")
    private Double estimatedEffort;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "acceptance_criteria", columnDefinition = "TEXT")
    private String acceptanceCriteria;

    @OneToOne(mappedBy = "requirement", cascade = CascadeType.ALL, orphanRemoval = true)
    private UserStory userStory;

    @ManyToMany
    @JoinTable(
        name = "requirement_candidates",
        joinColumns = @JoinColumn(name = "requirement_id"),
        inverseJoinColumns = @JoinColumn(name = "technology_id")
    )
    @JsonIgnore
    @Builder.Default
    private Set<Technology> candidates = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = RequirementStatus.DRAFT;
        }
        if (type == null) {
            type = RequirementType.FUNCTIONAL;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getFeatureId() {
        return feature != null ? feature.getId() : null;
    }

    public UUID getProjectId() {
        return feature != null ? feature.getProjectId() : null;
    }
}
