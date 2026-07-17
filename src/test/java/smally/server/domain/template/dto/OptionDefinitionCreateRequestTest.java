package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import smally.server.domain.template.entity.OptionDefinition;

class OptionDefinitionCreateRequestTest {

    @Test
    void toEntity_모든_필드를_매핑한다() {
        List<Object> allowed = List.of("small", "normal", "large");
        OptionDefinitionCreateRequest request = new OptionDefinitionCreateRequest(
                "fontSize", "폰트 크기", "select", "global", allowed, "normal");

        OptionDefinition entity = request.toEntity();

        assertThat(entity.getKey()).isEqualTo("fontSize");
        assertThat(entity.getLabel()).isEqualTo("폰트 크기");
        assertThat(entity.getControlType()).isEqualTo("select");
        assertThat(entity.getScope()).isEqualTo("global");
        assertThat(entity.getAllowedValues()).isEqualTo(allowed);
        assertThat(entity.getDefaultValue()).isEqualTo("normal");
    }
}
