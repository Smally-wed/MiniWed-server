package smally.server.domain.user.service;

import smally.server.domain.user.dto.UserCreateRequest;
import smally.server.domain.user.dto.UserResponse;

public interface UserService {

    UserResponse createUser(UserCreateRequest request);

    UserResponse getUser(String email);
}
