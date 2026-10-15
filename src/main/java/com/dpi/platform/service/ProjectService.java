package com.dpi.platform.service;

import com.dpi.platform.dto.ProjectCreateRequest;
import com.dpi.platform.dto.ProjectFullDTO;
import com.dpi.platform.dto.VisionUpdateRequest;
import com.dpi.platform.model.Project;
import com.dpi.platform.model.Vision;
import java.util.List;
import java.util.UUID;

public interface ProjectService {
    Project createProject(ProjectCreateRequest request);
    Project getProject(UUID id);
    List<Project> getAllProjects();
    Vision setProjectVision(UUID projectId, VisionUpdateRequest request);
    Vision getProjectVision(UUID projectId);
    void deleteProject(UUID id);
    ProjectFullDTO getProjectFull(UUID id);
    Project seedSampleProject();
    Project restructureMathProject(UUID id);
    Project updateProjectGitHub(UUID id, String repoOwner, String repoName);
}
