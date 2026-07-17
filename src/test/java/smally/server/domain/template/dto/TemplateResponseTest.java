package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.template.entity.Template;

class TemplateResponseTest {

    @Test
    void from_필드를_노출하고_templateUid가_null이면_null을_담는다() {
        List<Map<String, Object>> sections = List.of(Map.of("componentUId", "GalleryGrid"));
        Map<String, Object> theme = Map.of("fontSize", "large");
        Template template = Template.builder()
                .name("클래식 화이트").thumbnail("t.png").category("클래식")
                .sections(sections).theme(theme)
                .build(); // templateUid는 @PrePersist 전이라 null

        TemplateResponse response = TemplateResponse.from(template);

        assertThat(response.templateUid()).isNull();
        assertThat(response.name()).isEqualTo("클래식 화이트");
        assertThat(response.thumbnail()).isEqualTo("t.png");
        assertThat(response.category()).isEqualTo("클래식");
        assertThat(response.sections()).isEqualTo(sections);
        assertThat(response.theme()).isEqualTo(theme);
    }
}
