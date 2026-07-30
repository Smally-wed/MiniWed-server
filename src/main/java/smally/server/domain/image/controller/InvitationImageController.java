package smally.server.domain.image.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import smally.server.core.dto.ApiResponse;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.UserException;
import smally.server.core.security.CurrentUser;
import smally.server.domain.image.dto.ImageUploadResponse;
import smally.server.domain.image.service.InvitationImageService;
import smally.server.domain.user.repository.UserRepository;

@RestController
@RequestMapping("/api/invitation")
@RequiredArgsConstructor
public class InvitationImageController {

    private final InvitationImageService invitationImageService;
    private final UserRepository userRepository;

    @PostMapping(value = "/v1/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageUploadResponse>> upload(
            @AuthenticationPrincipal String principal,
            @RequestPart("image") MultipartFile image) {
        Long userId = CurrentUser.requireUserId(principal);
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED,
                invitationImageService.upload(image, userRepository.findById(userId)
                        .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND)))));
    }
}
