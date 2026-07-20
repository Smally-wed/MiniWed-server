package smally.server.domain.template.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.exceptions.CategoryException;
import smally.server.domain.template.dto.CategoryCreateRequest;
import smally.server.domain.template.entity.Category;
import smally.server.domain.template.repository.CategoryRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static smally.server.core.exception.ErrorCode.CATEGORY_NOT_FOUND;
import static smally.server.core.exception.ErrorCode.DUPLICATE_CATEGORY;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService{
    private final static String CATEGORY_CACHE_KEY = "CAT";
    private final static Duration CATEGORY_CACHE_TTL = Duration.ofDays(30);

    private final CategoryRepository categoryRepository;
    private final RedisCacheService redisCacheService;

    @Override
    @Transactional
    public void createCategory(CategoryCreateRequest categoryCreateRequest) {
        if (categoryRepository.existsByTitle(categoryCreateRequest.title())) {
            throw new CategoryException(DUPLICATE_CATEGORY);
        }
        categoryRepository.save(categoryCreateRequest.to());
        redisCacheService.deleteCacheData(CATEGORY_CACHE_KEY);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAllCategories() {
        @SuppressWarnings("unchecked")
        List<String> cached = (List<String>) redisCacheService.getCacheData(CATEGORY_CACHE_KEY, List.class);

        if(cached != null){
            return cached;
        }

        log.warn("[Cache-miss] Category List cache miss key : {}", CATEGORY_CACHE_KEY);

        List<String> categoryList = categoryRepository.findAll().stream()
                .map(Category::getTitle)
                .toList();

        redisCacheService.setCacheData(CATEGORY_CACHE_KEY,new ArrayList<>(categoryList),CATEGORY_CACHE_TTL);

        return categoryList;
    }

    @Override
    @Transactional(readOnly = true)
    public void validateExists(String title) {
        if (!categoryRepository.existsByTitle(title)) {
            throw new CategoryException(CATEGORY_NOT_FOUND);
        }
    }

}
