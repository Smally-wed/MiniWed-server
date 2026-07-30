package smally.server.domain.invitation.dto;

import java.time.Instant;
import java.util.Map;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.invitation.enums.InvitationStatus;

public record InvitationResponse(
        String invitationUid,
        String templateUid,
        InvitationStatus status,
        String slug,
        Map<String, Object> sectionValues,
        Map<String, Object> selectedOptions,
        Instant publishedAt,
        Instant updatedAt
) {
    /** sectionValues에는 presigned URL로 치환된 사본을 넣는다. */
    public static InvitationResponse of(Invitation invitation, Map<String, Object> sectionValues) {
        return new InvitationResponse(
                invitation.getInvitationUid() == null ? null : invitation.getInvitationUid().toString(),
                invitation.getTemplate().getTemplateUid().toString(),
                invitation.getStatus(),
                invitation.getSlug(),
                sectionValues,
                invitation.getSelectedOptions(),
                invitation.getPublishedAt(),
                invitation.getUpdatedAt());
    }
}
