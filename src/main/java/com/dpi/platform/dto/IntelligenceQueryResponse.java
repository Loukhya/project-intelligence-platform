package com.dpi.platform.dto;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntelligenceQueryResponse {
    private String answer;
    
    @Builder.Default
    private List<String> referencedEntityIds = new ArrayList<>();
}
