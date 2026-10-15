package com.dpi.platform.service;

import com.dpi.platform.model.*;
import com.dpi.platform.repository.CodeCandidateRepository;
import com.dpi.platform.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI Gateway Service Prompt and Self-Healing Tests")
class AiGatewayServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CodeCandidateRepository codeCandidateRepository;

    @Mock
    private JavaParserService javaParserService;

    @Mock
    private GroqClientService groqClientService;

    @Mock
    private com.dpi.platform.repository.AgentMessageRepository agentMessageRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiGatewayServiceImpl aiGatewayService;



    @Test
    @DisplayName("Given task, requirement, userstory, and failed candidate, when assigning AI, then verify prompt contains story, criteria, and self-healing log")
    void shouldGeneratePromptWithStoryCriteriaAndSelfHealingContext() throws Exception {
        // Arrange
        UUID taskId = UUID.randomUUID();
        UUID reqId = UUID.randomUUID();

        Project project = Project.builder()
                .id(UUID.randomUUID())
                .name("Math Util App")
                .localPath(".")
                .build();

        Epic epic = Epic.builder().project(project).build();
        Feature feature = Feature.builder().epic(epic).build();

        Requirement requirement = Requirement.builder()
                .id(reqId)
                .title("Add prime detection")
                .description("Create a function to check prime status")
                .complexity(Complexity.MEDIUM)
                .priority(Priority.HIGH)
                .acceptanceCriteria("Number must be positive")
                .feature(feature)
                .build();

        UserStory story = UserStory.builder()
                .requirement(requirement)
                .storyText("As a dev I want a prime checker")
                .build();
        requirement.setUserStory(story);

        Task task = Task.builder()
                .id(taskId)
                .feature(feature)
                .requirement(requirement)
                .title("Implement PrimeChecker class")
                .description("Create PrimeChecker.java")
                .status(TaskStatus.BACKLOG)
                .build();

        // Previous failed candidate
        CodeCandidate failedCand = CodeCandidate.builder()
                .id(UUID.randomUUID())
                .task(task)
                .status("REJECTED")
                .verificationLog("COMPILATION ERROR: semicolon missing at line 12")
                .testExecutionLog("JUnit assertion failed: expected true but got false")
                .testResultsSummary("{\"testsRun\": 1, \"failures\": 1, \"errors\": 0}")
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(javaParserService.getCodeArea(reqId, 2)).thenReturn(Collections.emptyList());
        when(codeCandidateRepository.findByTask_Id(taskId)).thenReturn(Arrays.asList(failedCand));
        when(codeCandidateRepository.save(any(CodeCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<AgentMessage> savedMessages = new ArrayList<>();
        when(agentMessageRepository.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            AgentMessage msg = invocation.getArgument(0);
            savedMessages.add(msg);
            return msg;
        });
        when(agentMessageRepository.findByTask_IdOrderByCreatedAtAsc(taskId)).thenReturn(savedMessages);
        
        String llmResponse = "{\"filePath\":\"src/main/java/com/math/PrimeChecker.java\",\"proposedContent\":\"public class PrimeChecker {}\"}";
        when(groqClientService.generateCodeFromMessages(anyList())).thenReturn(llmResponse);

        // Act
        CodeCandidate result = aiGatewayService.assignAiToTask(taskId);

        // Assert
        assertNotNull(result);
        assertEquals("src/main/java/com/math/PrimeChecker.java", result.getFilePath());
        assertEquals("public class PrimeChecker {}", result.getProposedContent());

        // Capture prompts sent to Groq Client
        @SuppressWarnings({ "unchecked", "rawtypes" })
        ArgumentCaptor<List<Map<String, String>>> messagesCaptor = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(groqClientService, times(1)).generateCodeFromMessages(messagesCaptor.capture());

        List<Map<String, String>> messages = messagesCaptor.getValue();
        String systemPrompt = messages.get(0).get("content");
        String userPrompt = messages.get(1).get("content");

        // Assert System Prompt is present
        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("Java Software Architect"), "Prompt must contain senior developer role");
        
        // Assert User Story is present
        assertTrue(userPrompt.contains("- User Story: As a dev I want a prime checker"), "Prompt must contain user story text");
        
        // Assert Acceptance Criteria is present
        assertTrue(userPrompt.contains("- Acceptance Criteria: Number must be positive"), "Prompt must contain acceptance criteria");
        
        // Assert Self-Healing error block is present
        assertTrue(userPrompt.contains("COMPILATION ERROR: semicolon missing at line 12"), "Prompt must contain previous compilation errors for self-healing");
        assertTrue(userPrompt.contains("JUnit assertion failed: expected true but got false"), "Prompt must contain previous test failures for self-healing");
    }
}
