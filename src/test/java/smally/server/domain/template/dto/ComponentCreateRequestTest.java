package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.component.dto.ComponentCreateRequest;
import smally.server.domain.component.entity.Component;

class ComponentCreateRequestTest {

    @Test
    void toComponent_모든_필드를_매핑한다() {
        Map<String, Object> dataSchema = Map.of("type", "object",
                "properties", Map.of("photos", Map.of("type", "array")));
        Map<String, Object> optionSchema = Map.of("type", "object");

        ComponentCreateRequest request = new ComponentCreateRequest(
                "클래식 갤러리", "gallery", "GalleryGrid", dataSchema, optionSchema);

        Component component = request.toComponent();

        assertThat(component.getName()).isEqualTo("클래식 갤러리");
        assertThat(component.getComponentType()).isEqualTo("gallery");
        assertThat(component.getComponentUId()).isEqualTo("GalleryGrid");
        assertThat(component.getDataSchema()).isEqualTo(dataSchema);
        assertThat(component.getOptionSchema()).isEqualTo(optionSchema);
    }
}
