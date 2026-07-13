package smally.server.domain.user.service;

import smally.server.domain.user.dto.UserCreateRequest;
import smally.server.domain.user.dto.UserResponse;

public interface InternalUserService {

    UserResponse createUser(UserCreateRequest request);

}
