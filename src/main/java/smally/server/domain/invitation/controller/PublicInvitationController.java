package smally.server.domain.invitation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smally.server.core.dto.ApiResponse;
import smally.server.domain.invitation.dto.PublicInvitationResponse;
import smally.server.domain.invitation.service.PublicInvitationService;

/** 하객이 로그인 없이 여는 경로. 인증 경계가 달라 컨트롤러를 분리한다. */
@RestController
@RequestMapping("/api/public/invitation")
@RequiredArgsConstructor
public class PublicInvitationController {

    private final PublicInvitationService publicInvitationService;

    @GetMapping("/v1/{slug}")
    public ResponseEntity<ApiResponse<PublicInvitationResponse>> getBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(publicInvitationService.getBySlug(slug)));
    }
}
