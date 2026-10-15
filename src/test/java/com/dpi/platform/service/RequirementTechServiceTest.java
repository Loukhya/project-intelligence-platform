package com.dpi.platform.service;

import com.dpi.platform.dto.RequirementCreateRequest;
import com.dpi.platform.dto.TechDecisionRequest;
import com.dpi.platform.dto.UserStoryCreateRequest;
import com.dpi.platform.model.*;
import com.dpi.platform.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Requirement & Technology Service Tests")
class RequirementTechServiceTest {

    @Mock
    private FeatureRepository featureRepository;

    @Mock
    private RequirementRepository requirementRepository;

    @Mock
    private UserStoryRepository userStoryRepository;

    @Mock
    private TechnologyRepository technologyRepository;

    @Mock
    private TechnologyDecisionRepository technologyDecisionRepository;

    @InjectMocks
    private RequirementTechServiceImpl requirementTechService;

    @Nested
    @DisplayName("Requirement Management Tests")
    class RequirementManagement {

        @Test
        @DisplayName("Given valid request, when creating requirement, then return saved requirement")
        void shouldCreateRequirementSuccessfully() {
            // Arrange
            UUID featureId = UUID.randomUUID();
            Feature feature = Feature.builder().id(featureId).name("User Login").build();

            RequirementCreateRequest request = RequirementCreateRequest.builder()
                    .featureId(featureId)
                    .title("Support email login")
                    .description("Enable login via email and password")
                    .complexity(Complexity.MEDIUM)
                    .priority(Priority.HIGH)
                    .estimatedEffort(5.0)
                    .notes("Standard OAuth logic")
                    .acceptanceCriteria("User must enter valid email format")
                    .build();

            Requirement savedRequirement = Requirement.builder()
                    .id(UUID.randomUUID())
                    .feature(feature)
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .complexity(request.getComplexity())
                    .priority(request.getPriority())
                    .estimatedEffort(request.getEstimatedEffort())
                    .notes(request.getNotes())
                    .acceptanceCriteria(request.getAcceptanceCriteria())
                    .build();

            when(featureRepository.findById(featureId)).thenReturn(Optional.of(feature));
            when(requirementRepository.save(any(Requirement.class))).thenReturn(savedRequirement);

            // Act
            Requirement result = requirementTechService.createRequirement(request);

            // Assert
            assertNotNull(result);
            assertEquals("Support email login", result.getTitle());
            assertEquals(Complexity.MEDIUM, result.getComplexity());
            assertEquals("User must enter valid email format", result.getAcceptanceCriteria());
            verify(featureRepository, times(1)).findById(featureId);
            verify(requirementRepository, times(1)).save(any(Requirement.class));
        }
    }

    @Nested
    @DisplayName("User Story Mapping Tests")
    class UserStoryMapping {

        @Test
        @DisplayName("Given a requirement, when attaching a user story, then save and return the story linked to it")
        void shouldAttachUserStoryToRequirement() {
            // Arrange
            UUID reqId = UUID.randomUUID();
            Requirement requirement = Requirement.builder().id(reqId).title("Support email login").build();
            UserStoryCreateRequest request = new UserStoryCreateRequest("As a user I want to login using email So that I can access my account");

            UserStory savedStory = UserStory.builder()
                    .id(UUID.randomUUID())
                    .requirement(requirement)
                    .storyText(request.getStoryText())
                    .build();

            when(requirementRepository.findById(reqId)).thenReturn(Optional.of(requirement));
            when(userStoryRepository.save(any(UserStory.class))).thenReturn(savedStory);

            // Act
            UserStory result = requirementTechService.attachUserStory(reqId, request);

            // Assert
            assertNotNull(result);
            assertEquals(request.getStoryText(), result.getStoryText());
            assertEquals(requirement, result.getRequirement());
            verify(requirementRepository, times(1)).findById(reqId);
            verify(userStoryRepository, times(1)).save(any(UserStory.class));
        }
    }

    @Nested
    @DisplayName("Technology Candidates evaluation Tests")
    class TechCandidates {

        @Test
        @DisplayName("Given a requirement and technologies, when adding candidates, then link them as candidates")
        void shouldAddTechnologyCandidates() {
            // Arrange
            UUID reqId = UUID.randomUUID();
            Requirement requirement = Requirement.builder().id(reqId).title("Support email login").build();

            UUID techId1 = UUID.randomUUID();
            UUID techId2 = UUID.randomUUID();
            Technology tech1 = Technology.builder().id(techId1).name("Spring Security").build();
            Technology tech2 = Technology.builder().id(techId2).name("Keycloak").build();

            when(requirementRepository.findById(reqId)).thenReturn(Optional.of(requirement));
            when(technologyRepository.findById(techId1)).thenReturn(Optional.of(tech1));
            when(technologyRepository.findById(techId2)).thenReturn(Optional.of(tech2));
            when(requirementRepository.save(any(Requirement.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            requirementTechService.addTechnologyCandidates(reqId, Arrays.asList(techId1, techId2));

            // Assert
            assertTrue(requirement.getCandidates().contains(tech1));
            assertTrue(requirement.getCandidates().contains(tech2));
            verify(requirementRepository, times(1)).findById(reqId);
            verify(technologyRepository, times(1)).findById(techId1);
            verify(technologyRepository, times(1)).findById(techId2);
            verify(requirementRepository, times(1)).save(requirement);
        }
    }

    @Nested
    @DisplayName("Technology Decision Tracking Tests")
    class TechDecision {

        @Test
        @DisplayName("Given a requirement and technology, when selecting the technology, then save and return the decision details")
        void shouldSelectTechnologyAndRecordDecision() {
            // Arrange
            UUID reqId = UUID.randomUUID();
            Requirement requirement = Requirement.builder().id(reqId).title("Support email login").build();

            UUID techId = UUID.randomUUID();
            Technology technology = Technology.builder().id(techId).name("Spring Security").build();

            TechDecisionRequest request = TechDecisionRequest.builder()
                    .technologyId(techId)
                    .whySelected("Standard choice")
                    .advantages("Stateless, secure")
                    .tradeoffs("Complex config")
                    .notes("Excellent Spring ecosystem support")
                    .build();

            TechnologyDecision savedDecision = TechnologyDecision.builder()
                    .id(UUID.randomUUID())
                    .requirement(requirement)
                    .technology(technology)
                    .whySelected(request.getWhySelected())
                    .advantages(request.getAdvantages())
                    .tradeoffs(request.getTradeoffs())
                    .notes(request.getNotes())
                    .build();

            when(requirementRepository.findById(reqId)).thenReturn(Optional.of(requirement));
            when(technologyRepository.findById(techId)).thenReturn(Optional.of(technology));
            when(technologyDecisionRepository.save(any(TechnologyDecision.class))).thenReturn(savedDecision);

            // Act
            TechnologyDecision result = requirementTechService.selectTechnology(reqId, request);

            // Assert
            assertNotNull(result);
            assertEquals("Standard choice", result.getWhySelected());
            assertEquals("Stateless, secure", result.getAdvantages());
            assertEquals("Complex config", result.getTradeoffs());
            assertEquals(requirement, result.getRequirement());
            assertEquals(technology, result.getTechnology());
            verify(requirementRepository, times(1)).findById(reqId);
            verify(technologyRepository, times(1)).findById(techId);
            verify(technologyDecisionRepository, times(1)).save(any(TechnologyDecision.class));
        }
    }
}
