package smally.server.domain.component.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.component.entity.Component;

class ComponentCreateRequestTest {

    @Test
    void toComponent_componentType을_제외한_필드를_매핑한다() {
        Map<String, Object> dataSchema = Map.of("type", "object",
                "properties", Map.of("photos", Map.of("type", "array")));
        Map<String, Object> optionSchema = Map.of("type", "object");

        ComponentCreateRequest request = new ComponentCreateRequest(
                "클래식 갤러리", "gallery", "GalleryGrid", dataSchema, optionSchema);

        Component component = request.toComponent();

        assertThat(component.getName()).isEqualTo("클래식 갤러리");
        assertThat(component.getComponentUId()).isEqualTo("GalleryGrid");
        assertThat(component.getDataSchema()).isEqualTo(dataSchema);
        assertThat(component.getOptionSchema()).isEqualTo(optionSchema);
        // componentTypeName은 여기서 매핑하지 않는다. 이름을 실제 ComponentType 엔티티로
        // 해석하려면 조회가 필요하므로 ComponentServiceImpl이 setComponentType으로 주입한다.
        assertThat(component.getComponentType()).isNull();
    }
}
