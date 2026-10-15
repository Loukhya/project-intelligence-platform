package com.dpi.platform.service;

import com.dpi.platform.dto.IntelligenceQueryResponse;
import java.util.UUID;

public interface ProjectIntelligenceService {
    IntelligenceQueryResponse queryProject(UUID projectId, String query);
}
