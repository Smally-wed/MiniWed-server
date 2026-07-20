package smally.server.domain.invitation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.invitation.entity.Invitation;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
}
