package com.dpi.platform.service;

import com.dpi.platform.model.CodeCandidate;
import java.util.UUID;

public interface AiGatewayService {
    CodeCandidate assignAiToTask(UUID taskId);
    void cleanIntermediateMessages(UUID taskId);
}
