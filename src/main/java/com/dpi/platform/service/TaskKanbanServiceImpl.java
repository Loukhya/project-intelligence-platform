package com.dpi.platform.service;

import com.dpi.platform.dto.TaskCreateRequest;
import com.dpi.platform.dto.TaskStatusUpdateRequest;
import com.dpi.platform.model.*;
import com.dpi.platform.repository.FeatureRepository;
import com.dpi.platform.repository.RequirementRepository;
import com.dpi.platform.repository.TaskAuditLogRepository;
import com.dpi.platform.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Manages task CRUD and Kanban board operations.
 * @Requirement(id="33333333-aaaa-5555-bbbb-cccccccccccc")
 */
@Service
public class TaskKanbanServiceImpl implements TaskKanbanService {

    private final FeatureRepository featureRepository;
    private final TaskRepository taskRepository;
    private final TaskAuditLogRepository taskAuditLogRepository;
    private final RequirementRepository requirementRepository;
    private final ProgressService progressService;

    @Autowired
    public TaskKanbanServiceImpl(FeatureRepository featureRepository,
                                  TaskRepository taskRepository,
                                  TaskAuditLogRepository taskAuditLogRepository,
                                  RequirementRepository requirementRepository,
                                  ProgressService progressService) {
        this.featureRepository = featureRepository;
        this.taskRepository = taskRepository;
        this.taskAuditLogRepository = taskAuditLogRepository;
        this.requirementRepository = requirementRepository;
        this.progressService = progressService;
    }

    @Override
    public Task createTask(TaskCreateRequest request) {
        Feature feature = featureRepository.findById(request.getFeatureId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature not found"));

        Task.TaskBuilder builder = Task.builder()
                .feature(feature)
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority())
                .estimatedHours(request.getEstimatedHours())
                .actualHours(0.0)
                .status(TaskStatus.BACKLOG);

        // Link task to a specific requirement if provided (for traceability)
        if (request.getRequirementId() != null) {
            Requirement requirement = requirementRepository.findById(request.getRequirementId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requirement not found"));
            builder.requirement(requirement);
        }

        Task savedTask = taskRepository.save(builder.build());
        if (progressService != null) {
            progressService.recalculateFeatureProgress(feature.getId());
        }
        return savedTask;
    }

    @Override
    public Task getTask(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    @Override
    public Task transitionTaskStatus(UUID taskId, TaskStatusUpdateRequest request) {
        Task task = getTask(taskId);
        TaskStatus previousStatus = task.getStatus();
        TaskStatus newStatus = request.getNewStatus();

        if (previousStatus != newStatus) {
            TaskAuditLog auditLog = TaskAuditLog.builder()
                    .taskId(taskId)
                    .previousStatus(previousStatus)
                    .newStatus(newStatus)
                    .timestamp(LocalDateTime.now())
                    .build();
            taskAuditLogRepository.save(auditLog);
            task.setStatus(newStatus);

            // Auto-set startedAt on first transition to IN_PROGRESS
            if (newStatus == TaskStatus.IN_PROGRESS && task.getStartedAt() == null) {
                task.setStartedAt(LocalDateTime.now());
            }

            // Auto-set completedAt on transition to COMPLETED
            if (newStatus == TaskStatus.COMPLETED) {
                task.setCompletedAt(LocalDateTime.now());
            }

            // Clear completedAt if moving back from COMPLETED
            if (previousStatus == TaskStatus.COMPLETED && newStatus != TaskStatus.COMPLETED) {
                task.setCompletedAt(null);
            }
        }

        if (request.getActualHours() != null) {
            task.setActualHours(request.getActualHours());
        }

        Task savedTask = taskRepository.save(task);
        if (progressService != null && savedTask.getFeature() != null) {
            progressService.recalculateFeatureProgress(savedTask.getFeature().getId());
        }
        return savedTask;
    }

    @Override
    public List<TaskAuditLog> getTaskAuditLogs(UUID taskId) {
        return taskAuditLogRepository.findByTaskId(taskId);
    }
}
