package smally.server.domain.component.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentException;
import smally.server.core.exception.exceptions.ComponentTypeException;
import smally.server.core.cache.RedisCacheService;
import smally.server.core.validation.SchemaValidator;
import smally.server.domain.component.dto.ComponentCreateRequest;
import smally.server.domain.component.entity.Component;
import smally.server.domain.component.entity.ComponentType;
import smally.server.domain.component.repository.ComponentRepository;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ComponentServiceImplTest {

    @Mock ComponentRepository componentRepository;
    @Mock
    ComponentTypeService componentTypeService;
    @Mock
    RedisCacheService redisCacheService;
    SchemaValidator schemaValidator = new SchemaValidator(new ObjectMapper());
    ComponentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ComponentServiceImpl(componentRepository, componentTypeService, schemaValidator, redisCacheService);
    }

    private ComponentCreateRequest request(Map<String, Object> dataSchema) {
        return new ComponentCreateRequest("클래식 갤러리", "gallery", "GalleryGrid", dataSchema, null);
    }

    @Test
    void createComponent_종류가_없으면_거절하고_저장하지_않는다() {
        doThrow(new ComponentTypeException(ErrorCode.COMPONENT_TYPE_NOT_FOUND))
                .when(componentTypeService).getComponentByName("gallery");

        assertThatThrownBy(() -> service.createComponent(request(Map.of("type", "object"))))
                .isInstanceOf(ComponentTypeException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMPONENT_TYPE_NOT_FOUND);

        verify(componentRepository, never()).save(any());
    }

    @Test
    void createComponent_dataSchema가_유효하지_않으면_거절하고_저장하지_않는다() {
        ComponentCreateRequest req = request(Map.of("type", 123)); // type은 문자열이어야 함

        assertThatThrownBy(() -> service.createComponent(req))
                .isInstanceOf(ComponentException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_COMPONENT_SCHEMA);

        verify(componentRepository, never()).save(any());
    }

    @Test
    void createComponent_유효하면_저장한다() {
        ComponentCreateRequest req = request(Map.of("type", "object"));
        when(componentTypeService.getComponentByName("gallery"))
                .thenReturn(ComponentType.builder().name("gallery").build());

        assertThatCode(() -> service.createComponent(req)).doesNotThrowAnyException();

        verify(componentRepository).save(any(Component.class));
    }

    @Test
    void getComponent_없는_componentUId면_COMPONENT_NOT_FOUND() {
        when(componentRepository.findByComponentUId("GalleryGrid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getComponent("GalleryGrid"))
                .isInstanceOf(ComponentException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMPONENT_NOT_FOUND);
    }
}
