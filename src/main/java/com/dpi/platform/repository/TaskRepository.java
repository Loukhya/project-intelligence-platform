package com.dpi.platform.repository;

import com.dpi.platform.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByFeature_Id(UUID featureId);
    List<Task> findByFeature_IdIn(java.util.Collection<UUID> featureIds);
    List<Task> findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String title, String description);
}
