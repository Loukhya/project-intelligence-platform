package com.dpi.platform.controller;

import com.dpi.platform.model.CodeMapping;
import com.dpi.platform.model.MethodCall;
import com.dpi.platform.service.JavaParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ProjectScanController {

    private final JavaParserService javaParserService;

    @Autowired
    public ProjectScanController(JavaParserService javaParserService) {
        this.javaParserService = javaParserService;
    }

    @PostMapping("/projects/{id}/scan")
    public ResponseEntity<Void> scanProjectCodebase(@PathVariable UUID id) {
        javaParserService.scanProject(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/{id}/mappings")
    public ResponseEntity<List<CodeMapping>> getProjectMappings(@PathVariable UUID id) {
        List<CodeMapping> mappings = javaParserService.getProjectMappings(id);
        return ResponseEntity.ok(mappings);
    }

    @GetMapping("/projects/{id}/method-calls")
    public ResponseEntity<List<MethodCall>> getProjectMethodCalls(@PathVariable UUID id) {
        List<MethodCall> calls = javaParserService.getProjectMethodCalls(id);
        return ResponseEntity.ok(calls);
    }

    @GetMapping("/requirements/{id}/code-area")
    public ResponseEntity<List<CodeMapping>> getRequirementCodeArea(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "2") int maxDepth) {
        List<CodeMapping> mappings = javaParserService.getCodeArea(id, maxDepth);
        return ResponseEntity.ok(mappings);
    }
}
