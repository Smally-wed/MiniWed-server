package smally.server.domain.invitation.dto;

import java.time.Instant;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.invitation.enums.InvitationStatus;

/** 목록에서는 jsonb 본문을 내려보내지 않는다. */
public record InvitationSummaryResponse(
        String invitationUid,
        String templateUid,
        InvitationStatus status,
        String slug,
        Instant updatedAt
) {
    public static InvitationSummaryResponse from(Invitation invitation) {
        return new InvitationSummaryResponse(
                invitation.getInvitationUid().toString(),
                invitation.getTemplate().getTemplateUid().toString(),
                invitation.getStatus(),
                invitation.getSlug(),
                invitation.getUpdatedAt());
    }
}
