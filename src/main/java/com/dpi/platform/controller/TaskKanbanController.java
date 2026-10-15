package com.dpi.platform.controller;

import com.dpi.platform.dto.TaskCreateRequest;
import com.dpi.platform.dto.TaskStatusUpdateRequest;
import com.dpi.platform.model.Task;
import com.dpi.platform.model.TaskAuditLog;
import com.dpi.platform.service.TaskKanbanService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TaskKanbanController {

    private final TaskKanbanService taskKanbanService;

    @Autowired
    public TaskKanbanController(TaskKanbanService taskKanbanService) {
        this.taskKanbanService = taskKanbanService;
    }

    @PostMapping("/tasks")
    public ResponseEntity<Task> createTask(@Valid @RequestBody TaskCreateRequest request) {
        Task created = taskKanbanService.createTask(request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<Task> getTask(@PathVariable UUID id) {
        return ResponseEntity.ok(taskKanbanService.getTask(id));
    }

    @PostMapping("/tasks/{id}/transition")
    public ResponseEntity<Task> transitionTaskStatus(
            @PathVariable UUID id,
            @Valid @RequestBody TaskStatusUpdateRequest request) {
        return ResponseEntity.ok(taskKanbanService.transitionTaskStatus(id, request));
    }

    @GetMapping("/tasks/{id}/audit")
    public ResponseEntity<List<TaskAuditLog>> getTaskAuditLogs(@PathVariable UUID id) {
        return ResponseEntity.ok(taskKanbanService.getTaskAuditLogs(id));
    }
}
