package com.dpi.platform.controller;

import com.dpi.platform.dto.ProjectCreateRequest;
import com.dpi.platform.dto.ProjectFullDTO;
import com.dpi.platform.dto.VisionUpdateRequest;
import com.dpi.platform.model.Project;
import com.dpi.platform.model.Vision;
import com.dpi.platform.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    @Autowired
    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<Project> createProject(@Valid @RequestBody ProjectCreateRequest request) {
        Project created = projectService.createProject(request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> getProject(@PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getProject(id));
    }

    @GetMapping
    public ResponseEntity<List<Project>> getAllProjects() {
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    @PostMapping("/{id}/vision")
    public ResponseEntity<Vision> setProjectVision(
            @PathVariable UUID id,
            @Valid @RequestBody VisionUpdateRequest request) {
        return ResponseEntity.ok(projectService.setProjectVision(id, request));
    }

    @GetMapping("/{id}/vision")
    public ResponseEntity<Vision> getProjectVision(@PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getProjectVision(id));
    }

    @GetMapping("/{id}/full")
    public ResponseEntity<ProjectFullDTO> getProjectFull(@PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getProjectFull(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/seed")
    public ResponseEntity<Project> seedSampleProject() {
        Project seeded = projectService.seedSampleProject();
        return new ResponseEntity<>(seeded, HttpStatus.CREATED);
    }

    @PostMapping("/{id}/restructure")
    public ResponseEntity<Project> restructureMathProject(@PathVariable UUID id) {
        Project restructured = projectService.restructureMathProject(id);
        return ResponseEntity.ok(restructured);
    }
}