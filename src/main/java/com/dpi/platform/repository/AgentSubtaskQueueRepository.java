package com.dpi.platform.repository;

import com.dpi.platform.model.AgentSubtaskQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgentSubtaskQueueRepository extends JpaRepository<AgentSubtaskQueue, UUID> {
    List<AgentSubtaskQueue> findByTaskId(UUID taskId);
    List<AgentSubtaskQueue> findByTaskIdAndSubtaskType(UUID taskId, String subtaskType);
}
