package com.dpi.platform.service;

import com.dpi.platform.dto.ProjectCreateRequest;
import com.dpi.platform.dto.ProjectFullDTO;
import com.dpi.platform.dto.VisionUpdateRequest;
import com.dpi.platform.model.*;
import com.dpi.platform.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final VisionRepository visionRepository;
    private final EpicRepository epicRepository;
    private final FeatureRepository featureRepository;
    private final RequirementRepository requirementRepository;
    private final TaskRepository taskRepository;
    private final CodeCandidateRepository codeCandidateRepository;

    @Autowired
    public ProjectServiceImpl(ProjectRepository projectRepository, VisionRepository visionRepository,
                              EpicRepository epicRepository, FeatureRepository featureRepository,
                              RequirementRepository requirementRepository, TaskRepository taskRepository,
                              CodeCandidateRepository codeCandidateRepository) {
        this.projectRepository = projectRepository;
        this.visionRepository = visionRepository;
        this.epicRepository = epicRepository;
        this.featureRepository = featureRepository;
        this.requirementRepository = requirementRepository;
        this.taskRepository = taskRepository;
        this.codeCandidateRepository = codeCandidateRepository;
    }

    /**
     * Creates a new project in the platform.
     * @Requirement(id="b8c9d0e1-f2a3-4b5c-6d7e-8f9a0b1c2d3e")
     */
    @Override
    public Project createProject(ProjectCreateRequest request) {
        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .difficulty(request.getDifficulty())
                .status(ProjectStatus.IDEA)
                .startDate(request.getStartDate())
                .targetCompletionDate(request.getTargetCompletionDate())
                .build();
        return projectRepository.save(project);
    }

    /**
     * Retrieves a project by its unique identifier.
     * @Requirement(id="b8c9d0e1-f2a3-4b5c-6d7e-8f9a0b1c2d3e")
     */
    @Override
    public Project getProject(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @Override
    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    @Override
    public Vision setProjectVision(UUID projectId, VisionUpdateRequest request) {
        Project project = getProject(projectId);
        Vision vision = project.getVision();
        if (vision == null) {
            vision = Vision.builder()
                    .project(project)
                    .visionStatement(request.getVisionStatement())
                    .build();
            project.setVision(vision);
        } else {
            vision.setVisionStatement(request.getVisionStatement());
        }
        return visionRepository.save(vision);
    }

    @Override
    public Vision getProjectVision(UUID projectId) {
        Project project = getProject(projectId);
        Vision vision = project.getVision();
        if (vision == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vision not found for project");
        }
        return vision;
    }

    @Override
    public void deleteProject(UUID id) {
        Project project = getProject(id);
        projectRepository.delete(project);
    }

    /**
     * Loads a project with its full hierarchy in optimized batch queries.
     * Returns Project → Epics → Features → Requirements (+ UserStories) + Tasks.
     */
    @Override
    public ProjectFullDTO getProjectFull(UUID id) {
        Project project = getProject(id);

        // 1. Load all epics for project
        List<Epic> epics = epicRepository.findByProject_Id(id);

        // 2. Load all features across all epics
        List<UUID> epicIds = epics.stream().map(Epic::getId).toList();
        List<Feature> allFeatures = new ArrayList<>();
        for (UUID epicId : epicIds) {
            allFeatures.addAll(featureRepository.findByEpic_Id(epicId));
        }
        List<UUID> featureIds = allFeatures.stream().map(Feature::getId).toList();

        // 3. Batch load requirements and tasks for all features
        List<Requirement> allRequirements = featureIds.isEmpty() ? List.of()
                : requirementRepository.findByFeature_IdIn(featureIds);
        List<Task> allTasks = featureIds.isEmpty() ? List.of()
                : taskRepository.findByFeature_IdIn(featureIds);

        // 4. Check which tasks have code candidates (for AI status display)
        Set<UUID> tasksWithCandidates = new HashSet<>();
        for (Task t : allTasks) {
            List<CodeCandidate> candidates = codeCandidateRepository.findByTask_IdAndStatus(t.getId(), "PENDING");
            if (candidates != null && !candidates.isEmpty()) {
                tasksWithCandidates.add(t.getId());
            }
        }

        // Group requirements and tasks by feature
        Map<UUID, List<Requirement>> reqsByFeature = allRequirements.stream()
                .collect(Collectors.groupingBy(Requirement::getFeatureId));
        Map<UUID, List<Task>> tasksByFeature = allTasks.stream()
                .collect(Collectors.groupingBy(Task::getFeatureId));

        // Build requirement lookup for task display
        Map<UUID, Requirement> reqById = allRequirements.stream()
                .collect(Collectors.toMap(Requirement::getId, r -> r));

        // Group features by epic
        Map<UUID, List<Feature>> featuresByEpic = allFeatures.stream()
                .collect(Collectors.groupingBy(Feature::getEpicId));

        // Assemble DTO tree
        List<ProjectFullDTO.EpicDTO> epicDTOs = epics.stream().map(epic -> {
            List<Feature> epicFeatures = featuresByEpic.getOrDefault(epic.getId(), List.of());

            List<ProjectFullDTO.FeatureDTO> featureDTOs = epicFeatures.stream().map(feature -> {
                List<Requirement> featureReqs = reqsByFeature.getOrDefault(feature.getId(), List.of());
                List<Task> featureTasks = tasksByFeature.getOrDefault(feature.getId(), List.of());

                // Count tasks linked to each requirement
                Map<UUID, Long> taskCountByReq = featureTasks.stream()
                        .filter(t -> t.getRequirementId() != null)
                        .collect(Collectors.groupingBy(Task::getRequirementId, Collectors.counting()));

                List<ProjectFullDTO.RequirementDTO> reqDTOs = featureReqs.stream().map(req ->
                    ProjectFullDTO.RequirementDTO.builder()
                            .id(req.getId())
                            .title(req.getTitle())
                            .description(req.getDescription())
                            .complexity(req.getComplexity().name())
                            .priority(req.getPriority().name())
                            .status(req.getStatus().name())
                            .type(req.getType().name())
                            .estimatedEffort(req.getEstimatedEffort())
                            .notes(req.getNotes())
                            .acceptanceCriteria(req.getAcceptanceCriteria())
                            .userStory(req.getUserStory() != null ? ProjectFullDTO.UserStoryDTO.builder()
                                    .id(req.getUserStory().getId())
                                    .storyText(req.getUserStory().getStoryText())
                                    .build() : null)
                            .linkedTaskCount(taskCountByReq.getOrDefault(req.getId(), 0L).intValue())
                            .build()
                ).toList();

                List<ProjectFullDTO.TaskDTO> taskDTOs = featureTasks.stream().map(task -> {
                    Requirement linkedReq = task.getRequirementId() != null ? reqById.get(task.getRequirementId()) : null;
                    return ProjectFullDTO.TaskDTO.builder()
                            .id(task.getId())
                            .title(task.getTitle())
                            .description(task.getDescription())
                            .priority(task.getPriority().name())
                            .status(task.getStatus().name())
                            .estimatedHours(task.getEstimatedHours())
                            .actualHours(task.getActualHours())
                            .featureId(feature.getId())
                            .featureName(feature.getName())
                            .requirementId(task.getRequirementId())
                            .requirementTitle(linkedReq != null ? linkedReq.getTitle() : null)
                            .hasCodeCandidate(tasksWithCandidates.contains(task.getId()))
                            .build();
                }).toList();

                long completedCount = featureTasks.stream()
                        .filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();

                return ProjectFullDTO.FeatureDTO.builder()
                        .id(feature.getId())
                        .name(feature.getName())
                        .description(feature.getDescription())
                        .priority(feature.getPriority().name())
                        .status(feature.getStatus().name())
                        .progress(feature.getProgress())
                        .taskCount(featureTasks.size())
                        .completedTaskCount((int) completedCount)
                        .requirementCount(featureReqs.size())
                        .requirements(reqDTOs)
                        .tasks(taskDTOs)
                        .build();
            }).toList();

            return ProjectFullDTO.EpicDTO.builder()
                    .id(epic.getId())
                    .name(epic.getName())
                    .description(epic.getDescription())
                    .priority(epic.getPriority().name())
                    .status(epic.getStatus().name())
                    .progress(epic.getProgress())
                    .features(featureDTOs)
                    .build();
        }).toList();

        return ProjectFullDTO.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .category(project.getCategory())
                .difficulty(project.getDifficulty().name())
                .status(project.getStatus().name())
                .progress(project.getProgress())
                .startDate(project.getStartDate() != null ? project.getStartDate().toString() : null)
                .targetCompletionDate(project.getTargetCompletionDate() != null ? project.getTargetCompletionDate().toString() : null)
                .repositoryUrl(project.getRepositoryUrl())
                .localPath(project.getLocalPath())
                .vision(project.getVision() != null ? ProjectFullDTO.VisionDTO.builder()
                        .id(project.getVision().getId())
                        .visionStatement(project.getVision().getVisionStatement())
                                                .build() : null)
                .epics(epicDTOs)
                .build();
    }

    @org.springframework.transaction.annotation.Transactional
    @Override
    public Project seedSampleProject() {
        // 1. Create Project
        Project project = Project.builder()
                .name("Math Calculator Project")
                .description("A sample math calculator project to demonstrate epics, features, requirements, decisions, and tasks in action.")
                .category("Utility Application")
                .difficulty(Difficulty.BEGINNER)
                .status(ProjectStatus.PLANNING)
                .startDate(java.time.LocalDate.now())
                .targetCompletionDate(java.time.LocalDate.now().plusMonths(1))
                .build();
        project = projectRepository.save(project);

        // 2. Create Vision
        Vision vision = Vision.builder()
                .project(project)
                .visionStatement("To provide a premium web-based math calculator containing basic arithmetic and complex statistical calculations with call graph traceability.")
                .build();
        vision = visionRepository.save(vision);
        project.setVision(vision);
        project = projectRepository.save(project);

        // 3. Create Epic
        Epic epic = Epic.builder()
                .project(project)
                .name("Core Functions")
                .description("Fundamental math calculator capabilities including arithmetic and statistics.")
                .priority(Priority.HIGH)
                .status(EpicStatus.PLANNED)
                .build();
        epic = epicRepository.save(epic);

        // 4. Create Feature
        Feature feature = Feature.builder()
                .epic(epic)
                .name("Basic Arithmetic")
                .description("Core arithmetic operations: add, subtract, multiply, divide.")
                .priority(Priority.HIGH)
                .status(FeatureStatus.PLANNED)
                .build();
        feature = featureRepository.save(feature);

        // 5. Create Requirement 1
        Requirement req1 = Requirement.builder()
                .feature(feature)
                .title("Addition and Subtraction")
                .description("The calculator must support adding and subtracting multiple float values accurately.")
                .complexity(Complexity.LOW)
                .priority(Priority.HIGH)
                .status(RequirementStatus.APPROVED)
                .acceptanceCriteria("addition must support negative numbers; subtraction must handle negative outputs.")
                .build();
        req1 = requirementRepository.save(req1);

        UserStory story1 = UserStory.builder()
                .requirement(req1)
                .storyText("As a developer, I want to add and subtract numeric values so that I can calculate totals.")
                .build();
        req1.setUserStory(story1);
        req1 = requirementRepository.save(req1);

        // Create Task for Requirement 1
        Task t1 = Task.builder()
                .feature(feature)
                .requirement(req1)
                .title("Implement Addition & Subtraction")
                .description("Create core service methods in math library to handle addition and subtraction operations.")
                .priority(Priority.MEDIUM)
                .status(TaskStatus.BACKLOG)
                .estimatedHours(4.0)
                .actualHours(0.0)
                .build();
        taskRepository.save(t1);

        // 6. Create Requirement 2
        Requirement req2 = Requirement.builder()
                .feature(feature)
                .title("Multiplication and Division")
                .description("The calculator must support multiplying and dividing numeric inputs.")
                .complexity(Complexity.MEDIUM)
                .priority(Priority.HIGH)
                .status(RequirementStatus.APPROVED)
                .acceptanceCriteria("division by zero must throw an IllegalArgumentException with custom message.")
                .build();
        req2 = requirementRepository.save(req2);

        UserStory story2 = UserStory.builder()
                .requirement(req2)
                .storyText("As a researcher, I want to divide numeric results so that I can compute average ratios.")
                .build();
        req2.setUserStory(story2);
        req2 = requirementRepository.save(req2);

        // Create Task for Requirement 2
        Task t2 = Task.builder()
                .feature(feature)
                .requirement(req2)
                .title("Implement Multiplication & Division")
                .description("Create core service methods in math library to handle multiplication and division operations.")
                .priority(Priority.MEDIUM)
                .status(TaskStatus.BACKLOG)
                .estimatedHours(4.0)
                .actualHours(0.0)
                .build();
        taskRepository.save(t2);

        return project;
    }

    @org.springframework.transaction.annotation.Transactional
    @Override
    public Project restructureMathProject(UUID id) {
        Project project = getProject(id);

        // 1. Delete all tasks for features under epics of this project
        List<Epic> epics = epicRepository.findByProject_Id(id);
        for (Epic epic : epics) {
            List<Feature> features = featureRepository.findByEpic_Id(epic.getId());
            for (Feature feature : features) {
                // Delete tasks
                List<Task> tasks = taskRepository.findByFeature_Id(feature.getId());
                taskRepository.deleteAll(tasks);
                // Delete requirements
                List<Requirement> reqs = requirementRepository.findByFeature_Id(feature.getId());
                requirementRepository.deleteAll(reqs);
                // Delete feature
                featureRepository.delete(feature);
            }
            // Delete epic
            epicRepository.delete(epic);
        }

        // Reset project fields
        project.setDescription("A premium library containing advanced arithmetic and combinatorics calculations with call graph traceability.");
        project.setProgress(0.0);
        project.setStatus(ProjectStatus.PLANNING);
        project = projectRepository.save(project);

        // 2. Create fresh Epic
        Epic epic = Epic.builder()
                .project(project)
                .name("Mathematical Capabilities")
                .description("Advanced sequences, statistics, and combinatorics calculations.")
                .priority(Priority.HIGH)
                .status(EpicStatus.PLANNED)
                .build();
        epic = epicRepository.save(epic);

        // 3. Create fresh Feature
        Feature feature = Feature.builder()
                .epic(epic)
                .name("Arithmetical & Statistical Algorithms")
                .description("Calculation engines for factorial and combinatorics equations.")
                .priority(Priority.HIGH)
                .status(FeatureStatus.PLANNED)
                .build();
        feature = featureRepository.save(feature);

        // 4. Create Factorial Requirement
        Requirement req1 = Requirement.builder()
                .feature(feature)
                .title("Factorial Service Calculation")
                .description("The service must implement a factorial calculation method that computes the factorial of N (where 0 <= N <= 20) and returns a long value.")
                .complexity(Complexity.LOW)
                .priority(Priority.HIGH)
                .status(RequirementStatus.APPROVED)
                .acceptanceCriteria("Method long calculateFactorial(int n) must return 1 for n=0 and n=1.\nThrow IllegalArgumentException for negative input.\nReturn correct results up to N=20.")
                .build();
        req1 = requirementRepository.save(req1);

        UserStory story1 = UserStory.builder()
                .requirement(req1)
                .storyText("As a math student, I want to calculate factorials so that I can compute combinations.")
                .build();
        req1.setUserStory(story1);
        req1 = requirementRepository.save(req1);

        // Create Task for Factorial Requirement
        Task t1 = Task.builder()
                .feature(feature)
                .requirement(req1)
                .title("Implement Factorial calculations in FactorialService")
                .description("Create a class com.mathutil.FactorialService with a method public long calculateFactorial(int n) that calculates the factorial. Create it in d:/spring/projects/math-utility/src/main/java/com/mathutil/FactorialService.java.")
                .priority(Priority.HIGH)
                .status(TaskStatus.BACKLOG)
                .estimatedHours(4.0)
                .actualHours(0.0)
                .build();
        taskRepository.save(t1);

        // 5. Create Combinatorics Requirement
        Requirement req2 = Requirement.builder()
                .feature(feature)
                .title("Combinatorics calculations")
                .description("The service must support combination (nCr) and permutation (nPr) mathematics calculations using the FactorialService.")
                .complexity(Complexity.MEDIUM)
                .priority(Priority.MEDIUM)
                .status(RequirementStatus.APPROVED)
                .acceptanceCriteria("Method long nCr(int n, int r) and long nPr(int n, int r) must calculate correct outputs.\nThrow exception when r > n or inputs are negative.")
                .build();
        req2 = requirementRepository.save(req2);

        UserStory story2 = UserStory.builder()
                .requirement(req2)
                .storyText("As a statistics researcher, I want to calculate combinations and permutations so that I can study binomial subsets.")
                .build();
        req2.setUserStory(story2);
        req2 = requirementRepository.save(req2);

        // Create Task for Combinatorics Requirement
        Task t2 = Task.builder()
                .feature(feature)
                .requirement(req2)
                .title("Implement Combinatorics calculations in CombinatoricsService")
                .description("Create a class com.mathutil.CombinatoricsService with methods for nCr and nPr. Create it in d:/spring/projects/math-utility/src/main/java/com/mathutil/CombinatoricsService.java.")
                .priority(Priority.HIGH)
                .status(TaskStatus.BACKLOG)
                .estimatedHours(6.0)
                .actualHours(0.0)
                .build();
        taskRepository.save(t2);

        return project;
    }

    @Override
    public Project updateProjectGitHub(UUID id, String repoOwner, String repoName) {
        Project project = getProject(id);
        project.setGithubOwner(repoOwner);
        project.setGithubRepoName(repoName);
        if (repoOwner != null && repoName != null) {
            project.setRepositoryUrl("https://github.com/" + repoOwner + "/" + repoName);
        } else {
            project.setRepositoryUrl(null);
        }
        return projectRepository.save(project);
    }
}