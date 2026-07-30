package smally.server.domain.invitation.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import smally.server.core.dto.ApiResponse;
import smally.server.core.security.CurrentUser;
import smally.server.domain.invitation.dto.*;
import smally.server.domain.invitation.service.InvitationService;

@RestController
@RequestMapping("/api/invitation")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping("/v1")
    public ResponseEntity<ApiResponse<InvitationResponse>> create(
            @AuthenticationPrincipal String principal,
            @RequestBody @Valid InvitationCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED,
                invitationService.create(CurrentUser.requireUserId(principal), request)));
    }

    @GetMapping("/v1/{invitationUid}")
    public ResponseEntity<ApiResponse<InvitationResponse>> get(
            @AuthenticationPrincipal String principal,
            @PathVariable String invitationUid) {
        return ResponseEntity.ok(ApiResponse.ok(
                invitationService.get(CurrentUser.requireUserId(principal), invitationUid)));
    }

    @GetMapping("/v1")
    public ResponseEntity<ApiResponse<List<InvitationSummaryResponse>>> getMine(
            @AuthenticationPrincipal String principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                invitationService.getMine(CurrentUser.requireUserId(principal))));
    }

    @PutMapping("/v1/{invitationUid}")
    public ResponseEntity<ApiResponse<InvitationResponse>> update(
            @AuthenticationPrincipal String principal,
            @PathVariable String invitationUid,
            @RequestBody @Valid InvitationUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                invitationService.update(CurrentUser.requireUserId(principal), invitationUid, request)));
    }

    @PostMapping("/v1/{invitationUid}/publish")
    public ResponseEntity<ApiResponse<InvitationResponse>> publish(
            @AuthenticationPrincipal String principal,
            @PathVariable String invitationUid) {
        return ResponseEntity.ok(ApiResponse.ok(
                invitationService.publish(CurrentUser.requireUserId(principal), invitationUid)));
    }

    @PostMapping("/v1/{invitationUid}/unpublish")
    public ResponseEntity<ApiResponse<InvitationResponse>> unpublish(
            @AuthenticationPrincipal String principal,
            @PathVariable String invitationUid) {
        return ResponseEntity.ok(ApiResponse.ok(
                invitationService.unpublish(CurrentUser.requireUserId(principal), invitationUid)));
    }

    @DeleteMapping("/v1/{invitationUid}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal String principal,
            @PathVariable String invitationUid) {
        invitationService.delete(CurrentUser.requireUserId(principal), invitationUid);
        return ResponseEntity.ok(ApiResponse.noContent());
    }
}
