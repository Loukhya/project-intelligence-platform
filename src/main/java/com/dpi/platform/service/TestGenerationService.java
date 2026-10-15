package com.dpi.platform.service;

import com.dpi.platform.model.CodeCandidate;

public interface TestGenerationService {
    CodeCandidate generateTestSuite(CodeCandidate candidate);
}
