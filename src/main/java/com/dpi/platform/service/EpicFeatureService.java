package com.dpi.platform.service;

import com.dpi.platform.dto.EpicCreateRequest;
import com.dpi.platform.dto.FeatureCreateRequest;
import com.dpi.platform.model.Epic;
import com.dpi.platform.model.Feature;
import com.dpi.platform.model.FeatureStatus;
import java.util.UUID;

public interface EpicFeatureService {
    Epic createEpic(EpicCreateRequest request);
    Epic getEpic(UUID id);
    Feature createFeature(FeatureCreateRequest request);
    Feature getFeature(UUID id);
    void addFeatureDependency(UUID sourceId, UUID targetId);
    Feature transitionFeatureStatus(UUID featureId, FeatureStatus newStatus);
}
