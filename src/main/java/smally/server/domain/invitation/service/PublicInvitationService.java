package smally.server.domain.invitation.service;

import smally.server.domain.invitation.dto.PublicInvitationResponse;

public interface PublicInvitationService {

    /** 발행된 청첩장만 조회된다. DRAFT는 존재 자체가 드러나지 않는다. */
    PublicInvitationResponse getBySlug(String slug);
}
