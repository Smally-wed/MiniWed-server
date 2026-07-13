package smally.server.domain.user.service;

import smally.server.domain.user.dto.UserResponse;

public interface UserService {

    UserResponse getUser(Long userId);
}
