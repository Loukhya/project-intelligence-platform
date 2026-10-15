package com.dpi.platform.repository;

import com.dpi.platform.model.TechnologyDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface TechnologyDecisionRepository extends JpaRepository<TechnologyDecision, UUID> {
    List<TechnologyDecision> findByRequirementId(UUID requirementId);
}
