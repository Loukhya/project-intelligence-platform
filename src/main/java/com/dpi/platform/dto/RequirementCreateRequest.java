package com.dpi.platform.dto;

import com.dpi.platform.model.Complexity;
import com.dpi.platform.model.Priority;
import com.dpi.platform.model.RequirementStatus;
import com.dpi.platform.model.RequirementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequirementCreateRequest {

    @NotNull(message = "Feature ID is required")
    private UUID featureId;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Complexity is required")
    private Complexity complexity;

    @NotNull(message = "Priority is required")
    private Priority priority;

    private RequirementStatus status;

    private RequirementType type;

    private Double estimatedEffort;
    private String notes;
    private String acceptanceCriteria;
}
