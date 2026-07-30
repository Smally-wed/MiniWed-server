package smally.server.domain.user.service;

import smally.server.domain.user.dto.UserCreateRequest;
import smally.server.domain.user.dto.UserResponse;
import smally.server.domain.user.entity.User;

public interface InternalUserService {

    UserResponse createUser(UserCreateRequest request);

    User getUserIfExist(Long userId);

}
