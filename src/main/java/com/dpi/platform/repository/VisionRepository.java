package com.dpi.platform.repository;

import com.dpi.platform.model.Vision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface VisionRepository extends JpaRepository<Vision, UUID> {
}
