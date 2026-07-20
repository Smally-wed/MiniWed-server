package smally.server.domain.image.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.image.entity.ImageUpload;

public interface ImageUploadRepository extends JpaRepository<ImageUpload, Long> {
}
