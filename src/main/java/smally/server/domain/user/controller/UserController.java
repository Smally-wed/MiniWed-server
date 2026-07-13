package smally.server.domain.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import smally.server.core.dto.ApiResponse;
import smally.server.domain.user.dto.UserResponse;
import smally.server.domain.user.service.UserService;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/v1/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
            @AuthenticationPrincipal String userId
    ){
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.OK, userService.getUser(Long.valueOf(userId))));
    }
}
