package smally.server.domain.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.template.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByTitle(String title);
}
