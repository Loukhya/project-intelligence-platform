package com.dpi.platform.repository;

import com.dpi.platform.model.JavaClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface JavaClassRepository extends JpaRepository<JavaClass, UUID> {
    List<JavaClass> findByProjectId(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
