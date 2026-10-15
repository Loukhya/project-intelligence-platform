package com.dpi.platform.dto;

import com.dpi.platform.model.TechCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnologyCreateRequest {

    @NotBlank(message = "Technology name is required")
    private String name;

    @NotNull(message = "Category is required")
    private TechCategory category;

    private String description;
    private String officialDocumentation;
    private String notes;
}
