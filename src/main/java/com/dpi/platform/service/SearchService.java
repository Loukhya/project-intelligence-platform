package com.dpi.platform.service;

import com.dpi.platform.dto.SearchResults;
import java.util.List;
import java.util.UUID;

public interface SearchService {
    SearchResults search(String query,
                         List<String> statuses,
                         List<String> priorities,
                         List<String> categories,
                         List<UUID> technologyIds,
                         String completionState);
}
