package com.dpi.platform.repository;

import com.dpi.platform.model.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface FeatureRepository extends JpaRepository<Feature, UUID> {
    List<Feature> findByEpic_Id(UUID epicId);
    List<Feature> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String name, String description);
}
