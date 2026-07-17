package smally.server.domain.component.dto;

import java.util.Map;
import smally.server.domain.component.entity.Component;

public record ComponentResponse(
        Long id,
        String name,
        String componentType,
        String frontendBinding,
        Map<String, Object> optionSchema
) {
    public static ComponentResponse from(Component component) {
        return new ComponentResponse(
                component.getId(),
                component.getName(),
                component.getComponentType().getName(),
                component.getComponentUId(),
                component.getOptionSchema()
        );
    }
}
