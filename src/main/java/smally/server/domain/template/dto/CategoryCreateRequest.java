package smally.server.domain.template.dto;

import jakarta.validation.constraints.NotBlank;
import smally.server.domain.template.entity.Category;

public record CategoryCreateRequest(
        @NotBlank(message = "카테고리 이름은 필수입니다.")
        String title
) {
    public Category to(){
        return Category.builder()
                .title(title)
                .build();
    }
}
