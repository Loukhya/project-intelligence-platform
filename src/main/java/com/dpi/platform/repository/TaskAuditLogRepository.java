package com.dpi.platform.repository;

import com.dpi.platform.model.TaskAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskAuditLogRepository extends JpaRepository<TaskAuditLog, UUID> {
    List<TaskAuditLog> findByTaskId(UUID taskId);
}
