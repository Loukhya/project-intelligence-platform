package com.dpi.platform.service;

import com.dpi.platform.dto.RequirementCreateRequest;
import com.dpi.platform.dto.TechDecisionRequest;
import com.dpi.platform.dto.TechnologyCreateRequest;
import com.dpi.platform.dto.UserStoryCreateRequest;
import com.dpi.platform.model.*;
import com.dpi.platform.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RequirementTechServiceImpl implements RequirementTechService {

    private final FeatureRepository featureRepository;
    private final RequirementRepository requirementRepository;
    private final UserStoryRepository userStoryRepository;
    private final TechnologyRepository technologyRepository;
    private final TechnologyDecisionRepository technologyDecisionRepository;
    private final ProgressService progressService;

    @Autowired
    public RequirementTechServiceImpl(FeatureRepository featureRepository,
            RequirementRepository requirementRepository,
            UserStoryRepository userStoryRepository,
            TechnologyRepository technologyRepository,
            TechnologyDecisionRepository technologyDecisionRepository,
            ProgressService progressService) {
        this.featureRepository = featureRepository;
        this.requirementRepository = requirementRepository;
        this.userStoryRepository = userStoryRepository;
        this.technologyRepository = technologyRepository;
        this.technologyDecisionRepository = technologyDecisionRepository;
        this.progressService = progressService;
    }

    @Override
    public Requirement createRequirement(RequirementCreateRequest request) {
        Feature feature = featureRepository.findById(request.getFeatureId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature not found"));

        Requirement requirement = Requirement.builder()
                .feature(feature)
                .title(request.getTitle())
                .description(request.getDescription())
                .complexity(request.getComplexity())
                .priority(request.getPriority())
                .status(request.getStatus())
                .type(request.getType())
                .estimatedEffort(request.getEstimatedEffort())
                .notes(request.getNotes())
                .acceptanceCriteria(request.getAcceptanceCriteria())
                .build();

        Requirement savedRequirement = requirementRepository.save(requirement);
        if (progressService != null) {
            progressService.recalculateFeatureProgress(feature.getId());
        }
        return savedRequirement;
    }

    @Override
    public Requirement getRequirement(UUID id) {
        return requirementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requirement not found"));
    }

    @Override
    public UserStory attachUserStory(UUID requirementId, UserStoryCreateRequest request) {
        Requirement requirement = getRequirement(requirementId);
        UserStory userStory = requirement.getUserStory();
        if (userStory == null) {
            userStory = UserStory.builder()
                    .requirement(requirement)
                    .storyText(request.getStoryText())
                    .build();
            requirement.setUserStory(userStory);
        } else {
            userStory.setStoryText(request.getStoryText());
        }
        return userStoryRepository.save(userStory);
    }

    @Override
    public UserStory getUserStory(UUID requirementId) {
        Requirement requirement = getRequirement(requirementId);
        UserStory userStory = requirement.getUserStory();
        if (userStory == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User Story not found for requirement");
        }
        return userStory;
    }

    @Override
    public Technology createTechnology(TechnologyCreateRequest request) {
        Technology technology = Technology.builder()
                .name(request.getName())
                .category(request.getCategory())
                .description(request.getDescription())
                .officialDocumentation(request.getOfficialDocumentation())
                .notes(request.getNotes())
                .build();
        return technologyRepository.save(technology);
    }

    @Override
    public Technology getTechnology(UUID id) {
        return technologyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Technology not found"));
    }

    @Override
    public List<Technology> getAllTechnologies() {
        return technologyRepository.findAll();
    }

    @Override
    public void addTechnologyCandidates(UUID requirementId, List<UUID> technologyIds) {
        Requirement requirement = getRequirement(requirementId);
        for (UUID techId : technologyIds) {
            Technology tech = getTechnology(techId);
            requirement.getCandidates().add(tech);
        }
        requirementRepository.save(requirement);
    }

    @Override
    public List<Technology> getTechnologyCandidates(UUID requirementId) {
        Requirement requirement = getRequirement(requirementId);
        return new ArrayList<>(requirement.getCandidates());
    }

    /**
     * Records a selected technology decision for a requirement.
     * @Requirement(id="c9d0e1f2-a3b4-5c6d-7e8f-9a0b1c2d3e4f")
     */
    @Override
    public TechnologyDecision selectTechnology(UUID requirementId, TechDecisionRequest request) {
        Requirement requirement = getRequirement(requirementId);
        Technology technology = getTechnology(request.getTechnologyId());

        TechnologyDecision decision = TechnologyDecision.builder()
                .requirement(requirement)
                .technology(technology)
                .whySelected(request.getWhySelected())
                .advantages(request.getAdvantages())
                .tradeoffs(request.getTradeoffs())
                .notes(request.getNotes())
                .build();

        TechnologyDecision savedDecision = technologyDecisionRepository.save(decision);
        if (progressService != null && requirement.getFeature() != null) {
            progressService.recalculateFeatureProgress(requirement.getFeature().getId());
        }
        return savedDecision;
    }

    @Override
    public List<TechnologyDecision> getTechnologyDecisions(UUID requirementId) {
        return technologyDecisionRepository.findByRequirementId(requirementId);
    }
}
