package com.dpi.platform.repository;

import com.dpi.platform.model.CodeMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CodeMappingRepository extends JpaRepository<CodeMapping, UUID> {
    List<CodeMapping> findByRequirementId(UUID requirementId);
    List<CodeMapping> findByTaskId(UUID taskId);
    List<CodeMapping> findByJavaClassProjectId(UUID projectId);
}
