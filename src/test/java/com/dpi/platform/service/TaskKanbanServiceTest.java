package com.dpi.platform.service;

import com.dpi.platform.dto.TaskCreateRequest;
import com.dpi.platform.dto.TaskStatusUpdateRequest;
import com.dpi.platform.model.*;
import com.dpi.platform.repository.FeatureRepository;
import com.dpi.platform.repository.TaskAuditLogRepository;
import com.dpi.platform.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task & Kanban Service Tests")
class TaskKanbanServiceTest {

    @Mock
    private FeatureRepository featureRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskAuditLogRepository taskAuditLogRepository;

    @InjectMocks
    private TaskKanbanServiceImpl taskKanbanService;

    @Nested
    @DisplayName("Task Creation Tests")
    class TaskCreation {

        @Test
        @DisplayName("Given valid task request, when creating task, then return saved task with Backlog status and 0.0 actual hours")
        void shouldCreateTaskSuccessfully() {
            // Arrange
            UUID featureId = UUID.randomUUID();
            Feature feature = Feature.builder().id(featureId).name("User Login").build();

            TaskCreateRequest request = TaskCreateRequest.builder()
                    .featureId(featureId)
                    .title("Create User Entity")
                    .description("Define Hibernate mapping")
                    .priority(Priority.HIGH)
                    .estimatedHours(4.0)
                    .build();

            Task savedTask = Task.builder()
                    .id(UUID.randomUUID())
                    .feature(feature)
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .priority(request.getPriority())
                    .estimatedHours(request.getEstimatedHours())
                    .actualHours(0.0)
                    .status(TaskStatus.BACKLOG)
                    .build();

            when(featureRepository.findById(featureId)).thenReturn(Optional.of(feature));
            when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

            // Act
            Task result = taskKanbanService.createTask(request);

            // Assert
            assertNotNull(result);
            assertEquals("Create User Entity", result.getTitle());
            assertEquals(TaskStatus.BACKLOG, result.getStatus());
            assertEquals(0.0, result.getActualHours());
            verify(featureRepository, times(1)).findById(featureId);
            verify(taskRepository, times(1)).save(any(Task.class));
        }
    }

    @Nested
    @DisplayName("Kanban Transitions & Audit Logs Tests")
    class KanbanTransitions {

        @Test
        @DisplayName("Given a task, when transitioning status on Kanban, then update status and log audit transition")
        void shouldTransitionTaskStatusAndLogAudit() {
            // Arrange
            UUID taskId = UUID.randomUUID();
            Feature feature = Feature.builder().id(UUID.randomUUID()).name("User Login").build();
            Task task = Task.builder()
                    .id(taskId)
                    .feature(feature)
                    .title("Create User Entity")
                    .status(TaskStatus.BACKLOG)
                    .actualHours(0.0)
                    .build();

            TaskStatusUpdateRequest request = TaskStatusUpdateRequest.builder()
                    .newStatus(TaskStatus.IN_PROGRESS)
                    .actualHours(2.5)
                    .build();

            TaskAuditLog savedLog = TaskAuditLog.builder()
                    .id(UUID.randomUUID())
                    .taskId(taskId)
                    .previousStatus(TaskStatus.BACKLOG)
                    .newStatus(TaskStatus.IN_PROGRESS)
                    .timestamp(LocalDateTime.now())
                    .build();

            when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(taskAuditLogRepository.save(any(TaskAuditLog.class))).thenReturn(savedLog);

            // Act
            Task result = taskKanbanService.transitionTaskStatus(taskId, request);

            // Assert
            assertNotNull(result);
            assertEquals(TaskStatus.IN_PROGRESS, result.getStatus());
            assertEquals(2.5, result.getActualHours());
            verify(taskRepository, times(1)).findById(taskId);
            verify(taskRepository, times(1)).save(task);
            verify(taskAuditLogRepository, times(1)).save(any(TaskAuditLog.class));
        }
    }
}
