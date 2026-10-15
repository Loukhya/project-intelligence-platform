package com.dpi.platform.controller;

import com.dpi.platform.model.CodeCandidate;
import com.dpi.platform.model.Project;
import com.dpi.platform.model.Task;
import com.dpi.platform.model.TaskStatus;
import com.dpi.platform.repository.CodeCandidateRepository;
import com.dpi.platform.repository.TaskRepository;
import com.dpi.platform.service.AiGatewayService;
import com.dpi.platform.service.CodeVerificationService;
import com.dpi.platform.service.GitService;
import com.dpi.platform.service.JavaParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.dpi.platform.event.CodeAcceptedEvent;
import com.dpi.platform.event.CodeGeneratedEvent;
import org.springframework.context.ApplicationEventPublisher;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AiAgentController {

    private static final Logger log = LoggerFactory.getLogger(AiAgentController.class);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AiGatewayService aiGatewayService;

    @Autowired
    private CodeVerificationService codeVerificationService;

    @Autowired
    private CodeCandidateRepository codeCandidateRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private GitService gitService;

    @Autowired
    private com.dpi.platform.service.CodeExecutionService codeExecutionService;

    @PostMapping("/candidates/{id}/run-tests")
    public ResponseEntity<CodeCandidate> runCandidateTests(@PathVariable UUID id) {
        log.info("Running Unit Tests for Candidate ID: {}", id);
        CodeCandidate candidate = codeExecutionService.runTestSuite(id);
        return ResponseEntity.ok(candidate);
    }

    @PostMapping("/tasks/{id}/assign-ai")
    public ResponseEntity<CodeCandidate> assignAiToTask(@PathVariable UUID id) {
        log.info("Assigning AI agent to Task ID: {}", id);
        CodeCandidate candidate = aiGatewayService.assignAiToTask(id);
        eventPublisher.publishEvent(new CodeGeneratedEvent(this, candidate));
        return ResponseEntity.ok(candidate);
    }

    @GetMapping("/tasks/{id}/candidates")
    public ResponseEntity<List<CodeCandidate>> getTaskCandidates(@PathVariable UUID id) {
        List<CodeCandidate> candidates = codeCandidateRepository.findByTask_Id(id);
        return ResponseEntity.ok(candidates);
    }

    @PostMapping("/candidates/{id}/verify")
    public ResponseEntity<CodeCandidate> verifyCandidate(@PathVariable UUID id) {
        log.info("Verifying Code Candidate ID: {}", id);
        CodeCandidate candidate = codeVerificationService.verifyCandidate(id);
        return ResponseEntity.ok(candidate);
    }

    @PostMapping("/candidates/{id}/approve")
    @Transactional
    public ResponseEntity<CodeCandidate> approveCandidate(@PathVariable UUID id) {
        log.info("Approving Code Candidate ID: {}", id);
        CodeCandidate candidate = codeCandidateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + id));

        if (!"PENDING".equals(candidate.getStatus())) {
            throw new IllegalStateException("Candidate is not in PENDING status.");
        }

        Task task = candidate.getTask();
        Project project = task.getFeature().getEpic().getProject();
        String localPath = project.getLocalPath();

        if (localPath == null || localPath.trim().isEmpty()) {
            throw new IllegalStateException("Project local path is not configured.");
        }

        String filePath = candidate.getFilePath();
        String testFilePath = candidate.getTestFilePath();
        String cleanLocalPath = localPath.replace("\\", "/");

        if (filePath.startsWith(cleanLocalPath)) {
            filePath = filePath.substring(cleanLocalPath.length());
        }
        while (filePath.startsWith("/") || filePath.startsWith("\\")) {
            filePath = filePath.substring(1);
        }

        if (testFilePath != null) {
            if (testFilePath.startsWith(cleanLocalPath)) {
                testFilePath = testFilePath.substring(cleanLocalPath.length());
            }
            while (testFilePath.startsWith("/") || testFilePath.startsWith("\\")) {
                testFilePath = testFilePath.substring(1);
            }
        }

        File targetFile = new File(localPath, filePath);
        File parentDir = targetFile.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        try {
            // Permanently write the proposed changes to the project source directory
            Files.writeString(targetFile.toPath(), candidate.getProposedContent());
            log.info("Successfully wrote candidate code to: {}", targetFile.getAbsolutePath());

            // Permanently write the generated unit test suite if it was generated
            if (testFilePath != null && candidate.getTestProposedContent() != null) {
                File targetTestFile = new File(localPath, testFilePath);
                targetTestFile.getParentFile().mkdirs();
                Files.writeString(targetTestFile.toPath(), candidate.getTestProposedContent());
                log.info("Successfully wrote candidate test code to: {}", targetTestFile.getAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to write candidate source or test code to disk: {}", targetFile.getAbsolutePath(), e);
            throw new RuntimeException("Failed to write code to file system: " + e.getMessage(), e);
        }

        // Commit and Push to repository if git is configured
        if (project.getRepositoryUrl() != null && !project.getRepositoryUrl().trim().isEmpty()) {
            try {
                gitService.commitAndPush(localPath, "AI implemented Task " + task.getId() + ": " + task.getTitle());
                log.info("Committed and pushed AI changes to Git.");
            } catch (Exception e) {
                log.warn("Git commit/push failed: {}. Continuing anyway.", e.getMessage());
            }
        }

        // Update database records
        candidate.setStatus("APPROVED");
        codeCandidateRepository.save(candidate);

        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);

        // Publish event for asynchronous post-processing (Git scan & chat history cleaning)
        eventPublisher.publishEvent(new CodeAcceptedEvent(this, candidate));

        return ResponseEntity.ok(candidate);
    }

    @PostMapping("/candidates/{id}/reject")
    @Transactional
    public ResponseEntity<CodeCandidate> rejectCandidate(@PathVariable UUID id) {
        log.info("Rejecting Code Candidate ID: {}", id);
        CodeCandidate candidate = codeCandidateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + id));

        if (!"PENDING".equals(candidate.getStatus())) {
            throw new IllegalStateException("Candidate is not in PENDING status.");
        }

        // Update candidate status
        candidate.setStatus("REJECTED");
        codeCandidateRepository.save(candidate);

        // Revert task status back to BACKLOG
        Task task = candidate.getTask();
        task.setStatus(TaskStatus.BACKLOG);
        taskRepository.save(task);

        return ResponseEntity.ok(candidate);
    }
}
