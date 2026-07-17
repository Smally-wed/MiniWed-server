package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.template.entity.Template;

class TemplateCreateRequestTest {

    @Test
    void toTemplate_sections와_theme를_매핑한다() {
        List<Map<String, Object>> sections = List.of(
                Map.of("componentUId", "GalleryGrid",
                        "options", Map.of("titleSize", "L"),
                        "editable", List.of("titleSize")));
        Map<String, Object> theme = Map.of("fontSize", "large");

        TemplateCreateRequest request = new TemplateCreateRequest(
                "클래식 화이트", "https://cdn/thumb.png", "클래식", sections, theme);

        Template template = request.toTemplate();

        assertThat(template.getName()).isEqualTo("클래식 화이트");
        assertThat(template.getThumbnail()).isEqualTo("https://cdn/thumb.png");
        assertThat(template.getCategory()).isEqualTo("클래식");
        assertThat(template.getSections()).isEqualTo(sections);
        assertThat(template.getTheme()).isEqualTo(theme);
    }
}
