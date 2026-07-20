package smally.server.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.OptionDefinitionException;
import smally.server.domain.template.dto.OptionDefinitionCreateRequest;
import smally.server.domain.template.dto.OptionDefinitionResponse;
import smally.server.domain.template.entity.OptionDefinition;
import smally.server.domain.template.repository.OptionDefinitionRepository;

@ExtendWith(MockitoExtension.class)
class OptionDefinitionServiceImplTest {

    @Mock OptionDefinitionRepository repository;
    @Mock RedisCacheService redisCacheService;
    OptionDefinitionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OptionDefinitionServiceImpl(repository, redisCacheService);
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

    @Test
    void getOptionDefinition_캐시에_있으면_DB를_조회하지_않는다() {
        OptionDefinitionResponse cached = new OptionDefinitionResponse(
                "fontSize", "폰트 크기", "select", "global", List.of("small", "large"), "small");
        when(redisCacheService.getCacheData("OPT:fontSize", OptionDefinitionResponse.class))
                .thenReturn(cached);

        assertThat(service.getOptionDefinition("fontSize")).isEqualTo(cached);

        verify(repository, never()).findByKey(any());
    }

    @Test
    void getOptionDefinition_캐시미스면_DB조회후_캐시에_저장한다() {
        OptionDefinition entity = OptionDefinition.builder()
                .key("fontSize").label("폰트 크기").controlType("select").scope("global")
                .allowedValues(List.of("small", "large")).defaultValue("small")
                .build();
        when(redisCacheService.getCacheData("OPT:fontSize", OptionDefinitionResponse.class))
                .thenReturn(null);
        when(repository.findByKey("fontSize")).thenReturn(Optional.of(entity));

        OptionDefinitionResponse result = service.getOptionDefinition("fontSize");

        assertThat(result.key()).isEqualTo("fontSize");
        verify(redisCacheService).setCacheData(eq("OPT:fontSize"), eq(result), any(Duration.class));
    }

    @Test
    void getAllOptionDefinitions_캐시에_있으면_DB를_조회하지_않는다() {
        List<OptionDefinitionResponse> cached = List.of(new OptionDefinitionResponse(
                "fontSize", "폰트 크기", "select", "global", List.of("small", "large"), "small"));
        when(redisCacheService.getCacheData("OPT:ALL", List.class)).thenReturn(cached);

        assertThat(service.getAllOptionDefinitions()).isEqualTo(cached);

        verify(repository, never()).findAll();
    }

    @Test
    void createOptionDefinition_저장후_전체목록_캐시를_삭제한다() {
        when(repository.existsByKey("fontSize")).thenReturn(false);

        service.createOptionDefinition(request(List.of("small", "large"), "small"));

        verify(redisCacheService).setCacheData(eq("OPT:fontSize"), any(), any(Duration.class));
        verify(redisCacheService).deleteCacheData("OPT:ALL");
    }
}
