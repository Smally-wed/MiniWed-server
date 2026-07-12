package smally.server.domain.auth.dto;

import smally.server.domain.user.dto.UserResponse;
import smally.server.domain.user.enums.UserRole;

public record SignupResponse(
        Long userId,
        String email,
        UserRole role
) {

    public static SignupResponse from(UserResponse user) {
        return new SignupResponse(user.id(), user.email(), user.userRole());
    }
}
