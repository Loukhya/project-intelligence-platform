package com.dpi.platform.service;

import com.dpi.platform.model.CodeCandidate;
import com.dpi.platform.model.Project;
import com.dpi.platform.model.AgentMessage;
import com.dpi.platform.repository.CodeCandidateRepository;
import com.dpi.platform.repository.AgentMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class CodeExecutionServiceImpl implements CodeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutionServiceImpl.class);

    private static final Pattern TEST_RESULT_PATTERN = Pattern.compile("Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+)(?:,\\s*Skipped:\\s*(\\d+))?");

    @Autowired
    private CodeCandidateRepository codeCandidateRepository;

    @Autowired
    private TestGenerationService testGenerationService;

    @Autowired
    private AgentMessageRepository agentMessageRepository;

    @Autowired
    private GroqClientService groqClientService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public CodeCandidate runTestSuite(UUID candidateId) {
        CodeCandidate candidate = codeCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));

        // 1. Generate tests if they don't exist yet
        if (candidate.getTestProposedContent() == null || candidate.getTestProposedContent().trim().isEmpty()) {
            candidate = testGenerationService.generateTestSuite(candidate);
        }

        Project project = candidate.getTask().getFeature().getEpic().getProject();
        String localPath = project.getLocalPath();
        if (localPath == null || localPath.trim().isEmpty()) {
            throw new IllegalStateException("Project local path is not configured.");
        }

        // Setup candidate target files
        String filePath = candidate.getFilePath();
        String testFilePath = candidate.getTestFilePath();
        String cleanLocalPath = localPath.replace("\\", "/");

        if (filePath.startsWith(cleanLocalPath)) {
            filePath = filePath.substring(cleanLocalPath.length());
        }
        while (filePath.startsWith("/") || filePath.startsWith("\\")) {
            filePath = filePath.substring(1);
        }

        if (testFilePath != null) {
            if (testFilePath.startsWith(cleanLocalPath)) {
                testFilePath = testFilePath.substring(cleanLocalPath.length());
            }
            while (testFilePath.startsWith("/") || testFilePath.startsWith("\\")) {
                testFilePath = testFilePath.substring(1);
            }
        }

        File mainFile = new File(localPath, filePath);
        File testFile = new File(localPath, testFilePath);

        // Backups
        boolean mainExisted = mainFile.exists();
        String mainBackup = null;
        if (mainExisted) {
            try {
                mainBackup = Files.readString(mainFile.toPath());
            } catch (IOException e) {
                log.error("Failed to read main source backup: {}", mainFile.getAbsolutePath(), e);
            }
        }

        boolean testExisted = testFile.exists();
        String testBackup = null;
        if (testExisted) {
            try {
                testBackup = Files.readString(testFile.toPath());
            } catch (IOException e) {
                log.error("Failed to read test source backup: {}", testFile.getAbsolutePath(), e);
            }
        }

        // Build Maven test command
        String testFileName = testFile.getName();
        String testClassName = testFileName.endsWith(".java") 
                ? testFileName.substring(0, testFileName.length() - 5) 
                : testFileName;

        List<String> command = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            command.add("cmd.exe");
            command.add("/c");
            command.add("mvn");
            command.add("test");
            command.add("-Dtest=" + testClassName);
        } else {
            command.add("mvn");
            command.add("test");
            command.add("-Dtest=" + testClassName);
        }

        StringBuilder runLog = new StringBuilder();
        runLog.append("[DPIP Execution Engine] Starting unit test verification via Maven...\\n\\n");

        int retryLimit = 3;
        int attempt = 0;
        boolean success = false;
        String currentContent = candidate.getProposedContent();

        int testsRunCount = 0;
        int failuresCount = 0;
        int errorsCount = 0;
        int skippedCount = 0;

        while (attempt <= retryLimit && !success) {
            if (attempt > 0) {
                runLog.append("\\n[DPIP Execution Engine] --- Automatic Refinement Attempt ").append(attempt).append(" / ").append(retryLimit).append(" ---\\n");
            }

            try {
                // Write files to disk
                mainFile.getParentFile().mkdirs();
                testFile.getParentFile().mkdirs();
                Files.writeString(mainFile.toPath(), currentContent);
                Files.writeString(testFile.toPath(), candidate.getTestProposedContent());
                log.info("Wrote source & test files temporarily (attempt {}): {} & {}", attempt + 1, mainFile.getName(), testFile.getName());

                runLog.append("[DPIP Execution Engine] Spawned Maven: mvn test -Dtest=").append(testClassName).append("\\n");

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(new File(localPath));
                pb.redirectErrorStream(true);

                Process process = pb.start();

                StringBuilder currentAttemptLog = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        currentAttemptLog.append(line).append("\\n");

                        // Parse test metrics from stdout line
                        Matcher matcher = TEST_RESULT_PATTERN.matcher(line);
                        if (matcher.find()) {
                            testsRunCount = Integer.parseInt(matcher.group(1));
                            failuresCount = Integer.parseInt(matcher.group(2));
                            errorsCount = Integer.parseInt(matcher.group(3));
                            if (matcher.group(4) != null) {
                                skippedCount = Integer.parseInt(matcher.group(4));
                            }
                        }
                    }
                }

                boolean completed = process.waitFor(120, TimeUnit.SECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    currentAttemptLog.append("\\n[DPIP Execution Engine] ERROR: Test execution timed out.\\n");
                }

                int exitCode = completed ? process.exitValue() : -1;
                runLog.append(currentAttemptLog);
                runLog.append("\\n[DPIP Execution Engine] Maven process completed with exit code: ").append(exitCode).append("\\n");

                if (exitCode == 0 && testsRunCount > 0 && failuresCount == 0 && errorsCount == 0) {
                    success = true;
                    runLog.append("[DPIP Execution Engine] SUCCESS: All test assertions passed successfully! 🏆\\n");
                    // Save final passing source content
                    candidate.setProposedContent(currentContent);
                } else {
                    runLog.append("[DPIP Execution Engine] FAILURE: Unit tests failed or compile errors occurred. ❌\\n");
                    if (attempt < retryLimit) {
                        attempt++;
                        
                        // Check if the compilation error lies inside the unit test suite itself
                        if (currentAttemptLog.toString().contains("[ERROR]") && currentAttemptLog.toString().contains(testClassName)) {
                            runLog.append("[DPIP Execution Engine] Detected compilation error in the unit test class itself. Triggering test suite self-healing...\\n");

                            String healSystemPrompt = "You are an expert Java developer and tester specializing in JUnit 5 unit testing. Your goal is to fix compilation errors in a given JUnit 5 test class.\\n\\n" +
                                    "You must return your response as a valid JSON object matching this exact structure:\\n" +
                                    "{\\n" +
                                    "  \"testFilePath\": \"" + testFilePath + "\",\\n" +
                                    "  \"testProposedContent\": \"...the complete compiling JUnit 5 test class code...\"\\n" +
                                    "}\\n\\n" +
                                    "CRITICAL RULES:\\n" +
                                    "1. Return ONLY a valid JSON object. Do not wrap it in markdown block tags (like ```json ... ```) or prepend/append any conversational text.\\n" +
                                    "2. Fix all compilation errors in the test class. Ensure all called methods exist and are public in the source class. Add all missing imports (such as java.util.InputMismatchException or java.util.NoSuchElementException).\\n" +
                                    "3. Return the complete updated test class code.";

                            String healUserPrompt = "Source Class Code:\\n" +
                                    "```java\\n" + currentContent + "\\n```\\n\\n" +
                                    "Current Test Class Code:\\n" +
                                    "```java\\n" + candidate.getTestProposedContent() + "\\n```\\n\\n" +
                                    "Compilation Error Log:\\n" +
                                    "```\\n" + currentAttemptLog.toString() + "\\n```\\n\\n" +
                                    "Please regenerate and correct the unit test class to resolve these errors.";

                            log.info("Triggering LLM test-suite self-healing generation (attempt {})", attempt);
                            String rawLlmResponse = groqClientService.generateCode(healSystemPrompt, healUserPrompt);
                            log.info("LLM responded successfully for test-suite self-healing");

                            // Parse the updated test code content
                            Map<String, String> parsedResult = parseLlmResponse(rawLlmResponse);
                            String updatedTestProposedContent = parsedResult.get("testProposedContent");
                            if (updatedTestProposedContent != null) {
                                candidate.setTestProposedContent(updatedTestProposedContent);
                            } else {
                                runLog.append("[DPIP Execution Engine] WARNING: LLM response did not contain testProposedContent, continuing with previous test code.\\n");
                            }
                        } else {
                            runLog.append("[DPIP Execution Engine] Detected test assertion failure or source compilation error. Triggering source code self-healing...\\n");

                            // Form prompt with errors
                            String failPrompt = "The code you generated failed unit test validation with this Maven log:\\n\\n" +
                                    "```\\n" + currentAttemptLog.toString() + "\\n```\\n\\n" +
                                    "Please analyze the unit test failures, assertions, or syntax errors, correct the source code, and rewrite the class to pass all unit tests.";

                            // Save user prompt to local memory
                            AgentMessage failMsg = AgentMessage.builder()
                                    .task(candidate.getTask())
                                    .role("user")
                                    .content(failPrompt)
                                    .createdAt(LocalDateTime.now())
                                    .build();
                            agentMessageRepository.save(failMsg);

                            // Fetch chronological history
                            List<AgentMessage> dbHistory = agentMessageRepository.findByTask_IdOrderByCreatedAtAsc(candidate.getTask().getId());
                            List<Map<String, String>> messages = new ArrayList<>();
                            for (AgentMessage m : dbHistory) {
                                messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
                            }

                            // Call Groq to refine the code
                            log.info("Triggering LLM source-code self-healing generation (attempt {})", attempt);
                            String rawLlmResponse = groqClientService.generateCodeFromMessages(messages);
                            log.info("LLM responded successfully for source-code self-healing");

                            // Save assistant response to memory
                            AgentMessage nextAstMsg = AgentMessage.builder()
                                    .task(candidate.getTask())
                                    .role("assistant")
                                    .content(rawLlmResponse)
                                    .createdAt(LocalDateTime.now())
                                    .build();
                            agentMessageRepository.save(nextAstMsg);

                            // Parse the updated code content
                            Map<String, String> parsedResult = parseLlmResponse(rawLlmResponse);
                            String proposedContent = parsedResult.get("proposedContent");
                            if (proposedContent != null) {
                                currentContent = proposedContent;
                            } else {
                                runLog.append("[DPIP Execution Engine] WARNING: LLM response did not contain proposedContent, continuing with previous code.\\n");
                            }
                        }
                    } else {
                        // Max retries reached
                        attempt++;
                    }
                }

            } catch (Exception e) {
                log.error("Exception in test execution loop", e);
                runLog.append("\\n[DPIP Execution Engine] ERROR: Exception occurred: ").append(e.getMessage()).append("\\n");
                break;
            }
        }

        // Restore disk state
        try {
            if (mainExisted && mainBackup != null) {
                Files.writeString(mainFile.toPath(), mainBackup);
            } else if (mainFile.exists()) {
                Files.delete(mainFile.toPath());
            }

            if (testExisted && testBackup != null) {
                Files.writeString(testFile.toPath(), testBackup);
            } else if (testFile.exists()) {
                Files.delete(testFile.toPath());
            }
            log.info("Restored disk state successfully.");
        } catch (IOException e) {
            log.error("Failed to restore project files after test execution", e);
            runLog.append("[DPIP Execution Engine] WARNING: Failed to clean up temp files on disk.\\n");
        }

        // Form results summary JSON
        String summaryJson = String.format(
                "{\"testsRun\":%d,\"failures\":%d,\"errors\":%d,\"skipped\":%d,\"passed\":%b}",
                testsRunCount, failuresCount, errorsCount, skippedCount, success
        );

        candidate.setTestExecutionLog(runLog.toString());
        candidate.setTestResultsSummary(summaryJson);

        return codeCandidateRepository.saveAndFlush(candidate);
    }

    private Map<String, String> parseLlmResponse(String rawResponse) throws Exception {
        String cleanedResponse = rawResponse.trim();
        if (cleanedResponse.startsWith("```")) {
            int firstNewLine = cleanedResponse.indexOf("\\n");
            int lastBackticks = cleanedResponse.lastIndexOf("```");
            if (firstNewLine != -1 && lastBackticks != -1) {
                cleanedResponse = cleanedResponse.substring(firstNewLine + 1, lastBackticks).trim();
            }
        }
        return objectMapper.readValue(cleanedResponse, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
    }
}
