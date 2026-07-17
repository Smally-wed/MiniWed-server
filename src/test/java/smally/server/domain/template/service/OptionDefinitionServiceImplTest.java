package smally.server.domain.template.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.OptionDefinitionException;
import smally.server.domain.template.dto.OptionDefinitionCreateRequest;
import smally.server.domain.template.entity.OptionDefinition;
import smally.server.domain.template.repository.OptionDefinitionRepository;

@ExtendWith(MockitoExtension.class)
class OptionDefinitionServiceImplTest {

    @Mock OptionDefinitionRepository repository;
    OptionDefinitionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OptionDefinitionServiceImpl(repository);
    }

    private OptionDefinitionCreateRequest request(List<Object> allowed, Object def) {
        return new OptionDefinitionCreateRequest("fontSize", "폰트 크기", "select", "global", allowed, def);
    }

    @Test
    void createOptionDefinition_기본값이_allowedValues에_없으면_거절하고_저장하지_않는다() {
        when(repository.existsByKey("fontSize")).thenReturn(false);
        OptionDefinitionCreateRequest req = request(List.of("small", "large"), "huge");

        assertThatThrownBy(() -> service.createOptionDefinition(req))
                .isInstanceOf(OptionDefinitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_OPTION_DEFAULT);

        verify(repository, never()).save(any());
    }

    @Test
    void createOptionDefinition_키가_중복이면_거절한다() {
        when(repository.existsByKey("fontSize")).thenReturn(true);
        OptionDefinitionCreateRequest req = request(List.of("small", "large"), "small");

        assertThatThrownBy(() -> service.createOptionDefinition(req))
                .isInstanceOf(OptionDefinitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_OPTION_DEFINITION);

        verify(repository, never()).save(any());
    }

    @Test
    void createOptionDefinition_기본값이_allowedValues에_있으면_저장한다() {
        when(repository.existsByKey("fontSize")).thenReturn(false);
        OptionDefinitionCreateRequest req = request(List.of("small", "large"), "large");

        assertThatCode(() -> service.createOptionDefinition(req)).doesNotThrowAnyException();

        verify(repository).save(any(OptionDefinition.class));
    }

    @Test
    void createOptionDefinition_allowedValues가_없으면_기본값검증을_건너뛴다() {
        when(repository.existsByKey("fontSize")).thenReturn(false);
        OptionDefinitionCreateRequest req = request(null, "anything");

        assertThatCode(() -> service.createOptionDefinition(req)).doesNotThrowAnyException();

        verify(repository).save(any(OptionDefinition.class));
    }

    @Test
    void createOptionDefinition_기본값이_null이면_allowedValues가_있어도_저장한다() {
        when(repository.existsByKey("fontSize")).thenReturn(false);
        OptionDefinitionCreateRequest req = request(List.of("small", "large"), null);

        assertThatCode(() -> service.createOptionDefinition(req)).doesNotThrowAnyException();

        verify(repository).save(any(OptionDefinition.class));
    }

    @Test
    void createOptionDefinition_allowedValues가_비어있으면_기본값검증을_건너뛴다() {
        when(repository.existsByKey("fontSize")).thenReturn(false);
        OptionDefinitionCreateRequest req = request(List.of(), "anything");

        assertThatCode(() -> service.createOptionDefinition(req)).doesNotThrowAnyException();

        verify(repository).save(any(OptionDefinition.class));
    }

    @Test
    void getOptionDefinition_없는_키면_OPTION_DEFINITION_NOT_FOUND() {
        when(repository.findByKey("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOptionDefinition("nope"))
                .isInstanceOf(OptionDefinitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OPTION_DEFINITION_NOT_FOUND);
    }
}
