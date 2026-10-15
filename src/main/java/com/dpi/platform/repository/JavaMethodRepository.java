package com.dpi.platform.repository;

import com.dpi.platform.model.JavaMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface JavaMethodRepository extends JpaRepository<JavaMethod, UUID> {
    List<JavaMethod> findByJavaClassId(UUID classId);
    List<JavaMethod> findByJavaClassProjectId(UUID projectId);
}
