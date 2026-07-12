package smally.server.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.enums.UserRole;

public record UserCreateRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.") String password,
        @NotBlank String nickname
) {

    public User toUser(String encodedPassword) {
        return User.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .userRole(UserRole.USER)
                .build();
    }
}
