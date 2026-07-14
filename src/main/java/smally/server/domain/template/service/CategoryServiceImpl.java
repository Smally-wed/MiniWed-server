package smally.server.domain.template.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.exception.exceptions.CategoryException;
import smally.server.domain.template.dto.CategoryCreateRequest;
import smally.server.domain.template.entity.Category;
import smally.server.domain.template.repository.CategoryRepository;

import java.util.List;

import static smally.server.core.exception.ErrorCode.CATEGORY_NOT_FOUND;
import static smally.server.core.exception.ErrorCode.DUPLICATE_CATEGORY;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService{

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void createCategory(CategoryCreateRequest categoryCreateRequest) {
        if (categoryRepository.existsByTitle(categoryCreateRequest.title())) {
            throw new CategoryException(DUPLICATE_CATEGORY);
        }
        categoryRepository.save(categoryCreateRequest.to());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(Category::getTitle)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void validateExists(String title) {
        if (!categoryRepository.existsByTitle(title)) {
            throw new CategoryException(CATEGORY_NOT_FOUND);
        }
    }

}
