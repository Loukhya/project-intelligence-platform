package com.dpi.platform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "code_candidates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CodeCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    @JsonIgnore
    private Task task;

    @Column(name = "file_path", nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "original_content", columnDefinition = "TEXT")
    private String originalContent;

    @Column(name = "proposed_content", nullable = false, columnDefinition = "TEXT")
    private String proposedContent;

    @Column(name = "diff_content", columnDefinition = "TEXT")
    private String diffContent;

    @Column(nullable = false, length = 50)
    private String status; // PENDING, APPROVED, REJECTED

    @Column(name = "verification_log", columnDefinition = "TEXT")
    private String verificationLog;

    @Column(name = "test_file_path", columnDefinition = "TEXT")
    private String testFilePath;

    @Column(name = "test_proposed_content", columnDefinition = "TEXT")
    private String testProposedContent;

    @Column(name = "test_execution_log", columnDefinition = "TEXT")
    private String testExecutionLog;

    @Column(name = "test_results_summary", columnDefinition = "TEXT")
    private String testResultsSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
    }

    public UUID getTaskId() {
        return task != null ? task.getId() : null;
    }
}
