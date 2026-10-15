package com.dpi.platform.controller;

import com.dpi.platform.dto.IntelligenceQueryRequest;
import com.dpi.platform.dto.IntelligenceQueryResponse;
import com.dpi.platform.service.ProjectIntelligenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class IntelligenceController {

    private final ProjectIntelligenceService intelligenceService;

    @Autowired
    public IntelligenceController(ProjectIntelligenceService intelligenceService) {
        this.intelligenceService = intelligenceService;
    }

    @PostMapping("/{projectId}/query")
    public ResponseEntity<IntelligenceQueryResponse> queryProject(
            @PathVariable UUID projectId,
            @RequestBody IntelligenceQueryRequest request) {
        
        IntelligenceQueryResponse response = intelligenceService.queryProject(projectId, request.getQuery());
        return ResponseEntity.ok(response);
    }
}
