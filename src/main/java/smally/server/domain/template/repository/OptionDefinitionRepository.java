package smally.server.domain.template.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.template.entity.OptionDefinition;

public interface OptionDefinitionRepository extends JpaRepository<OptionDefinition, Long> {
    boolean existsByKey(String key);
    Optional<OptionDefinition> findByKey(String key);
}
