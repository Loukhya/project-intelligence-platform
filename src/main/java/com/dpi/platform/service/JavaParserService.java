package com.dpi.platform.service;

import com.dpi.platform.model.CodeMapping;
import java.util.List;
import java.util.UUID;

public interface JavaParserService {
    void scanProject(UUID projectId);
    List<CodeMapping> getCodeArea(UUID requirementId, int maxDepth);
    List<CodeMapping> getProjectMappings(UUID projectId);
    List<com.dpi.platform.model.MethodCall> getProjectMethodCalls(UUID projectId);
}
