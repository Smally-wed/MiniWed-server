package smally.server.domain.component.dto;

import jakarta.validation.constraints.NotBlank;
import smally.server.domain.component.entity.ComponentType;

public record ComponentTypeCreateRequest(
        @NotBlank(message = "컴포넌트 종류 이름은 필수입니다.")
        String name
) {
    public ComponentType to() {
        return ComponentType.builder().name(name).build();
    }
}
