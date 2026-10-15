package com.dpi.platform.service;

import com.dpi.platform.dto.RequirementCreateRequest;
import com.dpi.platform.dto.TechDecisionRequest;
import com.dpi.platform.dto.TechnologyCreateRequest;
import com.dpi.platform.dto.UserStoryCreateRequest;
import com.dpi.platform.model.Requirement;
import com.dpi.platform.model.Technology;
import com.dpi.platform.model.TechnologyDecision;
import com.dpi.platform.model.UserStory;
import java.util.List;
import java.util.UUID;

public interface RequirementTechService {
    Requirement createRequirement(RequirementCreateRequest request);
    Requirement getRequirement(UUID id);
    
    UserStory attachUserStory(UUID requirementId, UserStoryCreateRequest request);
    UserStory getUserStory(UUID requirementId);

    Technology createTechnology(TechnologyCreateRequest request);
    Technology getTechnology(UUID id);
    List<Technology> getAllTechnologies();

    void addTechnologyCandidates(UUID requirementId, List<UUID> technologyIds);
    List<Technology> getTechnologyCandidates(UUID requirementId);

    TechnologyDecision selectTechnology(UUID requirementId, TechDecisionRequest request);
    List<TechnologyDecision> getTechnologyDecisions(UUID requirementId);
}
