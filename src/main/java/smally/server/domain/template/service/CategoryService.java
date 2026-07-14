package smally.server.domain.template.service;

import smally.server.domain.template.dto.CategoryCreateRequest;

import java.util.List;

public interface CategoryService {
    void createCategory(CategoryCreateRequest categoryCreateRequest);

    List<String> getAllCategories();

    void validateExists(String title);
}
