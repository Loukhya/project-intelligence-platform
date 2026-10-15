package com.dpi.platform.service;

import com.dpi.platform.model.CodeCandidate;
import java.util.UUID;

public interface CodeVerificationService {
    CodeCandidate verifyCandidate(UUID candidateId);
}
