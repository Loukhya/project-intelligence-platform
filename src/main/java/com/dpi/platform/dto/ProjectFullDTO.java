package com.dpi.platform.dto;

import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Comprehensive project response containing full hierarchy:
 * Project → Epics → Features → Requirements (+ UserStories) + Tasks
 * Eliminates N+1 calls to /api/search from the frontend.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectFullDTO {

    private UUID id;
    private String name;
    private String description;
    private String category;
    private String difficulty;
    private String status;
    private Double progress;
    private String startDate;
    private String targetCompletionDate;
    private VisionDTO vision;
    private String repositoryUrl;
    private String localPath;

    @Builder.Default
    private List<EpicDTO> epics = new ArrayList<>();

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class VisionDTO {
        private UUID id;
        private String visionStatement;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EpicDTO {
        private UUID id;
        private String name;
        private String description;
        private String priority;
        private String status;
        private Double progress;
        @Builder.Default
        private List<FeatureDTO> features = new ArrayList<>();
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FeatureDTO {
        private UUID id;
        private String name;
        private String description;
        private String priority;
        private String status;
        private Double progress;
        private int taskCount;
        private int completedTaskCount;
        private int requirementCount;
        @Builder.Default
        private List<RequirementDTO> requirements = new ArrayList<>();
        @Builder.Default
        private List<TaskDTO> tasks = new ArrayList<>();
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RequirementDTO {
        private UUID id;
        private String title;
        private String description;
        private String complexity;
        private String priority;
        private String status;
        private String type;
        private Double estimatedEffort;
        private String notes;
        private String acceptanceCriteria;
        private UserStoryDTO userStory;
        private int linkedTaskCount;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UserStoryDTO {
        private UUID id;
        private String storyText;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TaskDTO {
        private UUID id;
        private String title;
        private String description;
        private String priority;
        private String status;
        private Double estimatedHours;
        private Double actualHours;
        private UUID featureId;
        private String featureName;
        private UUID requirementId;
        private String requirementTitle;
        private boolean hasCodeCandidate;
    }
}
