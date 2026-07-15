package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.template.entity.Template;

class TemplateCreateRequestTest {

    @Test
    void toTemplate_optionsSchema를_매핑한다() {
        Map<String, Object> options = Map.of(
                "design", Map.of("fontSize", Map.of("enum", List.of("small", "large"))));
        TemplateCreateRequest request = new TemplateCreateRequest(
                "클래식 화이트", "https://cdn/thumb.png", "클래식",
                Map.of("type", "object"), options, Map.of("color", List.of("white")));

        Template template = request.toTemplate();

        assertThat(template.getOptionsSchema()).isEqualTo(options);
    }
}
