package smally.server.domain.component.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.component.entity.ComponentType;

import java.util.Optional;

public interface ComponentTypeRepository extends JpaRepository<ComponentType, Long> {
    boolean existsByName(String name);
    Optional<ComponentType> findByName(String name);
}
