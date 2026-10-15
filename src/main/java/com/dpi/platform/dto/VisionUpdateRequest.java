package com.dpi.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisionUpdateRequest {

    @NotBlank(message = "Vision statement is required")
    private String visionStatement;
}
