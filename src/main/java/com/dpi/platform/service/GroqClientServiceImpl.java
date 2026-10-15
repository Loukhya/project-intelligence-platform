package com.dpi.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GroqClientServiceImpl implements GroqClientService {

    private static final Logger log = LoggerFactory.getLogger(GroqClientServiceImpl.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key:}")
    private String apiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String apiUrl;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String model;

    public GroqClientServiceImpl(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String generateCode(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));
        return generateCodeFromMessages(messages);
    }

    @Override
    public String generateCodeFromMessages(List<Map<String, String>> messages) {
        String activeKey = (this.apiKey != null && !this.apiKey.trim().isEmpty()) ? this.apiKey : System.getenv("GROQ_API_KEY");
        if (activeKey == null || activeKey.trim().isEmpty()) {
            throw new IllegalStateException("Groq API Key is not configured. Please set the GROQ_API_KEY environment variable or the spring property 'groq.api.key'.");
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.1);
            requestBody.put("response_format", Map.of("type", "json_object"));

            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + activeKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            log.info("Sending message-list generation request to Groq model: {}", model);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Groq API returned status code {}: {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to generate code from Groq. Status code: " + response.statusCode());
            }

            Map<String, Object> responseMap = objectMapper.readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("No choices returned from Groq API response.");
            }

            Map<String, Object> choice = choices.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            if (message == null || !message.containsKey("content")) {
                throw new RuntimeException("No message content returned from Groq API response.");
            }

            return (String) message.get("content");

        } catch (Exception e) {
            log.error("Error communicating with Groq API", e);
            throw new RuntimeException("Error during Groq code generation: " + e.getMessage(), e);
        }
    }
}
