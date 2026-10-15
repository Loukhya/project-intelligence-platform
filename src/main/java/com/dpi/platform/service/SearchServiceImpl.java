package com.dpi.platform.service;

import com.dpi.platform.dto.SearchResults;
import com.dpi.platform.model.*;
import com.dpi.platform.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implements multi-entity search across all project hierarchy entities.
 * @Requirement(id="33333333-aaaa-4444-bbbb-cccccccccccc")
 */
@Service
public class SearchServiceImpl implements SearchService {

    private final ProjectRepository projectRepository;
    private final EpicRepository epicRepository;
    private final FeatureRepository featureRepository;
    private final RequirementRepository requirementRepository;
    private final TaskRepository taskRepository;
    private final TechnologyRepository technologyRepository;
    private final TechnologyDecisionRepository technologyDecisionRepository;

    @Autowired
    public SearchServiceImpl(ProjectRepository projectRepository,
                             EpicRepository epicRepository,
                             FeatureRepository featureRepository,
                             RequirementRepository requirementRepository,
                             TaskRepository taskRepository,
                             TechnologyRepository technologyRepository,
                             TechnologyDecisionRepository technologyDecisionRepository) {
        this.projectRepository = projectRepository;
        this.epicRepository = epicRepository;
        this.featureRepository = featureRepository;
        this.requirementRepository = requirementRepository;
        this.taskRepository = taskRepository;
        this.technologyRepository = technologyRepository;
        this.technologyDecisionRepository = technologyDecisionRepository;
    }

    @Override
    public SearchResults search(String query,
                                 List<String> statuses,
                                 List<String> priorities,
                                 List<String> categories,
                                 List<UUID> technologyIds,
                                 String completionState) {

        boolean hasQuery = query != null && !query.trim().isEmpty();
        String cleanQuery = hasQuery ? query.trim() : "";

        // 1. Project Search & Filter
        List<Project> rawProjects = hasQuery
                ? projectRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(cleanQuery, cleanQuery)
                : projectRepository.findAll();

        List<Project> filteredProjects = rawProjects.stream().filter(p -> {
            if (statuses != null && !statuses.isEmpty() && !statuses.contains(p.getStatus().name())) return false;
            if (categories != null && !categories.isEmpty() && !categories.contains(p.getCategory())) return false;
            if (completionState != null) {
                boolean isCompleted = p.getStatus() == ProjectStatus.COMPLETED;
                if ("COMPLETED".equalsIgnoreCase(completionState) && !isCompleted) return false;
                if ("INCOMPLETE".equalsIgnoreCase(completionState) && isCompleted) return false;
            }
            return true;
        }).collect(Collectors.toList());

        // 2. Epic Search & Filter
        List<Epic> rawEpics = hasQuery
                ? epicRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(cleanQuery, cleanQuery)
                : epicRepository.findAll();

        List<Epic> filteredEpics = rawEpics.stream().filter(e -> {
            if (statuses != null && !statuses.isEmpty() && !statuses.contains(e.getStatus().name())) return false;
            if (priorities != null && !priorities.isEmpty() && !priorities.contains(e.getPriority().name())) return false;
            if (completionState != null) {
                boolean isCompleted = e.getStatus() == EpicStatus.COMPLETED;
                if ("COMPLETED".equalsIgnoreCase(completionState) && !isCompleted) return false;
                if ("INCOMPLETE".equalsIgnoreCase(completionState) && isCompleted) return false;
            }
            return true;
        }).collect(Collectors.toList());

        // 3. Feature Search & Filter
        List<Feature> rawFeatures = hasQuery
                ? featureRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(cleanQuery, cleanQuery)
                : featureRepository.findAll();

        List<Feature> filteredFeatures = rawFeatures.stream().filter(f -> {
            if (statuses != null && !statuses.isEmpty() && !statuses.contains(f.getStatus().name())) return false;
            if (priorities != null && !priorities.isEmpty() && !priorities.contains(f.getPriority().name())) return false;
            if (completionState != null) {
                boolean isCompleted = f.getStatus() == FeatureStatus.COMPLETED;
                if ("COMPLETED".equalsIgnoreCase(completionState) && !isCompleted) return false;
                if ("INCOMPLETE".equalsIgnoreCase(completionState) && isCompleted) return false;
            }
            return true;
        }).collect(Collectors.toList());

        // 4. Task Search & Filter
        List<Task> rawTasks = hasQuery
                ? taskRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(cleanQuery, cleanQuery)
                : taskRepository.findAll();

        List<Task> filteredTasks = rawTasks.stream().filter(t -> {
            if (statuses != null && !statuses.isEmpty() && !statuses.contains(t.getStatus().name())) return false;
            if (priorities != null && !priorities.isEmpty() && !priorities.contains(t.getPriority().name())) return false;
            if (completionState != null) {
                boolean isCompleted = t.getStatus() == TaskStatus.COMPLETED;
                if ("COMPLETED".equalsIgnoreCase(completionState) && !isCompleted) return false;
                if ("INCOMPLETE".equalsIgnoreCase(completionState) && isCompleted) return false;
            }
            return true;
        }).collect(Collectors.toList());

        // 5. Requirement Search & Filter
        List<Requirement> rawRequirements = hasQuery
                ? requirementRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(cleanQuery, cleanQuery)
                : requirementRepository.findAll();

        List<Requirement> filteredRequirements = rawRequirements.stream().filter(r -> {
            if (priorities != null && !priorities.isEmpty() && !priorities.contains(r.getPriority().name())) return false;
            
            // Filter by selected or candidate technologies
            if (technologyIds != null && !technologyIds.isEmpty()) {
                boolean matchCandidate = r.getCandidates().stream().anyMatch(tech -> technologyIds.contains(tech.getId()));
                boolean matchSelected = technologyDecisionRepository.findByRequirementId(r.getId()).stream()
                        .anyMatch(dec -> technologyIds.contains(dec.getTechnology().getId()));
                if (!matchCandidate && !matchSelected) return false;
            }

            if (completionState != null) {
                boolean isCompleted = !technologyDecisionRepository.findByRequirementId(r.getId()).isEmpty();
                if ("COMPLETED".equalsIgnoreCase(completionState) && !isCompleted) return false;
                if ("INCOMPLETE".equalsIgnoreCase(completionState) && isCompleted) return false;
            }
            return true;
        }).collect(Collectors.toList());

        // 6. Technology Search & Filter
        List<Technology> rawTechnologies = hasQuery
                ? technologyRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(cleanQuery, cleanQuery)
                : technologyRepository.findAll();

        List<Technology> filteredTechnologies = rawTechnologies.stream().filter(t -> {
            if (categories != null && !categories.isEmpty() && !categories.contains(t.getCategory().name())) return false;
            return true;
        }).collect(Collectors.toList());

        return SearchResults.builder()
                .projects(filteredProjects)
                .epics(filteredEpics)
                .features(filteredFeatures)
                .tasks(filteredTasks)
                .requirements(filteredRequirements)
                .technologies(filteredTechnologies)
                .build();
    }
}
