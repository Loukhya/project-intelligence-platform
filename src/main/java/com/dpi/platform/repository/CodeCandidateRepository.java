package com.dpi.platform.repository;

import com.dpi.platform.model.CodeCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CodeCandidateRepository extends JpaRepository<CodeCandidate, UUID> {
    List<CodeCandidate> findByTask_Id(UUID taskId);
    List<CodeCandidate> findByTask_IdAndStatus(UUID taskId, String status);
}
