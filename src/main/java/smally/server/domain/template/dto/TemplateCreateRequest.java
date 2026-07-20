package smally.server.domain.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import smally.server.domain.template.entity.Template;

public record TemplateCreateRequest(
        @NotBlank(message = "템플릿 이름은 필수입니다.")
        String name,
        @NotBlank(message = "카테고리는 필수입니다.")
        String category,
        @NotNull(message = "섹션 구성은 필수입니다.")
        List<Map<String, Object>> sections,
        Map<String, Object> theme
) {
    public Template toTemplate(String thumbnailObjectKey) {
        return Template.builder()
                .name(name)
                .thumbnail(thumbnailObjectKey)
                .category(category)
                .sections(sections)
                .theme(theme)
                .build();
    }
}
