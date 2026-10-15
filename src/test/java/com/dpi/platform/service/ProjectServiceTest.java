package com.dpi.platform.service;

import com.dpi.platform.dto.ProjectCreateRequest;
import com.dpi.platform.dto.VisionUpdateRequest;
import com.dpi.platform.model.Difficulty;
import com.dpi.platform.model.Project;
import com.dpi.platform.model.ProjectStatus;
import com.dpi.platform.model.Vision;
import com.dpi.platform.repository.ProjectRepository;
import com.dpi.platform.repository.VisionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Project & Vision Service Tests")
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private VisionRepository visionRepository;

    @InjectMocks
    private ProjectServiceImpl projectService;

    @Nested
    @DisplayName("Project Management Tests")
    class ProjectManagement {

        @Test
        @DisplayName("Given a valid project request, when creating the project, then return the saved project with IDEA status")
        void shouldCreateProjectSuccessfully() {
            // Arrange
            ProjectCreateRequest request = ProjectCreateRequest.builder()
                    .name("Growth OS")
                    .category("Learner")
                    .difficulty(Difficulty.BEGINNER)
                    .startDate(LocalDate.now())
                    .targetCompletionDate(LocalDate.now().plusMonths(3))
                    .build();

            UUID generatedId = UUID.randomUUID();
            Project savedProject = Project.builder()
                    .id(generatedId)
                    .name(request.getName())
                    .category(request.getCategory())
                    .difficulty(request.getDifficulty())
                    .status(ProjectStatus.IDEA)
                    .startDate(request.getStartDate())
                    .targetCompletionDate(request.getTargetCompletionDate())
                    .createdAt(LocalDateTime.now())
                    .build();

            when(projectRepository.save(any(Project.class))).thenReturn(savedProject);

            // Act
            Project result = projectService.createProject(request);

            // Assert
            assertNotNull(result);
            assertEquals(generatedId, result.getId());
            assertEquals("Growth OS", result.getName());
            assertEquals("Learner", result.getCategory());
            assertEquals(Difficulty.BEGINNER, result.getDifficulty());
            assertEquals(ProjectStatus.IDEA, result.getStatus());
            assertNotNull(result.getCreatedAt());
            verify(projectRepository, times(1)).save(any(Project.class));
        }

        @Test
        @DisplayName("Given an existing project ID, when fetching the project, then return the project details")
        void shouldFetchProjectById() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            Project project = Project.builder()
                    .id(projectId)
                    .name("Growth OS")
                    .category("Learner")
                    .difficulty(Difficulty.BEGINNER)
                    .status(ProjectStatus.IDEA)
                    .build();

            when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

            // Act
            Project result = projectService.getProject(projectId);

            // Assert
            assertNotNull(result);
            assertEquals(projectId, result.getId());
            verify(projectRepository, times(1)).findById(projectId);
        }
    }

    @Nested
    @DisplayName("Vision Management Tests")
    class VisionManagement {

        @Test
        @DisplayName("Given an existing project, when setting the vision, then save and return the vision linked to the project")
        void shouldSetVisionForProject() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            Project project = Project.builder()
                    .id(projectId)
                    .name("Growth OS")
                    .category("Learner")
                    .difficulty(Difficulty.BEGINNER)
                    .status(ProjectStatus.IDEA)
                    .build();

            VisionUpdateRequest request = new VisionUpdateRequest("A system to track learning progress");

            Vision savedVision = Vision.builder()
                    .id(UUID.randomUUID())
                    .project(project)
                    .visionStatement(request.getVisionStatement())
                    .createdAt(LocalDateTime.now())
                    .build();

            when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
            when(visionRepository.save(any(Vision.class))).thenReturn(savedVision);

            // Act
            Vision result = projectService.setProjectVision(projectId, request);

            // Assert
            assertNotNull(result);
            assertEquals(request.getVisionStatement(), result.getVisionStatement());
            assertEquals(project, result.getProject());
            verify(projectRepository, times(1)).findById(projectId);
            verify(visionRepository, times(1)).save(any(Vision.class));
        }

        @Test
        @DisplayName("Given an existing project with vision, when getting the vision, then return the vision details")
        void shouldGetVisionForProject() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            Project project = Project.builder()
                    .id(projectId)
                    .name("Growth OS")
                    .category("Learner")
                    .difficulty(Difficulty.BEGINNER)
                    .status(ProjectStatus.IDEA)
                    .build();

            Vision vision = Vision.builder()
                    .id(UUID.randomUUID())
                    .project(project)
                    .visionStatement("A system to track learning progress")
                    .build();
            project.setVision(vision);

            when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

            // Act
            Vision result = projectService.getProjectVision(projectId);

            // Assert
            assertNotNull(result);
            assertEquals("A system to track learning progress", result.getVisionStatement());
            verify(projectRepository, times(1)).findById(projectId);
        }
    }

    @Nested
    @DisplayName("Cascading Deletion Tests")
    class CascadingDeletion {

        @Test
        @DisplayName("Given an existing project, when deleting the project, then verify project is deleted from DB")
        void shouldDeleteProjectAndCascade() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            Project project = Project.builder()
                    .id(projectId)
                    .name("Growth OS")
                    .category("Learner")
                    .difficulty(Difficulty.BEGINNER)
                    .status(ProjectStatus.IDEA)
                    .build();

            when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
            doNothing().when(projectRepository).delete(project);

            // Act
            projectService.deleteProject(projectId);

            // Assert
            verify(projectRepository, times(1)).findById(projectId);
            verify(projectRepository, times(1)).delete(project);
        }
    }
}
