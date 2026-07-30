package smally.server.domain.invitation.service;

import java.util.List;
import smally.server.domain.invitation.dto.InvitationCreateRequest;
import smally.server.domain.invitation.dto.InvitationResponse;
import smally.server.domain.invitation.dto.InvitationSummaryResponse;
import smally.server.domain.invitation.dto.InvitationUpdateRequest;

public interface InvitationService {

    InvitationResponse create(Long userId, InvitationCreateRequest request);

    InvitationResponse get(Long userId, String invitationUid);

    List<InvitationSummaryResponse> getMine(Long userId);

    InvitationResponse update(Long userId, String invitationUid, InvitationUpdateRequest request);

    InvitationResponse publish(Long userId, String invitationUid);

    InvitationResponse unpublish(Long userId, String invitationUid);

    void delete(Long userId, String invitationUid);
}
