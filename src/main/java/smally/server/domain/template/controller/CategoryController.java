package smally.server.domain.template.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smally.server.core.dto.ApiResponse;
import smally.server.domain.template.dto.CategoryCreateRequest;
import smally.server.domain.template.service.CategoryService;

import java.util.List;

@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping("/v1")
    public ResponseEntity<ApiResponse<Void>> createCategory(
            @RequestBody @Valid CategoryCreateRequest categoryCreateRequest
    ) {
        categoryService.createCategory(categoryCreateRequest);
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED, null));
    }

    @GetMapping("/v1")
    public ResponseEntity<ApiResponse<List<String>>> getAllCategories() {
        return ResponseEntity.ok(
                ApiResponse.of(HttpStatus.OK, categoryService.getAllCategories())
        );
    }

}
