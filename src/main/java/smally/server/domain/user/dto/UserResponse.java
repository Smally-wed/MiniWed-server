package smally.server.domain.user.dto;

import smally.server.domain.user.entity.User;
import smally.server.domain.user.enums.UserRole;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        UserRole userRole
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getUserRole()
        );
    }
}
