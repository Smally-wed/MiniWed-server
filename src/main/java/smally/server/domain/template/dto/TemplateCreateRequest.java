package smally.server.domain.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import smally.server.domain.template.entity.Template;

import java.util.Map;

public record TemplateCreateRequest(
        @NotBlank(message = "템플릿 이름은 필수입니다.")
        String name,
        String thumbnail,
        @NotBlank(message = "카테고리는 필수입니다.")
        String category,
        @NotNull(message = "섹션 스키마는 필수입니다.")
        Map<String, Object> sectionSchema,
        Map<String, Object> optionsSchema,
        Map<String, Object> variants
) {
    public Template toTemplate(){
        return Template.builder()
                .name(name)
                .thumbnail(thumbnail)
                .category(category)
                .sectionSchema(sectionSchema)
                .optionsSchema(optionsSchema)
                .variants(variants)
                .build();
    }
}
