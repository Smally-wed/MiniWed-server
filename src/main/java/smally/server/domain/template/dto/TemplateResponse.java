package smally.server.domain.template.dto;

import java.util.List;
import java.util.Map;
import smally.server.domain.template.entity.Template;

public record TemplateResponse(
        String templateUid,
        String name,
        String thumbnail,
        String category,
        List<Map<String, Object>> sections,
        Map<String, Object> theme
) {
    public static TemplateResponse from(Template template) {
        return new TemplateResponse(
                template.getTemplateUid() == null ? null : template.getTemplateUid().toString(),
                template.getName(),
                template.getThumbnail(),
                template.getCategory(),
                template.getSections(),
                template.getTheme()
        );
    }

    public TemplateResponse withThumbnail(String thumbnail) {
        return new TemplateResponse(templateUid, name, thumbnail, category, sections, theme);
    }
}
