package com.dpi.platform.repository;

import com.dpi.platform.model.Requirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface RequirementRepository extends JpaRepository<Requirement, UUID> {
    List<Requirement> findByFeature_Id(UUID featureId);
    List<Requirement> findByFeature_IdIn(java.util.Collection<UUID> featureIds);
    List<Requirement> findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String title, String description);
}
