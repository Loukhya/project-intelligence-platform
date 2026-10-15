package com.dpi.platform.service;

import com.dpi.platform.model.CodeCandidate;
import java.util.UUID;

public interface CodeExecutionService {
    CodeCandidate runTestSuite(UUID candidateId);
}
