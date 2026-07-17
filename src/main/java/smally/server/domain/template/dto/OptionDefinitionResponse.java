package smally.server.domain.template.dto;

import java.util.List;
import smally.server.domain.template.entity.OptionDefinition;

public record OptionDefinitionResponse(
        String key,
        String label,
        String controlType,
        String scope,
        List<Object> allowedValues,
        Object defaultValue
) {
    public static OptionDefinitionResponse from(OptionDefinition entity) {
        return new OptionDefinitionResponse(
                entity.getKey(),
                entity.getLabel(),
                entity.getControlType(),
                entity.getScope(),
                entity.getAllowedValues(),
                entity.getDefaultValue()
        );
    }
}
