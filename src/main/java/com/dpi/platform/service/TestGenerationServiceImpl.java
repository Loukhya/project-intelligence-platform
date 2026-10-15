package com.dpi.platform.service;

import com.dpi.platform.model.CodeCandidate;
import com.dpi.platform.repository.CodeCandidateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Transactional
public class TestGenerationServiceImpl implements TestGenerationService {

    private static final Logger log = LoggerFactory.getLogger(TestGenerationServiceImpl.class);

    @Autowired
    private GroqClientService groqClientService;

    @Autowired
    private CodeCandidateRepository codeCandidateRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public CodeCandidate generateTestSuite(CodeCandidate candidate) {
        if (candidate.getTestProposedContent() != null && !candidate.getTestProposedContent().trim().isEmpty()) {
            log.info("Test suite already exists for candidate ID: {}. Skipping generation.", candidate.getId());
            return candidate;
        }

        String systemPrompt = "You are an expert Java developer and tester specializing in JUnit 5 unit testing. Your goal is to write a comprehensive suite of JUnit 5 unit tests for a given Java class.\n\n" +
                "You must return your response as a valid JSON object matching this exact structure:\n" +
                "{\n" +
                "  \"testFilePath\": \"src/test/java/com/mathutil/MathServiceTest.java\",\n" +
                "  \"testProposedContent\": \"...the complete compiling JUnit 5 test class code...\"\n" +
                "}\n\n" +
                "CRITICAL RULES:\n" +
                "1. Return ONLY a valid JSON object. Do not wrap it in markdown block tags (like ```json ... ```) or prepend/append any conversational text.\n" +
                "2. Write clean, compiling JUnit 5 code. Use package matching the original class. Import org.junit.jupiter.api.Test, and static org.junit.jupiter.api.Assertions.*.\n" +
                "3. Ensure all test cases compile and run correctly against the provided proposed class implementation.";

        String userPrompt = "Target Java Source File Path: " + candidate.getFilePath() + "\n\n" +
                "Target Java Class Code:\n" +
                "```java\n" +
                candidate.getProposedContent() +
                "\n```\n\n" +
                "Please generate a corresponding JUnit 5 unit test class for this class. Provide the complete code for the test file.";

        log.info("Triggering LLM test suite generation for candidate: {}", candidate.getFilePath());
        String rawLlmResponse = groqClientService.generateCode(systemPrompt, userPrompt);
        log.info("LLM test suite generated successfully.");

        try {
            // Clean markdown blocks if present
            String cleanedResponse = rawLlmResponse.trim();
            if (cleanedResponse.startsWith("```")) {
                int firstNewLine = cleanedResponse.indexOf("\n");
                int lastBackticks = cleanedResponse.lastIndexOf("```");
                if (firstNewLine != -1 && lastBackticks != -1) {
                    cleanedResponse = cleanedResponse.substring(firstNewLine + 1, lastBackticks).trim();
                }
            }

            Map<String, String> parsedResult = objectMapper.readValue(cleanedResponse, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
            String testFilePath = parsedResult.get("testFilePath");
            String testProposedContent = parsedResult.get("testProposedContent");

            if (testFilePath == null || testProposedContent == null) {
                throw new IllegalArgumentException("Invalid LLM response payload: missing testFilePath or testProposedContent");
            }

            // Standardize path separators
            testFilePath = testFilePath.replace("\\", "/");

            // Make filePath relative if it is absolute or starts with localPath prefix
            com.dpi.platform.model.Project project = candidate.getTask().getFeature().getEpic().getProject();
            String localPath = project.getLocalPath();
            if (localPath != null) {
                String cleanLocalPath = localPath.replace("\\", "/");
                if (testFilePath.startsWith(cleanLocalPath)) {
                    testFilePath = testFilePath.substring(cleanLocalPath.length());
                }
                while (testFilePath.startsWith("/")) {
                    testFilePath = testFilePath.substring(1);
                }
            }

            candidate.setTestFilePath(testFilePath);
            candidate.setTestProposedContent(testProposedContent);

            return codeCandidateRepository.saveAndFlush(candidate);

        } catch (Exception e) {
            log.error("Failed to parse LLM test generation response", e);
            throw new RuntimeException("Test generation parsing failed: " + e.getMessage(), e);
        }
    }
}
