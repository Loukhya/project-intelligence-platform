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
@Table(name = "features")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "epic_id", nullable = false)
    @JsonIgnore
    private Epic epic;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeatureStatus status;

    @ManyToMany
    @JoinTable(
        name = "feature_dependencies",
        joinColumns = @JoinColumn(name = "source_feature_id"),
        inverseJoinColumns = @JoinColumn(name = "target_feature_id")
    )
    @JsonIgnore
    @Builder.Default
    private Set<Feature> dependencies = new HashSet<>();

    @ManyToMany(mappedBy = "dependencies")
    @JsonIgnore
    @Builder.Default
    private Set<Feature> blockedFeatures = new HashSet<>();

    @Column(name = "progress")
    @Builder.Default
    private Double progress = 0.0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getEpicId() {
        return epic != null ? epic.getId() : null;
    }

    public UUID getProjectId() {
        return epic != null ? epic.getProjectId() : null;
    }
}
