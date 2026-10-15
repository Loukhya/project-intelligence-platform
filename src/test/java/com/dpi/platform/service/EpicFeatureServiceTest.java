package com.dpi.platform.service;

import com.dpi.platform.dto.EpicCreateRequest;
import com.dpi.platform.dto.FeatureCreateRequest;
import com.dpi.platform.model.*;
import com.dpi.platform.repository.EpicRepository;
import com.dpi.platform.repository.FeatureRepository;
import com.dpi.platform.repository.ProjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Epic & Feature Service Tests")
class EpicFeatureServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private EpicRepository epicRepository;

    @Mock
    private FeatureRepository featureRepository;

    @InjectMocks
    private EpicFeatureServiceImpl epicFeatureService;

    @Nested
    @DisplayName("Epic Management Tests")
    class EpicManagement {

        @Test
        @DisplayName("Given valid Epic request, when creating Epic, then return saved Epic with Planned status")
        void shouldCreateEpicSuccessfully() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            Project project = Project.builder().id(projectId).name("Growth OS").build();

            EpicCreateRequest request = EpicCreateRequest.builder()
                    .name("Authentication")
                    .description("User auth epic")
                    .priority(Priority.HIGH)
                    .projectId(projectId)
                    .build();

            Epic savedEpic = Epic.builder()
                    .id(UUID.randomUUID())
                    .project(project)
                    .name(request.getName())
                    .description(request.getDescription())
                    .priority(request.getPriority())
                    .status(EpicStatus.PLANNED)
                    .build();

            when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
            when(epicRepository.save(any(Epic.class))).thenReturn(savedEpic);

            // Act
            Epic result = epicFeatureService.createEpic(request);

            // Assert
            assertNotNull(result);
            assertEquals("Authentication", result.getName());
            assertEquals(EpicStatus.PLANNED, result.getStatus());
            assertEquals(Priority.HIGH, result.getPriority());
            verify(projectRepository, times(1)).findById(projectId);
            verify(epicRepository, times(1)).save(any(Epic.class));
        }
    }

    @Nested
    @DisplayName("Feature Management & Dependencies Tests")
    class FeatureManagement {

        @Test
        @DisplayName("Given valid Feature request, when creating Feature, then return saved Feature with Backlog status")
        void shouldCreateFeatureSuccessfully() {
            // Arrange
            UUID epicId = UUID.randomUUID();
            Epic epic = Epic.builder().id(epicId).name("Authentication").build();

            FeatureCreateRequest request = FeatureCreateRequest.builder()
                    .name("User Login")
                    .description("Standard JWT login")
                    .priority(Priority.MEDIUM)
                    .epicId(epicId)
                    .build();

            Feature savedFeature = Feature.builder()
                    .id(UUID.randomUUID())
                    .epic(epic)
                    .name(request.getName())
                    .description(request.getDescription())
                    .priority(request.getPriority())
                    .status(FeatureStatus.BACKLOG)
                    .build();

            when(epicRepository.findById(epicId)).thenReturn(Optional.of(epic));
            when(featureRepository.save(any(Feature.class))).thenReturn(savedFeature);

            // Act
            Feature result = epicFeatureService.createFeature(request);

            // Assert
            assertNotNull(result);
            assertEquals("User Login", result.getName());
            assertEquals(FeatureStatus.BACKLOG, result.getStatus());
            verify(epicRepository, times(1)).findById(epicId);
            verify(featureRepository, times(1)).save(any(Feature.class));
        }

        @Test
        @DisplayName("Given source and target features, when establishing dependency, then link them in the graph")
        void shouldDeclareFeatureDependency() {
            // Arrange
            UUID sourceId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();

            Feature source = Feature.builder().id(sourceId).name("Role Management").build();
            Feature target = Feature.builder().id(targetId).name("User Login").build();

            when(featureRepository.findById(sourceId)).thenReturn(Optional.of(source));
            when(featureRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(featureRepository.save(any(Feature.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            epicFeatureService.addFeatureDependency(sourceId, targetId);

            // Assert
            assertTrue(source.getDependencies().contains(target));
            verify(featureRepository, times(1)).findById(sourceId);
            verify(featureRepository, times(1)).findById(targetId);
            verify(featureRepository, times(1)).save(source);
        }

        @Test
        @DisplayName("Given a feature blocked by an incomplete dependency, when transitioning to In Progress, then block and throw exception")
        void shouldBlockTransitionWhenDependenciesAreIncomplete() {
            // Arrange
            UUID sourceId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();

            Feature target = Feature.builder().id(targetId).name("User Login").status(FeatureStatus.IN_PROGRESS).build();
            Feature source = Feature.builder().id(sourceId).name("Role Management").status(FeatureStatus.BACKLOG).build();
            source.getDependencies().add(target);

            when(featureRepository.findById(sourceId)).thenReturn(Optional.of(source));

            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                    epicFeatureService.transitionFeatureStatus(sourceId, FeatureStatus.IN_PROGRESS)
            );
            assertTrue(exception.getReason().contains("Feature is blocked by incomplete dependencies"));
            verify(featureRepository, times(1)).findById(sourceId);
            verify(featureRepository, never()).save(any(Feature.class));
        }

        @Test
        @DisplayName("Given a feature whose dependency is completed, when transitioning to In Progress, then succeed and update status")
        void shouldSucceedTransitionWhenDependenciesAreCompleted() {
            // Arrange
            UUID sourceId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();

            Feature target = Feature.builder().id(targetId).name("User Login").status(FeatureStatus.COMPLETED).build();
            Feature source = Feature.builder().id(sourceId).name("Role Management").status(FeatureStatus.BACKLOG).build();
            source.getDependencies().add(target);

            when(featureRepository.findById(sourceId)).thenReturn(Optional.of(source));
            when(featureRepository.save(any(Feature.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Feature result = epicFeatureService.transitionFeatureStatus(sourceId, FeatureStatus.IN_PROGRESS);

            // Assert
            assertNotNull(result);
            assertEquals(FeatureStatus.IN_PROGRESS, result.getStatus());
            verify(featureRepository, times(1)).findById(sourceId);
            verify(featureRepository, times(1)).save(source);
        }
    }
}
