package com.dpi.platform.dto;

import com.dpi.platform.model.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskStatusUpdateRequest {

    @NotNull(message = "New status is required")
    private TaskStatus newStatus;

    private Double actualHours;
}
