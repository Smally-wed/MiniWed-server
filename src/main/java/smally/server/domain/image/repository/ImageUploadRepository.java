package smally.server.domain.image.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.image.entity.ImageUpload;
import smally.server.domain.invitation.entity.Invitation;

public interface ImageUploadRepository extends JpaRepository<ImageUpload, Long> {

    List<ImageUpload> findAllByObjectKeyIn(Collection<String> objectKeys);

    List<ImageUpload> findAllByInvitation(Invitation invitation);
}
