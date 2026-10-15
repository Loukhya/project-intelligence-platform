package com.dpi.platform.service;

import java.util.UUID;

public interface ProgressService {
    void recalculateFeatureProgress(UUID featureId);
}
