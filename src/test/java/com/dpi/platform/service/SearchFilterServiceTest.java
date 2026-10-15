package com.dpi.platform.service;

import com.dpi.platform.dto.SearchResults;
import com.dpi.platform.model.*;
import com.dpi.platform.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Search & Filter Service Tests")
class SearchFilterServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private EpicRepository epicRepository;

    @Mock
    private FeatureRepository featureRepository;

    @Mock
    private RequirementRepository requirementRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TechnologyRepository technologyRepository;

    @Mock
    private TechnologyDecisionRepository technologyDecisionRepository;

    @InjectMocks
    private SearchServiceImpl searchService;

    @Test
    @DisplayName("Given keyword, when searching, then return all matching entities by name or description")
    void shouldSearchByKeyword() {
        // Arrange
        String query = "database";
        Feature feature = Feature.builder().name("Database Layer").description("Map JPA entities").build();
        Task task = Task.builder().title("Design schema").description("Database layout design").status(TaskStatus.BACKLOG).build();
        Technology technology = Technology.builder().name("PostgreSQL").description("Relational database").build();

        when(projectRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query))
                .thenReturn(Collections.emptyList());
        when(epicRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query))
                .thenReturn(Collections.emptyList());
        when(featureRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query))
                .thenReturn(Collections.singletonList(feature));
        when(requirementRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query))
                .thenReturn(Collections.emptyList());
        when(taskRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query))
                .thenReturn(Collections.singletonList(task));
        when(technologyRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query))
                .thenReturn(Collections.singletonList(technology));

        // Act
        SearchResults results = searchService.search(query, null, null, null, null, null);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.getFeatures().size());
        assertEquals("Database Layer", results.getFeatures().get(0).getName());
        assertEquals(1, results.getTasks().size());
        assertEquals("Design schema", results.getTasks().get(0).getTitle());
        assertEquals(1, results.getTechnologies().size());
        assertEquals("PostgreSQL", results.getTechnologies().get(0).getName());

        verify(projectRepository, times(1)).findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query);
        verify(epicRepository, times(1)).findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query);
        verify(featureRepository, times(1)).findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query);
        verify(requirementRepository, times(1)).findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query);
        verify(taskRepository, times(1)).findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query);
        verify(technologyRepository, times(1)).findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query);
    }

    @Test
    @DisplayName("Given filters, when searching, then filter results by priority and status")
    void shouldFilterSearchResults() {
        // Arrange
        String query = "";
        Task t1 = Task.builder().title("Create User Entity").priority(Priority.HIGH).status(TaskStatus.IN_PROGRESS).build();
        Task t2 = Task.builder().title("Implement JWT").priority(Priority.HIGH).status(TaskStatus.BACKLOG).build();
        Task t3 = Task.builder().title("Write migrations").priority(Priority.LOW).status(TaskStatus.IN_PROGRESS).build();

        // When query is empty, we find all tasks
        when(taskRepository.findAll()).thenReturn(List.of(t1, t2, t3));

        // Act
        SearchResults results = searchService.search(query, List.of("IN_PROGRESS"), List.of("HIGH"), null, null, null);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.getTasks().size());
        assertEquals("Create User Entity", results.getTasks().get(0).getTitle());
    }
}
