package com.dpi.platform.controller;

import com.dpi.platform.dto.SearchResults;
import com.dpi.platform.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    @Autowired
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResults> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) List<UUID> technologyId,
            @RequestParam(required = false) String completionState) {

        SearchResults results = searchService.search(query, status, priority, category, technologyId, completionState);
        return ResponseEntity.ok(results);
    }
}
