package smally.server.domain.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.template.entity.dto.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
