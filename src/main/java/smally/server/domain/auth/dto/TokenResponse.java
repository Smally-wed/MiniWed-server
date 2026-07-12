package smally.server.domain.auth.dto;


public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {

    private static final String BEARER = "Bearer";

    public static TokenResponse of(String accessToken, String refreshToken) {
        return new TokenResponse(accessToken, refreshToken, BEARER);
    }
}
