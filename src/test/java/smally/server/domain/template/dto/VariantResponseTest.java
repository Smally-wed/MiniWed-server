package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.template.entity.Template;

class VariantResponseTest {

    @Test
    void from_variant와_optionsSchema를_함께_노출한다() {
        Map<String, Object> variants = Map.of("color", List.of("white", "beige"));
        Map<String, Object> options = Map.of(
                "design", Map.of("fontSize", Map.of("enum", List.of("small", "large"))));
        Template template = Template.builder()
                .name("t").category("c")
                .sectionSchema(Map.of("type", "object"))
                .variants(variants)
                .optionsSchema(options)
                .build();

        VariantResponse response = VariantResponse.from(template);

        assertThat(response.variant()).isEqualTo(variants);
        assertThat(response.optionsSchema()).isEqualTo(options);
    }
}
