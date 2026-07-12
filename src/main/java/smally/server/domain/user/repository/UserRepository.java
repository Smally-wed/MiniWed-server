package smally.server.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.user.entity.dto.User;

public interface UserRepository extends JpaRepository<User,Long> {
}
