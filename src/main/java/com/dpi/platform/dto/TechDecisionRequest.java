package com.dpi.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechDecisionRequest {

    @NotNull(message = "Technology ID is required")
    private UUID technologyId;

    @NotBlank(message = "Selection reason is required")
    private String whySelected;

    private String advantages;
    private String tradeoffs;
    private String notes;
}
