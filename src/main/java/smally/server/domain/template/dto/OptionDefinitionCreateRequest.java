package smally.server.domain.template.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import smally.server.domain.template.entity.OptionDefinition;

public record OptionDefinitionCreateRequest(
        @NotBlank(message = "옵션 키는 필수입니다.")
        String key,
        @NotBlank(message = "라벨은 필수입니다.")
        String label,
        @NotBlank(message = "컨트롤 타입은 필수입니다.")
        String controlType,
        @NotBlank(message = "스코프는 필수입니다.")
        String scope,
        List<Object> allowedValues,
        Object defaultValue
) {
    public OptionDefinition toEntity() {
        return OptionDefinition.builder()
                .key(key)
                .label(label)
                .controlType(controlType)
                .scope(scope)
                .allowedValues(allowedValues)
                .defaultValue(defaultValue)
                .build();
    }
}
