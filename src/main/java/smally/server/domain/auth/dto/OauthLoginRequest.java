package smally.server.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OauthLoginRequest(
        @NotBlank String accessToken
) {
}
