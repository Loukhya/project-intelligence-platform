package com.dpi.platform.service;

import com.dpi.platform.model.*;
import com.dpi.platform.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProgressServiceImpl implements ProgressService {

    private final FeatureRepository featureRepository;
    private final EpicRepository epicRepository;
    private final ProjectRepository projectRepository;
    private final RequirementRepository requirementRepository;
    private final TaskRepository taskRepository;
    private final TechnologyDecisionRepository technologyDecisionRepository;

    @Autowired
    public ProgressServiceImpl(FeatureRepository featureRepository,
                               EpicRepository epicRepository,
                               ProjectRepository projectRepository,
                               RequirementRepository requirementRepository,
                               TaskRepository taskRepository,
                               TechnologyDecisionRepository technologyDecisionRepository) {
        this.featureRepository = featureRepository;
        this.epicRepository = epicRepository;
        this.projectRepository = projectRepository;
        this.requirementRepository = requirementRepository;
        this.taskRepository = taskRepository;
        this.technologyDecisionRepository = technologyDecisionRepository;
    }

    /**
     * Recalculates the completion progress percentage of a feature based on requirements and tasks.
     * @Requirement(id="33333333-aaaa-6666-bbbb-cccccccccccc")
     */
    @Override
    public void recalculateFeatureProgress(UUID featureId) {
        Feature feature = featureRepository.findById(featureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature not found"));

        List<Requirement> requirements = requirementRepository.findByFeature_Id(featureId);
        List<Task> tasks = taskRepository.findByFeature_Id(featureId);

        long completedRequirementsCount = requirements.stream()
                .filter(req -> !technologyDecisionRepository.findByRequirementId(req.getId()).isEmpty())
                .count();

        long completedTasksCount = tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.COMPLETED)
                .count();

        int totalItems = requirements.size() + tasks.size();
        long completedItems = completedRequirementsCount + completedTasksCount;

        double featureProgress = totalItems > 0
                ? (double) completedItems / totalItems * 100.0
                : (feature.getStatus() == FeatureStatus.COMPLETED ? 100.0 : 0.0);

        // Normalize decimal places
        featureProgress = Math.round(featureProgress * 100.0) / 100.0;
        feature.setProgress(featureProgress);
        featureRepository.save(feature);

        // Propagate to Epic
        Epic epic = feature.getEpic();
        if (epic != null) {
            recalculateEpicProgress(epic);
        }
    }

    private void recalculateEpicProgress(Epic epic) {
        List<Feature> features = featureRepository.findByEpic_Id(epic.getId());

        double sumFeaturesProgress = features.stream()
                .mapToDouble(Feature::getProgress)
                .sum();

        double epicProgress = features.size() > 0
                ? sumFeaturesProgress / features.size()
                : (epic.getStatus() == EpicStatus.COMPLETED ? 100.0 : 0.0);

        epicProgress = Math.round(epicProgress * 100.0) / 100.0;
        epic.setProgress(epicProgress);
        epicRepository.save(epic);

        // Propagate to Project
        Project project = epic.getProject();
        if (project != null) {
            recalculateProjectProgress(project);
        }
    }

    private void recalculateProjectProgress(Project project) {
        List<Epic> epics = epicRepository.findByProject_Id(project.getId());

        double sumEpicsProgress = epics.stream()
                .mapToDouble(Epic::getProgress)
                .sum();

        double projectProgress = epics.size() > 0
                ? sumEpicsProgress / epics.size()
                : (project.getStatus() == ProjectStatus.COMPLETED ? 100.0 : 0.0);

        projectProgress = Math.round(projectProgress * 100.0) / 100.0;
        project.setProgress(projectProgress);

        if (projectProgress >= 100.0) {
            if (project.getActualCompletionDate() == null) {
                project.setActualCompletionDate(LocalDate.now());
            }
            if (project.getStatus() != ProjectStatus.COMPLETED) {
                project.setStatus(ProjectStatus.COMPLETED);
            }
        } else {
            project.setActualCompletionDate(null);
            if (project.getStatus() == ProjectStatus.COMPLETED) {
                project.setStatus(ProjectStatus.IN_PROGRESS);
            }
        }

        projectRepository.save(project);
    }
}
