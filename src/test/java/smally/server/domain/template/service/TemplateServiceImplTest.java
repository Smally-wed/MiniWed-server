package smally.server.domain.template.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.TemplateException;
import smally.server.core.validation.SchemaValidator;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.repository.TemplateRepository;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    @Mock TemplateRepository templateRepository;
    @Mock CategoryService categoryService;

    SchemaValidator schemaValidator = new SchemaValidator(new ObjectMapper());
    TemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TemplateServiceImpl(templateRepository, categoryService, schemaValidator);
    }

    @Test
    void isValidTemplate_입력값이_스키마를_어기면_INVALID_SECTION_VALUES() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("greeting", Map.of("type", "string")),
                "required", List.of("greeting"));
        Template template = Template.builder().name("t").category("c").sectionSchema(schema).build();
        when(templateRepository.findTemplateByTemplateUid(any())).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.isValidTemplate(UUID.randomUUID().toString(), Map.of()))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_SECTION_VALUES);
    }

    @Test
    void isValidTemplate_입력값이_스키마를_지키면_통과() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("greeting", Map.of("type", "string")),
                "required", List.of("greeting"));
        Template template = Template.builder().name("t").category("c").sectionSchema(schema).build();
        when(templateRepository.findTemplateByTemplateUid(any())).thenReturn(Optional.of(template));

        assertThatCode(() -> service.isValidTemplate(UUID.randomUUID().toString(), Map.of("greeting", "안녕")))
                .doesNotThrowAnyException();
    }

    @Test
    void createTemplate_optionsSchema가_유효하지_않으면_거절하고_저장하지_않는다() {
        Map<String, Object> invalidOptions = Map.of("type", 123); // type은 문자열이어야 함
        TemplateCreateRequest request = new TemplateCreateRequest(
                "t", null, "클래식", Map.of("type", "object"), invalidOptions, null);

        assertThatThrownBy(() -> service.createTemplate(request))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TEMPLATE_OPTIONS_SCHEMA);

        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_optionsSchema가_유효하면_저장한다() {
        Map<String, Object> validOptions = Map.of(
                "type", "object",
                "properties", Map.of("fontSize", Map.of("enum", List.of("small", "large"))));
        TemplateCreateRequest request = new TemplateCreateRequest(
                "t", null, "클래식", Map.of("type", "object"), validOptions, null);
        when(templateRepository.save(any(Template.class))).thenAnswer(invocation -> {
            Template saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "templateUid", UUID.randomUUID());
            return saved;
        });

        assertThatCode(() -> service.createTemplate(request)).doesNotThrowAnyException();

        verify(templateRepository).save(any(Template.class));
    }
}
