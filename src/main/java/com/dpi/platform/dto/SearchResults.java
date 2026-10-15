package com.dpi.platform.dto;

import com.dpi.platform.model.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResults {
    @Builder.Default
    private List<Project> projects = new ArrayList<>();
    @Builder.Default
    private List<Epic> epics = new ArrayList<>();
    @Builder.Default
    private List<Feature> features = new ArrayList<>();
    @Builder.Default
    private List<Requirement> requirements = new ArrayList<>();
    @Builder.Default
    private List<Task> tasks = new ArrayList<>();
    @Builder.Default
    private List<Technology> technologies = new ArrayList<>();
}
