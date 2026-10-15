package com.dpi.platform.repository;

import com.dpi.platform.model.Epic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface EpicRepository extends JpaRepository<Epic, UUID> {
    List<Epic> findByProject_Id(UUID projectId);
    List<Epic> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String name, String description);
}
