package smally.server.domain.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.template.entity.dto.Template;

public interface TemplateRepository extends JpaRepository<Template, Long> {
}
