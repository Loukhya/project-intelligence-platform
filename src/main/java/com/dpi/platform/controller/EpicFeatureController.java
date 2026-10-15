package com.dpi.platform.controller;

import com.dpi.platform.dto.EpicCreateRequest;
import com.dpi.platform.dto.FeatureCreateRequest;
import com.dpi.platform.model.Epic;
import com.dpi.platform.model.Feature;
import com.dpi.platform.model.FeatureStatus;
import com.dpi.platform.service.EpicFeatureService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class EpicFeatureController {

    private final EpicFeatureService epicFeatureService;

    @Autowired
    public EpicFeatureController(EpicFeatureService epicFeatureService) {
        this.epicFeatureService = epicFeatureService;
    }

    @PostMapping("/epics")
    public ResponseEntity<Epic> createEpic(@Valid @RequestBody EpicCreateRequest request) {
        Epic created = epicFeatureService.createEpic(request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/epics/{id}")
    public ResponseEntity<Epic> getEpic(@PathVariable UUID id) {
        return ResponseEntity.ok(epicFeatureService.getEpic(id));
    }

    @PostMapping("/features")
    public ResponseEntity<Feature> createFeature(@Valid @RequestBody FeatureCreateRequest request) {
        Feature created = epicFeatureService.createFeature(request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/features/{id}")
    public ResponseEntity<Feature> getFeature(@PathVariable UUID id) {
        return ResponseEntity.ok(epicFeatureService.getFeature(id));
    }

    @PostMapping("/features/{id}/dependencies/{targetId}")
    public ResponseEntity<Void> addFeatureDependency(
            @PathVariable UUID id,
            @PathVariable UUID targetId) {
        epicFeatureService.addFeatureDependency(id, targetId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/features/{id}/transition")
    public ResponseEntity<Feature> transitionFeatureStatus(
            @PathVariable UUID id,
            @RequestParam FeatureStatus status) {
        Feature updated = epicFeatureService.transitionFeatureStatus(id, status);
        return ResponseEntity.ok(updated);
    }
}
