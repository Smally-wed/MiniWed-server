package smally.server.domain.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smally.server.core.dto.ApiResponse;
import smally.server.domain.auth.dto.LoginRequest;
import smally.server.domain.auth.dto.SignupResponse;
import smally.server.domain.auth.dto.TokenReissueRequest;
import smally.server.domain.auth.dto.TokenResponse;
import smally.server.domain.auth.service.AuthService;
import smally.server.domain.user.dto.UserCreateRequest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/v1/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED, authService.signup(request)));
    }

    @PostMapping("/v1/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.OK, authService.login(request)));
    }

    @PostMapping("/v1/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody TokenReissueRequest request) {
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.OK, authService.reissue(request.refreshToken())));
    }

    @PostMapping("/v1/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal String userId) {
        authService.logout(Long.valueOf(userId));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.noContent());
    }
}
