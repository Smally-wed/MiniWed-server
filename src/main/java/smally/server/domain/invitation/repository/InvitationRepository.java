package smally.server.domain.invitation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.invitation.enums.InvitationStatus;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByInvitationUid(UUID invitationUid);

    Optional<Invitation> findBySlugAndStatus(String slug, InvitationStatus status);

    List<Invitation> findAllByUserIdOrderByUpdatedAtDesc(Long userId);

    boolean existsBySlug(String slug);
}
