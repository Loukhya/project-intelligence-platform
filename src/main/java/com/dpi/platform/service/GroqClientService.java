package com.dpi.platform.service;

import java.util.List;
import java.util.Map;

public interface GroqClientService {
    String generateCode(String systemPrompt, String userPrompt);
    String generateCodeFromMessages(List<Map<String, String>> messages);
}
