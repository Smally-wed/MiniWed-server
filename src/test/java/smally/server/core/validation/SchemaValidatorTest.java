package smally.server.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SchemaValidatorTest {

    private final SchemaValidator validator = new SchemaValidator(new ObjectMapper());

    @Test
    void validateSchema_유효한_스키마면_빈_목록() {
        assertThat(validator.validateSchema(Map.of("type", "object"))).isEmpty();
    }

    @Test
    void validateSchema_type이_문자열이_아니면_위반() {
        assertThat(validator.validateSchema(Map.of("type", 123))).isNotEmpty();
    }

    @Test
    void validateData_enum에_없는_값이면_위반() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("fontSize", Map.of("enum", List.of("small", "large"))));
        assertThat(validator.validateData(schema, Map.of("fontSize", "huge"))).isNotEmpty();
    }

    @Test
    void validateData_enum에_있는_값이면_통과() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("fontSize", Map.of("enum", List.of("small", "large"))));
        assertThat(validator.validateData(schema, Map.of("fontSize", "small"))).isEmpty();
    }
}
