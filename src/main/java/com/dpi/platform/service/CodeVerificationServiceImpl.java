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

@Service
@Transactional
public class CodeVerificationServiceImpl implements CodeVerificationService {

    private static final Logger log = LoggerFactory.getLogger(CodeVerificationServiceImpl.class);

    @Autowired
    private CodeCandidateRepository codeCandidateRepository;

    @Autowired
    private AgentMessageRepository agentMessageRepository;

    @Autowired
    private GroqClientService groqClientService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public CodeCandidate verifyCandidate(UUID candidateId) {
        CodeCandidate candidate = codeCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));

        Project project = candidate.getTask().getFeature().getEpic().getProject();
        String localPath = project.getLocalPath();
        if (localPath == null || localPath.trim().isEmpty()) {
            throw new IllegalStateException("Project local path is not configured.");
        }

        String filePath = candidate.getFilePath();
        String cleanLocalPath = localPath.replace("\\", "/");
        if (filePath.startsWith(cleanLocalPath)) {
            filePath = filePath.substring(cleanLocalPath.length());
        }
        while (filePath.startsWith("/") || filePath.startsWith("\\")) {
            filePath = filePath.substring(1);
        }
        File targetFile = new File(localPath, filePath);
        File parentDir = targetFile.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        // Backup existing file state in memory
        boolean existedBefore = targetFile.exists();
        String backupContent = null;
        if (existedBefore) {
            try {
                backupContent = Files.readString(targetFile.toPath());
            } catch (IOException e) {
                log.error("Failed to read backup of original file: {}", targetFile.getAbsolutePath(), e);
            }
        }

        // Build OS specific command
        List<String> command = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            command.add("cmd.exe");
            command.add("/c");
            command.add("mvn");
            command.add("test-compile");
        } else {
            command.add("mvn");
            command.add("test-compile");
        }

        int retryLimit = 3;
        int attempt = 0;
        boolean compiledSuccessfully = false;
        String currentContent = candidate.getProposedContent();

        StringBuilder outputLog = new StringBuilder();
        outputLog.append("[DPIP Verification Engine] Starting compilation check via Maven...\n\n");

        while (attempt <= retryLimit && !compiledSuccessfully) {
            if (attempt > 0) {
                outputLog.append("\n[DPIP Verification Engine] --- Automatic Refinement Attempt ").append(attempt).append(" / ").append(retryLimit).append(" ---\n");
            }

            try {
                // Write current proposed content to disk
                Files.writeString(targetFile.toPath(), currentContent);
                log.info("Temporary candidate file written for verification (attempt {}): {}", attempt + 1, targetFile.getAbsolutePath());

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(new File(localPath));
                pb.redirectErrorStream(true);

                Process process = pb.start();

                StringBuilder currentAttemptLog = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        currentAttemptLog.append(line).append("\n");
                    }
                }

                boolean completed = process.waitFor(120, TimeUnit.SECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    currentAttemptLog.append("\n[DPIP Verification Engine] ERROR: Maven compilation timed out.\n");
                }

                int exitCode = completed ? process.exitValue() : -1;
                outputLog.append(currentAttemptLog);
                outputLog.append("\n[DPIP Verification Engine] Maven process exited with code: ").append(exitCode).append("\n");

                if (exitCode == 0) {
                    outputLog.append("[DPIP Verification Engine] SUCCESS: Code compiles successfully. ✅\n");
                    compiledSuccessfully = true;
                    // Save final compiled code
                    candidate.setProposedContent(currentContent);
                } else {
                    outputLog.append("[DPIP Verification Engine] FAILURE: Compilation failed. ❌\n");
                    if (attempt < retryLimit) {
                        attempt++;
                        outputLog.append("[DPIP Verification Engine] Automatically triggering self-healing loop with Groq AI...\n");

                        // Form prompt with errors
                        String failPrompt = "The code you generated failed compilation validation with this Maven log:\n\n" +
                                "```\n" + currentAttemptLog.toString() + "\n```\n\n" +
                                "Please analyze the error log, correct any unresolved compilation problems, syntax errors, or missing imports, and rewrite the class to correct these issues.";

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
                        log.info("Triggering LLM self-healing code generation (attempt {})", attempt);
                        String rawLlmResponse = groqClientService.generateCodeFromMessages(messages);
                        log.info("LLM responded successfully for self-healing");

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
                            outputLog.append("[DPIP Verification Engine] WARNING: LLM response did not contain proposedContent, continuing with previous code.\n");
                        }
                    } else {
                        // Max retries reached
                        attempt++;
                    }
                }

            } catch (Exception e) {
                log.error("Exception in verification refinement loop", e);
                outputLog.append("\n[DPIP Verification Engine] ERROR: Exception occurred during verification: ")
                        .append(e.getMessage()).append("\n");
                break;
            }
        }

        // Restore disk state back to original (clean sandboxing)
        try {
            if (existedBefore && backupContent != null) {
                Files.writeString(targetFile.toPath(), backupContent);
                log.info("Restored original file content: {}", targetFile.getAbsolutePath());
            } else if (targetFile.exists()) {
                Files.delete(targetFile.toPath());
                log.info("Removed temporary file: {}", targetFile.getAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to restore original file state after verification: {}", targetFile.getAbsolutePath(), e);
            outputLog.append("[DPIP Verification Engine] WARNING: Failed to clean up verification file on disk.\n");
        }

        // Save verification logs to DB
        candidate.setVerificationLog(outputLog.toString());
        return codeCandidateRepository.saveAndFlush(candidate);
    }

    private Map<String, String> parseLlmResponse(String rawResponse) throws Exception {
        String cleanedResponse = rawResponse.trim();
        if (cleanedResponse.startsWith("```")) {
            int firstNewLine = cleanedResponse.indexOf("\n");
            int lastBackticks = cleanedResponse.lastIndexOf("```");
            if (firstNewLine != -1 && lastBackticks != -1) {
                cleanedResponse = cleanedResponse.substring(firstNewLine + 1, lastBackticks).trim();
            }
        }
        return objectMapper.readValue(cleanedResponse, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
    }
}
