package smally.server.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.exception.exceptions.UserException;
import smally.server.core.exception.ErrorCode;
import smally.server.domain.user.dto.UserCreateRequest;
import smally.server.domain.user.dto.UserResponse;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService, InternalUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserException(ErrorCode.DUPLICATE_EMAIL);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = userRepository.save(request.toUser(encodedPassword));

        return UserResponse.from(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserIfExist(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
    }
}
