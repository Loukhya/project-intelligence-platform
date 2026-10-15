package com.dpi.platform.repository;

import com.dpi.platform.model.MethodCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MethodCallRepository extends JpaRepository<MethodCall, UUID> {
    List<MethodCall> findByCallerMethodId(UUID callerMethodId);
    List<MethodCall> findByCalleeMethodId(UUID calleeMethodId);

    @org.springframework.data.jpa.repository.Query("SELECT mc FROM MethodCall mc WHERE mc.callerMethod.javaClass.project.id = :projectId")
    List<MethodCall> findByProjectId(@org.springframework.data.repository.query.Param("projectId") UUID projectId);
}
