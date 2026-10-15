package com.dpi.platform.dto;

import com.dpi.platform.model.Difficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectCreateRequest {

    @NotBlank(message = "Project name is required")
    private String name;

    private String description;

    @NotBlank(message = "Category is required")
    private String category;

    @NotNull(message = "Difficulty is required")
    private Difficulty difficulty;

    private LocalDate startDate;
    private LocalDate targetCompletionDate;
}
