package smally.server.domain.template.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.TemplateException;
import smally.server.domain.component.dto.ComponentResponse;
import smally.server.domain.component.service.ComponentService;

class TemplateRecipeValidationTest {

    private final ComponentService componentService = mock(ComponentService.class);
    private final TemplateServiceImpl service = new TemplateServiceImpl(
            null, null, componentService, null, null, null);

    private void validateRecipe(List<Map<String, Object>> sections) {
        ReflectionTestUtils.invokeMethod(service, "validateRecipe", sections);
    }

    @Test
    void sectionId와_componentUId가_있으면_통과한다() {
        when(componentService.getComponent(anyString()))
                .thenReturn(new ComponentResponse(1L, "n", "t", "CoverBasic", Map.of()));

        assertThatCode(() -> validateRecipe(List.of(
                Map.of("sectionId", "cover", "componentUId", "CoverBasic"),
                Map.of("sectionId", "gallery-1", "componentUId", "GalleryGrid"))))
                .doesNotThrowAnyException();
    }

    @Test
    void sectionId가_없으면_거부한다() {
        assertThatThrownBy(() -> validateRecipe(List.of(
                Map.of("componentUId", "CoverBasic"))))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TEMPLATE_RECIPE);
    }

    /** 같은 sectionId가 둘이면 청첩장 값이 어느 인스턴스 것인지 알 수 없다. */
    @Test
    void sectionId가_중복되면_거부한다() {
        when(componentService.getComponent(anyString()))
                .thenReturn(new ComponentResponse(1L, "n", "t", "GalleryGrid", Map.of()));

        assertThatThrownBy(() -> validateRecipe(List.of(
                Map.of("sectionId", "gallery", "componentUId", "GalleryGrid"),
                Map.of("sectionId", "gallery", "componentUId", "GalleryGrid"))))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TEMPLATE_RECIPE);
    }
}
