package smally.server.domain.component.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentTypeException;
import smally.server.domain.component.dto.ComponentTypeCreateRequest;
import smally.server.domain.component.entity.ComponentType;
import smally.server.domain.component.repository.ComponentTypeRepository;

@ExtendWith(MockitoExtension.class)
class ComponentTypeServiceImplTest {

    @Mock
    ComponentTypeRepository repository;
    @Mock
    RedisCacheService redisCacheService;

    ComponentTypeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ComponentTypeServiceImpl(repository, redisCacheService);
    }

    @Test
    void createComponentType_중복이면_거절하고_저장하지_않는다() {
        when(repository.existsByName("hero")).thenReturn(true);

        assertThatThrownBy(() -> service.createComponentType(new ComponentTypeCreateRequest("hero")))
                .isInstanceOf(ComponentTypeException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_COMPONENT_TYPE);

        verify(repository, never()).save(any());
    }

    @Test
    void createComponentType_새_이름이면_저장한다() {
        when(repository.existsByName("hero")).thenReturn(false);

        assertThatCode(() -> service.createComponentType(new ComponentTypeCreateRequest("hero")))
                .doesNotThrowAnyException();

        verify(repository).save(any(ComponentType.class));
    }

    @Test
    void getComponentByName_없는_종류면_COMPONENT_TYPE_NOT_FOUND() {
        when(repository.findByName("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getComponentByName("nope"))
                .isInstanceOf(ComponentTypeException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMPONENT_TYPE_NOT_FOUND);
    }
}
