package smally.server.domain.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.template.entity.Template;

import java.util.Optional;
import java.util.UUID;

public interface TemplateRepository extends JpaRepository<Template, Long> {
    Optional<Template> findTemplateByTemplateUid(UUID templateUid);
}
