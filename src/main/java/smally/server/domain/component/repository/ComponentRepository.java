package smally.server.domain.component.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.component.entity.Component;

import java.util.Optional;

public interface ComponentRepository extends JpaRepository<Component, Long> {
    Optional<Component> findbyComponentUid(String componentUid);
}
