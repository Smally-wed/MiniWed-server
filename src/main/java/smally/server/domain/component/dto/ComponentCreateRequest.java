package smally.server.domain.component.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import smally.server.domain.component.entity.Component;

public record ComponentCreateRequest(
        @NotBlank(message = "컴포넌트 이름은 필수입니다.")
        String name,
        @NotBlank(message = "컴포넌트 종류는 필수입니다.")
        String componentTypeName,
        @NotBlank(message = "프론트 연결(componentUId)은 필수입니다.")
        String componentUId,
        @NotNull(message = "데이터 스키마는 필수입니다.")
        Map<String, Object> dataSchema,
        Map<String, Object> optionSchema
) {
    public Component toComponent() {
        return Component.builder()
                .name(name)
                .componentUId(componentUId)
                .dataSchema(dataSchema)
                .optionSchema(optionSchema)
                .build();
    }
}
