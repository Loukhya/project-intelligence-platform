package com.dpi.platform.service;

import com.dpi.platform.dto.TaskCreateRequest;
import com.dpi.platform.dto.TaskStatusUpdateRequest;
import com.dpi.platform.model.Task;
import com.dpi.platform.model.TaskAuditLog;
import java.util.List;
import java.util.UUID;

public interface TaskKanbanService {
    Task createTask(TaskCreateRequest request);
    Task getTask(UUID id);
    Task transitionTaskStatus(UUID taskId, TaskStatusUpdateRequest request);
    List<TaskAuditLog> getTaskAuditLogs(UUID taskId);
}
