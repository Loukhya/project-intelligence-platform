package com.dpi.platform.service;

import com.dpi.platform.dto.*;
import com.dpi.platform.model.*;
import com.dpi.platform.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Progress Tracking Engine Integration Tests")
class ProgressTrackingServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private EpicFeatureService epicFeatureService;

    @Autowired
    private TaskKanbanService taskKanbanService;

    @Autowired
    private RequirementTechService requirementTechService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EpicRepository epicRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private TechnologyRepository technologyRepository;

    private UUID projectId;
    private UUID epicId;
    private UUID featureId;

    @BeforeEach
    void setUp() {
        // Clear database
        projectRepository.deleteAll();
        technologyRepository.deleteAll();

        // Create Project
        ProjectCreateRequest projectReq = ProjectCreateRequest.builder()
                .name("Platform Development")
                .description("Build the platform")
                .category("Cloud")
                .difficulty(Difficulty.ADVANCED)
                .startDate(LocalDate.now())
                .targetCompletionDate(LocalDate.now().plusMonths(3))
                .build();
        Project project = projectService.createProject(projectReq);
        projectId = project.getId();

        // Create Epic
        EpicCreateRequest epicReq = EpicCreateRequest.builder()
                .projectId(projectId)
                .name("Core Services")
                .description("Implement backing modules")
                .priority(Priority.HIGH)
                .build();
        Epic epic = epicFeatureService.createEpic(epicReq);
        epicId = epic.getId();

        // Create Feature
        FeatureCreateRequest featureReq = FeatureCreateRequest.builder()
                .epicId(epicId)
                .name("Database Layer")
                .description("Map JPA entities")
                .priority(Priority.HIGH)
                .build();
        Feature feature = epicFeatureService.createFeature(featureReq);
        featureId = feature.getId();
    }

    @Test
    @DisplayName("Verify progress propagates bottom-up on Task and Requirement completions")
    void verifyProgressPropagation() {
        // 1. Initial checks - progress should be 0
        Feature f0 = featureRepository.findById(featureId).orElseThrow();
        assertEquals(0.0, f0.getProgress());

        // 2. Create 2 tasks
        TaskCreateRequest taskReq1 = TaskCreateRequest.builder()
                .featureId(featureId)
                .title("Define schema")
                .priority(Priority.HIGH)
                .estimatedHours(4.0)
                .build();
        Task t1 = taskKanbanService.createTask(taskReq1);

        TaskCreateRequest taskReq2 = TaskCreateRequest.builder()
                .featureId(featureId)
                .title("Write migrations")
                .priority(Priority.HIGH)
                .estimatedHours(2.0)
                .build();
        Task t2 = taskKanbanService.createTask(taskReq2);

        // 3. Complete Task 1
        TaskStatusUpdateRequest transitionReq = TaskStatusUpdateRequest.builder()
                .newStatus(TaskStatus.COMPLETED)
                .actualHours(4.5)
                .build();
        taskKanbanService.transitionTaskStatus(t1.getId(), transitionReq);

        // 4. Verify progress is 50% across Feature, Epic, and Project
        Feature f1 = featureRepository.findById(featureId).orElseThrow();
        Epic e1 = epicRepository.findById(epicId).orElseThrow();
        Project p1 = projectRepository.findById(projectId).orElseThrow();

        assertEquals(50.0, f1.getProgress());
        assertEquals(50.0, e1.getProgress());
        assertEquals(50.0, p1.getProgress());

        // 5. Add 1 Requirement (Total items = 3, completed = 1 task)
        RequirementCreateRequest reqRequest = RequirementCreateRequest.builder()
                .featureId(featureId)
                .title("Support Postgres partitioning")
                .complexity(Complexity.HIGH)
                .priority(Priority.HIGH)
                .build();
        Requirement requirement = requirementTechService.createRequirement(reqRequest);

        // Verify progress dropped to 33.33% (1 completed / 3 total)
        Feature f2 = featureRepository.findById(featureId).orElseThrow();
        assertEquals(33.33, f2.getProgress());

        // 6. Complete Requirement by selecting a technology
        Technology tech = requirementTechService.createTechnology(TechnologyCreateRequest.builder()
                .name("PostgreSQL")
                .category(TechCategory.DATABASE)
                .build());

        TechDecisionRequest decisionReq = TechDecisionRequest.builder()
                .technologyId(tech.getId())
                .whySelected("Standard partitioning support")
                .build();
        requirementTechService.selectTechnology(requirement.getId(), decisionReq);

        // Verify progress is 66.67% (1 completed task + 1 completed req / 3 total)
        Feature f3 = featureRepository.findById(featureId).orElseThrow();
        assertEquals(66.67, f3.getProgress());

        // 7. Complete the second Task
        taskKanbanService.transitionTaskStatus(t2.getId(), transitionReq);

        // Verify progress is 100% (3 completed / 3 total)
        Feature f4 = featureRepository.findById(featureId).orElseThrow();
        Epic e2 = epicRepository.findById(epicId).orElseThrow();
        Project p2 = projectRepository.findById(projectId).orElseThrow();

        assertEquals(100.0, f4.getProgress());
        assertEquals(100.0, e2.getProgress());
        assertEquals(100.0, p2.getProgress());
    }
}
