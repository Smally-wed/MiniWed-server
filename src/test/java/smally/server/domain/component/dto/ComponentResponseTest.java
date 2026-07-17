package smally.server.domain.component.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.component.entity.Component;
import smally.server.domain.component.entity.ComponentType;

class ComponentResponseTest {

    @Test
    void from_editor_필드를_노출하고_dataSchema는_담지_않는다() {
        Map<String, Object> optionSchema = Map.of("type", "object");
        Component component = Component.builder()
                .name("클래식 갤러리")
                .componentUId("GalleryGrid")
                .dataSchema(Map.of("type", "object")) // 서버 검증 전용
                .optionSchema(optionSchema)
                .build();
        component.setComponentType(ComponentType.builder().name("gallery").build());

        ComponentResponse response = ComponentResponse.from(component);

        assertThat(response.name()).isEqualTo("클래식 갤러리");
        assertThat(response.componentType()).isEqualTo("gallery");
        assertThat(response.frontendBinding()).isEqualTo("GalleryGrid");
        assertThat(response.optionSchema()).isEqualTo(optionSchema);
        // dataSchema 접근자는 존재하지 않는다(레코드 컴포넌트 5개): 컴파일 계약으로 보장
    }
}
