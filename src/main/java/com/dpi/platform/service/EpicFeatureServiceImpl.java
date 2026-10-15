package com.dpi.platform.service;

import com.dpi.platform.dto.EpicCreateRequest;
import com.dpi.platform.dto.FeatureCreateRequest;
import com.dpi.platform.model.*;
import com.dpi.platform.repository.EpicRepository;
import com.dpi.platform.repository.FeatureRepository;
import com.dpi.platform.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class EpicFeatureServiceImpl implements EpicFeatureService {

    private final ProjectRepository projectRepository;
    private final EpicRepository epicRepository;
    private final FeatureRepository featureRepository;

    @Autowired
    public EpicFeatureServiceImpl(ProjectRepository projectRepository,
                                   EpicRepository epicRepository,
                                   FeatureRepository featureRepository) {
        this.projectRepository = projectRepository;
        this.epicRepository = epicRepository;
        this.featureRepository = featureRepository;
    }

    @Override
    public Epic createEpic(EpicCreateRequest request) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        Epic epic = Epic.builder()
                .project(project)
                .name(request.getName())
                .description(request.getDescription())
                .priority(request.getPriority())
                .status(EpicStatus.PLANNED)
                .build();

        return epicRepository.save(epic);
    }

    @Override
    public Epic getEpic(UUID id) {
        return epicRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Epic not found"));
    }

    @Override
    public Feature createFeature(FeatureCreateRequest request) {
        Epic epic = epicRepository.findById(request.getEpicId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Epic not found"));

        Feature feature = Feature.builder()
                .epic(epic)
                .name(request.getName())
                .description(request.getDescription())
                .priority(request.getPriority())
                .status(FeatureStatus.BACKLOG)
                .build();

        return featureRepository.save(feature);
    }

    @Override
    public Feature getFeature(UUID id) {
        return featureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature not found"));
    }

    @Override
    public void addFeatureDependency(UUID sourceId, UUID targetId) {
        Feature source = getFeature(sourceId);
        Feature target = getFeature(targetId);

        source.getDependencies().add(target);
        featureRepository.save(source);
    }

    @Override
    public Feature transitionFeatureStatus(UUID featureId, FeatureStatus newStatus) {
        Feature feature = getFeature(featureId);

        // Block transition to IN_PROGRESS if dependencies are not COMPLETED
        if (newStatus == FeatureStatus.IN_PROGRESS) {
            for (Feature dependency : feature.getDependencies()) {
                if (dependency.getStatus() != FeatureStatus.COMPLETED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feature is blocked by incomplete dependencies: " + dependency.getName());
                }
            }
        }

        feature.setStatus(newStatus);
        return featureRepository.save(feature);
    }
}
