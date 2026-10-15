package com.dpi.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStoryCreateRequest {

    @NotBlank(message = "Story text is required")
    private String storyText;
}
