package com.dpi.platform.controller;

import com.dpi.platform.dto.RequirementCreateRequest;
import com.dpi.platform.dto.TechDecisionRequest;
import com.dpi.platform.dto.TechnologyCreateRequest;
import com.dpi.platform.dto.UserStoryCreateRequest;
import com.dpi.platform.model.Requirement;
import com.dpi.platform.model.Technology;
import com.dpi.platform.model.TechnologyDecision;
import com.dpi.platform.model.UserStory;
import com.dpi.platform.service.RequirementTechService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class RequirementTechController {

    private final RequirementTechService requirementTechService;

    @Autowired
    public RequirementTechController(RequirementTechService requirementTechService) {
        this.requirementTechService = requirementTechService;
    }

    @PostMapping("/requirements")
    public ResponseEntity<Requirement> createRequirement(@Valid @RequestBody RequirementCreateRequest request) {
        Requirement created = requirementTechService.createRequirement(request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/requirements/{id}")
    public ResponseEntity<Requirement> getRequirement(@PathVariable UUID id) {
        return ResponseEntity.ok(requirementTechService.getRequirement(id));
    }

    @PostMapping("/requirements/{id}/user-story")
    public ResponseEntity<UserStory> attachUserStory(
            @PathVariable UUID id,
            @Valid @RequestBody UserStoryCreateRequest request) {
        return ResponseEntity.ok(requirementTechService.attachUserStory(id, request));
    }

    @GetMapping("/requirements/{id}/user-story")
    public ResponseEntity<UserStory> getUserStory(@PathVariable UUID id) {
        return ResponseEntity.ok(requirementTechService.getUserStory(id));
    }

    @PostMapping("/technologies")
    public ResponseEntity<Technology> createTechnology(@Valid @RequestBody TechnologyCreateRequest request) {
        Technology created = requirementTechService.createTechnology(request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/technologies/{id}")
    public ResponseEntity<Technology> getTechnology(@PathVariable UUID id) {
        return ResponseEntity.ok(requirementTechService.getTechnology(id));
    }

    @GetMapping("/technologies")
    public ResponseEntity<List<Technology>> getAllTechnologies() {
        return ResponseEntity.ok(requirementTechService.getAllTechnologies());
    }

    @PostMapping("/requirements/{id}/candidates")
    public ResponseEntity<Void> addTechnologyCandidates(
            @PathVariable UUID id,
            @RequestBody List<UUID> technologyIds) {
        requirementTechService.addTechnologyCandidates(id, technologyIds);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/requirements/{id}/candidates")
    public ResponseEntity<List<Technology>> getTechnologyCandidates(@PathVariable UUID id) {
        return ResponseEntity.ok(requirementTechService.getTechnologyCandidates(id));
    }

    @PostMapping("/requirements/{id}/decisions")
    public ResponseEntity<TechnologyDecision> selectTechnology(
            @PathVariable UUID id,
            @Valid @RequestBody TechDecisionRequest request) {
        return ResponseEntity.ok(requirementTechService.selectTechnology(id, request));
    }

    @GetMapping("/requirements/{id}/decisions")
    public ResponseEntity<List<TechnologyDecision>> getTechnologyDecisions(@PathVariable UUID id) {
        return ResponseEntity.ok(requirementTechService.getTechnologyDecisions(id));
    }
}
