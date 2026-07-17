package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import smally.server.domain.template.entity.OptionDefinition;

class OptionDefinitionResponseTest {

    @Test
    void from_모든_카탈로그_필드를_노출한다() {
        List<Object> allowed = List.of("small", "normal", "large");
        OptionDefinition entity = OptionDefinition.builder()
                .key("fontSize").label("폰트 크기").controlType("select").scope("global")
                .allowedValues(allowed).defaultValue("normal")
                .build();

        OptionDefinitionResponse response = OptionDefinitionResponse.from(entity);

        assertThat(response.key()).isEqualTo("fontSize");
        assertThat(response.label()).isEqualTo("폰트 크기");
        assertThat(response.controlType()).isEqualTo("select");
        assertThat(response.scope()).isEqualTo("global");
        assertThat(response.allowedValues()).isEqualTo(allowed);
        assertThat(response.defaultValue()).isEqualTo("normal");
    }
}
