package com.dpi.platform.repository;

import com.dpi.platform.model.AgentMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgentMessageRepository extends JpaRepository<AgentMessage, UUID> {
    List<AgentMessage> findByTask_IdOrderByCreatedAtAsc(UUID taskId);
    void deleteByTask_Id(UUID taskId);
}
