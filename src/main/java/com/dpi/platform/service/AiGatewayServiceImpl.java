package com.dpi.platform.service;

import com.dpi.platform.model.*;
import com.dpi.platform.repository.CodeCandidateRepository;
import com.dpi.platform.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import com.dpi.platform.repository.AgentMessageRepository;
import com.dpi.platform.model.AgentMessage;
import java.time.LocalDateTime;

@Service
@Transactional
public class AiGatewayServiceImpl implements AiGatewayService {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayServiceImpl.class);

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private CodeCandidateRepository codeCandidateRepository;

    @Autowired
    private JavaParserService javaParserService;

    @Autowired
    private GroqClientService groqClientService;

    @Autowired
    private AgentMessageRepository agentMessageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.dpi.platform.repository.AgentSubtaskQueueRepository agentSubtaskQueueRepository;

    @org.springframework.beans.factory.annotation.Value("${crewai.api.url}")
    private String crewaiApiUrl;

    @org.springframework.beans.factory.annotation.Value("${ai.integration-mode}")
    private String aiIntegrationMode;


    @Override
    public CodeCandidate assignAiToTask(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        // Transition task to PLANNING
        task.setStatus(TaskStatus.PLANNING);
        taskRepository.saveAndFlush(task);

        Requirement requirement = task.getRequirement();
        if (requirement == null) {
            throw new IllegalStateException("Task must be linked to a requirement to assign to AI.");
        }

        Project project = task.getFeature().getEpic().getProject();
        String localPath = project.getLocalPath();
        if (localPath == null || localPath.trim().isEmpty()) {
            throw new IllegalStateException("Project local path is not configured.");
        }

        if ("crewai".equalsIgnoreCase(aiIntegrationMode)) {
            try {
                String userStoryText = (requirement.getUserStory() != null) ? requirement.getUserStory().getStoryText() : "";
                String acceptanceCriteriaText = (requirement.getAcceptanceCriteria() != null) ? requirement.getAcceptanceCriteria() : "";

                Map<String, String> payload = Map.of(
                    "taskId", taskId.toString(),
                    "requirementId", requirement.getId().toString(),
                    "requirementTitle", requirement.getTitle(),
                    "requirementDescription", requirement.getDescription() != null ? requirement.getDescription() : "",
                    "userStory", userStoryText,
                    "acceptanceCriteria", acceptanceCriteriaText
                );

                String jsonPayload = objectMapper.writeValueAsString(payload);
                log.info("Sending requirement decomposition request to FastAPI: {}", crewaiApiUrl + "/api/generate");
                String generateResponseText = postRequest(crewaiApiUrl + "/api/generate", jsonPayload);
                
                Map<String, Object> genResponse = objectMapper.readValue(generateResponseText, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                @SuppressWarnings("unchecked")
                Map<String, String> genData = (Map<String, String>) genResponse.get("data");
                String testSubtaskId = genData.get("test_subtask_id");
                String implSubtaskId = genData.get("impl_subtask_id");

                log.info("Executing BDD Agent on FastAPI for subtask: {}", testSubtaskId);
                postRequest(crewaiApiUrl + "/api/bdd/" + testSubtaskId, "{}");

                log.info("Executing TDD Agent on FastAPI for subtask: {}", implSubtaskId);
                postRequest(crewaiApiUrl + "/api/tdd/" + implSubtaskId, "{}");

                AgentSubtaskQueue testSubtask = agentSubtaskQueueRepository.findById(UUID.fromString(testSubtaskId))
                        .orElseThrow(() -> new IllegalStateException("Test subtask not found in DB: " + testSubtaskId));
                AgentSubtaskQueue implSubtask = agentSubtaskQueueRepository.findById(UUID.fromString(implSubtaskId))
                        .orElseThrow(() -> new IllegalStateException("Implementation subtask not found in DB: " + implSubtaskId));

                String filePath = implSubtask.getErrorLog(); 
                String testFilePath = testSubtask.getErrorLog(); 
                String proposedContent = implSubtask.getProposedCode();
                String testProposedContent = testSubtask.getTestCode();

                if (filePath == null || proposedContent == null) {
                    throw new IllegalStateException("CrewAI code generation returned null filePath or proposedContent");
                }

                filePath = filePath.replace("\\", "/");
                if (testFilePath != null) {
                    testFilePath = testFilePath.replace("\\", "/");
                }

                String cleanLocalPath = localPath.replace("\\", "/");
                if (filePath.startsWith(cleanLocalPath)) {
                    filePath = filePath.substring(cleanLocalPath.length());
                }
                while (filePath.startsWith("/")) {
                    filePath = filePath.substring(1);
                }

                if (testFilePath != null) {
                    if (testFilePath.startsWith(cleanLocalPath)) {
                        testFilePath = testFilePath.substring(cleanLocalPath.length());
                    }
                    while (testFilePath.startsWith("/")) {
                        testFilePath = testFilePath.substring(1);
                    }
                }

                String originalContent = null;
                File originalFile = new File(localPath, filePath);
                if (originalFile.exists() && originalFile.isFile()) {
                    originalContent = Files.readString(originalFile.toPath());
                }

                CodeCandidate candidate = CodeCandidate.builder()
                        .task(task)
                        .filePath(filePath)
                        .testFilePath(testFilePath)
                        .originalContent(originalContent)
                        .proposedContent(proposedContent)
                        .testProposedContent(testProposedContent)
                        .diffContent("CrewAI multi-agent proposed modifications for " + filePath)
                        .status("PENDING")
                        .build();

                candidate = codeCandidateRepository.save(candidate);

                task.setStatus(TaskStatus.TESTING);
                taskRepository.saveAndFlush(task);

                return candidate;

            } catch (Exception e) {
                log.error("Failed executing CrewAI agent workflow for Task: {}", task.getTitle(), e);
                task.setStatus(TaskStatus.BACKLOG);
                taskRepository.saveAndFlush(task);
                throw new RuntimeException("CrewAI workflow execution failed: " + e.getMessage(), e);
            }
        }


        // Get Code Area context
        List<CodeMapping> codeArea = javaParserService.getCodeArea(requirement.getId(), 2);
        StringBuilder filesContext = new StringBuilder();
        Set<String> processedFiles = new HashSet<>();

        for (CodeMapping mapping : codeArea) {
            JavaClass javaClass = mapping.getJavaClass();
            if (javaClass != null && javaClass.getFilePath() != null) {
                String relativePath = javaClass.getFilePath();
                if (processedFiles.add(relativePath)) {
                    File file = new File(localPath, relativePath);
                    if (file.exists() && file.isFile()) {
                        try {
                            String content = Files.readString(file.toPath());
                            filesContext.append("File Path: ").append(relativePath).append("\n");
                            filesContext.append("```java\n").append(content).append("\n```\n\n");
                        } catch (IOException e) {
                            log.warn("Failed to read context file: {}", file.getAbsolutePath(), e);
                        }
                    }
                }
            }
        }

        // Fetch UserStory and AcceptanceCriteria details
        String userStoryText = (requirement.getUserStory() != null) ? requirement.getUserStory().getStoryText() : "None";
        String acceptanceCriteriaText = (requirement.getAcceptanceCriteria() != null) ? requirement.getAcceptanceCriteria() : "None";

        // Query previous code candidates to build a self-healing loop for failures
        List<CodeCandidate> previousCandidates = codeCandidateRepository.findByTask_Id(task.getId());
        StringBuilder selfHealingContext = new StringBuilder();
        if (previousCandidates != null && !previousCandidates.isEmpty()) {
            // Sort by creation date descending to get the most recent attempt
            previousCandidates.sort((c1, c2) -> c2.getCreatedAt().compareTo(c1.getCreatedAt()));
            for (CodeCandidate c : previousCandidates) {
                boolean hasCompilationError = c.getVerificationLog() != null && 
                    (c.getVerificationLog().contains("COMPILATION ERROR") || c.getVerificationLog().contains("BUILD FAILURE"));
                boolean hasTestFailures = c.getTestResultsSummary() != null && 
                    c.getTestResultsSummary().contains("\"failures\":") && 
                    !c.getTestResultsSummary().contains("\"failures\":0");
                boolean hasTestErrors = c.getTestResultsSummary() != null && 
                    c.getTestResultsSummary().contains("\"errors\":") && 
                    !c.getTestResultsSummary().contains("\"errors\":0");

                if (hasCompilationError || hasTestFailures || hasTestErrors) {
                    selfHealingContext.append("ATTENTION: A previous attempt to write code for this task failed validation!\n");
                    selfHealingContext.append("Use these details to correct the code and prevent these errors in your new implementation:\n\n");
                    if (hasCompilationError) {
                        selfHealingContext.append("--- Compilation Failure Output ---\n");
                        selfHealingContext.append(c.getVerificationLog()).append("\n");
                    }
                    if (hasTestFailures || hasTestErrors) {
                        selfHealingContext.append("--- Unit Test Failure Output ---\n");
                        selfHealingContext.append(c.getTestExecutionLog()).append("\n");
                    }
                    selfHealingContext.append("--------------------------------\n\n");
                    break; // Only inject the most recent failure
                }
            }
        }

        // Form prompts
        String systemPrompt = "You are a Senior Principal Java Software Architect and Developer Agent. Your role is to understand a developer's requested feature/requirement, analyze the existing source files that are relevant to this requirement, and implement the necessary changes to fulfill the task.\n\n" +
                "You must return your response as a valid JSON object matching this exact structure:\n" +
                "{\n" +
                "  \"filePath\": \"src/main/java/com/dpi/platform/service/NewService.java\",\n" +
                "  \"proposedContent\": \"...the complete updated or new Java file contents...\"\n" +
                "}\n\n" +
                "CRITICAL RULES:\n" +
                "1. Return ONLY a valid JSON object. Do not wrap it in markdown block tags (like ```json ... ```) or prepend/append any conversational text.\n" +
                "2. The output MUST compile perfectly. Always write valid Java 21 code, ensuring all imports, package declarations, syntax rules, and annotations are fully correct.\n" +
                "3. You must include appropriate Javadoc comment annotations with @Requirement(id=\"req-uuid\") or @Task(id=\"task-uuid\") above class or method declarations to preserve full traceability. Use the actual UUID values:\n" +
                "   Requirement UUID: " + requirement.getId().toString() + "\n" +
                "   Task UUID: " + task.getId().toString() + "\n\n" +
                "Example Javadoc:\n" +
                "/**\n" +
                " * Performs progress calculation.\n" +
                " * @Requirement(id=\"" + requirement.getId().toString() + "\")\n" +
                " * @Task(id=\"" + task.getId().toString() + "\")\n" +
                " */";

        String userPrompt = "Project Context:\n" +
                "Project Name: " + project.getName() + "\n\n" +
                "Requirement Details:\n" +
                "- Title: " + requirement.getTitle() + "\n" +
                "- Description: " + requirement.getDescription() + "\n" +
                "- User Story: " + userStoryText + "\n" +
                "- Acceptance Criteria: " + acceptanceCriteriaText + "\n\n" +
                "Task Details:\n" +
                "- Title: " + task.getTitle() + "\n" +
                "- Description: " + task.getDescription() + "\n\n" +
                (selfHealingContext.length() > 0 ? selfHealingContext.toString() : "") +
                "Existing Files in the Code Area (Context):\n" +
                (filesContext.length() > 0 ? filesContext.toString() : "(No files are currently mapped to this requirement. You must create a new Java class file inside the project structure.)\n\n") +
                "Please implement the requested changes. Decide whether you should edit one of the existing files or create a new class. Provide the complete code for the file that should be written or updated.";

        // Clear existing session memory for this task before starting a fresh run
        agentMessageRepository.deleteByTask_Id(taskId);

        // Save prompts to database local memory (Option A)
        AgentMessage sysMessage = AgentMessage.builder()
                .task(task)
                .role("system")
                .content(systemPrompt)
                .createdAt(LocalDateTime.now())
                .build();
        agentMessageRepository.save(sysMessage);

        AgentMessage usrMessage = AgentMessage.builder()
                .task(task)
                .role("user")
                .content(userPrompt)
                .createdAt(LocalDateTime.now())
                .build();
        agentMessageRepository.save(usrMessage);

        // Fetch chronological messages
        List<AgentMessage> dbHistory = agentMessageRepository.findByTask_IdOrderByCreatedAtAsc(taskId);
        List<Map<String, String>> messages = new ArrayList<>();
        for (AgentMessage m : dbHistory) {
            messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }

        log.info("Triggering LLM code generation (with chat memory) for Task: {}", task.getTitle());
        String rawLlmResponse = groqClientService.generateCodeFromMessages(messages);
        log.info("LLM responded successfully for Task: {}", task.getTitle());

        // Save assistant response to database local memory
        AgentMessage astMessage = AgentMessage.builder()
                .task(task)
                .role("assistant")
                .content(rawLlmResponse)
                .createdAt(LocalDateTime.now())
                .build();
        agentMessageRepository.save(astMessage);

        try {
            // Clean markdown prefix/suffix if present
            String cleanedResponse = rawLlmResponse.trim();
            if (cleanedResponse.startsWith("```")) {
                int firstNewLine = cleanedResponse.indexOf("\n");
                int lastBackticks = cleanedResponse.lastIndexOf("```");
                if (firstNewLine != -1 && lastBackticks != -1) {
                    cleanedResponse = cleanedResponse.substring(firstNewLine + 1, lastBackticks).trim();
                }
            }

            Map<String, String> parsedResult = objectMapper.readValue(cleanedResponse, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
            String filePath = parsedResult.get("filePath");
            String proposedContent = parsedResult.get("proposedContent");

            if (filePath == null || proposedContent == null) {
                throw new IllegalArgumentException("Invalid LLM response payload: missing filePath or proposedContent");
            }

            // Standardize filePath to use forward slashes
            filePath = filePath.replace("\\", "/");

            // Make filePath relative if it is absolute or starts with localPath prefix
            String cleanLocalPath = localPath.replace("\\", "/");
            if (filePath.startsWith(cleanLocalPath)) {
                filePath = filePath.substring(cleanLocalPath.length());
            }
            while (filePath.startsWith("/")) {
                filePath = filePath.substring(1);
            }

            // Look up original file content if it exists
            String originalContent = null;
            File originalFile = new File(localPath, filePath);
            if (originalFile.exists() && originalFile.isFile()) {
                try {
                    originalContent = Files.readString(originalFile.toPath());
                } catch (IOException e) {
                    log.warn("Failed to read original file for diff baseline: {}", originalFile.getAbsolutePath(), e);
                }
            }

            // Save candidate to DB
            CodeCandidate candidate = CodeCandidate.builder()
                    .task(task)
                    .filePath(filePath)
                    .originalContent(originalContent)
                    .proposedContent(proposedContent)
                    .diffContent("Proposed modifications for " + filePath)
                    .status("PENDING")
                    .build();

            candidate = codeCandidateRepository.save(candidate);

            // Transition task to TESTING (acts as the Code Review state in the Kanban board)
            task.setStatus(TaskStatus.TESTING);
            taskRepository.saveAndFlush(task);

            return candidate;

        } catch (Exception e) {
            log.error("Failed to parse LLM response or save code candidate for Task: {}", task.getTitle(), e);
            // Revert task status to Backlog/Planning in case of failure
            task.setStatus(TaskStatus.BACKLOG);
            taskRepository.saveAndFlush(task);
            throw new RuntimeException("Code generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void cleanIntermediateMessages(UUID taskId) {
        log.info("Cleaning intermediate failed/retry chat history for task: {}", taskId);
        List<AgentMessage> history = agentMessageRepository.findByTask_IdOrderByCreatedAtAsc(taskId);
        if (history.size() > 3) {
            // Keep index 0 (system), index 1 (user initial prompt), and the last index (final success assistant)
            for (int i = 2; i < history.size() - 1; i++) {
                agentMessageRepository.delete(history.get(i));
            }
            agentMessageRepository.flush();
            log.info("Successfully pruned local task memory to system, user initial instruction, and final generated code.");
        }
    }

    private String postRequest(String urlStr, String jsonPayload) {
        log.info("postRequest to URL: {} with payload: {}", urlStr, jsonPayload);
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .version(java.net.http.HttpClient.Version.HTTP_1_1)
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(urlStr))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();
            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("HTTP Request failed with status: " + response.statusCode() + " - " + response.body());
            }
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("Error calling CrewAI Agent Service: " + e.getMessage(), e);
        }
    }
}
