package com.dpi.platform.dto;

import com.dpi.platform.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureCreateRequest {

    @NotBlank(message = "Feature name is required")
    private String name;

    private String description;

    @NotNull(message = "Priority is required")
    private Priority priority;

    @NotNull(message = "Epic ID is required")
    private UUID epicId;
}
