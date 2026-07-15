package smally.server.core.validation;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * JSON Schema 공용 검증기.
 * - validateSchema: 스키마 자체가 유효한 JSON Schema(draft 2020-12)인지 메타검증.
 * - validateData:   값이 주어진 스키마를 지키는지 데이터검증.
 * 둘 다 위반 메시지 목록을 반환한다(빈 목록 = 통과). ErrorCode 매핑은 호출자 책임.
 */
@Component
@RequiredArgsConstructor
public class SchemaValidator {

    private static final SchemaRegistry REGISTRY =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private static final SchemaLocation META_2020_12 =
            SchemaLocation.of("https://json-schema.org/draft/2020-12/schema");

    private final ObjectMapper objectMapper;

    public List<String> validateSchema(Map<String, Object> schema) {
        Schema metaSchema = REGISTRY.getSchema(META_2020_12);
        List<Error> errors = metaSchema.validate(
                objectMapper.writeValueAsString(schema), InputFormat.JSON);
        return errors.stream().map(Error::getMessage).toList();
    }

    public List<String> validateData(Map<String, Object> schema, Map<String, Object> data) {
        Schema compiled = REGISTRY.getSchema(
                objectMapper.writeValueAsString(schema), InputFormat.JSON);
        List<Error> errors = compiled.validate(
                objectMapper.writeValueAsString(data), InputFormat.JSON);
        return errors.stream().map(Error::getMessage).toList();
    }
}
