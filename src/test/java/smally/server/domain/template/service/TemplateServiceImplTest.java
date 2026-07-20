package smally.server.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentException;
import smally.server.core.exception.exceptions.OptionDefinitionException;
import smally.server.core.exception.exceptions.TemplateException;
import smally.server.domain.component.service.ComponentService;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.dto.OptionDefinitionResponse;
import smally.server.domain.template.entity.OptionDefinition;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.repository.TemplateRepository;

@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    @Mock TemplateRepository templateRepository;
    @Mock CategoryService categoryService;
    @Mock ComponentService componentService;
    @Mock OptionDefinitionService optionDefinitionService;
    @Mock RedisCacheService redisCacheService;
    @Mock smally.server.domain.image.service.StorageService storageService;

    TemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TemplateServiceImpl(
                templateRepository, categoryService, componentService,
                optionDefinitionService, redisCacheService, storageService);
    }

    private TemplateCreateRequest request(List<Map<String, Object>> sections, Map<String, Object> theme) {
        return new TemplateCreateRequest("클래식", "클래식", sections, theme);
    }

    private org.springframework.web.multipart.MultipartFile thumb() {
        return new org.springframework.mock.web.MockMultipartFile(
                "thumbnail", "c.jpg", "image/jpeg", new byte[]{1});
    }

    @Test
    void createTemplate_섹션이_비어있으면_INVALID_TEMPLATE_RECIPE() {
        assertThatThrownBy(() -> service.createTemplate(request(List.of(), null), thumb()))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TEMPLATE_RECIPE);

        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_섹션에_componentUId가_없으면_INVALID_TEMPLATE_RECIPE() {
        assertThatThrownBy(() -> service.createTemplate(
                request(List.of(Map.of("options", Map.of("titleSize", "L"))), null), thumb()))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TEMPLATE_RECIPE);

        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_참조_컴포넌트가_없으면_거절하고_저장하지_않는다() {
        doThrow(new ComponentException(ErrorCode.COMPONENT_NOT_FOUND))
                .when(componentService).getComponent("NoSuchComponent");

        assertThatThrownBy(() -> service.createTemplate(
                request(List.of(Map.of("componentUId", "NoSuchComponent")), null), thumb()))
                .isInstanceOf(ComponentException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMPONENT_NOT_FOUND);

        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_theme_키가_카탈로그에_없으면_거절한다() {
        doThrow(new OptionDefinitionException(ErrorCode.OPTION_DEFINITION_NOT_FOUND))
                .when(optionDefinitionService).getOptionDefinition("fontSize");

        assertThatThrownBy(() -> service.createTemplate(
                request(List.of(Map.of("componentUId", "GalleryGrid")), Map.of("fontSize", "large")), thumb()))
                .isInstanceOf(OptionDefinitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OPTION_DEFINITION_NOT_FOUND);

        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_theme_값이_허용값에_없으면_INVALID_TEMPLATE_THEME() {
        OptionDefinitionResponse fontSize = OptionDefinitionResponse.from(OptionDefinition.builder()
                .key("fontSize").label("폰트 크기").controlType("select").scope("global")
                .allowedValues(List.of("small", "large")).defaultValue("small")
                .build());
        when(optionDefinitionService.getOptionDefinition("fontSize")).thenReturn(fontSize);

        assertThatThrownBy(() -> service.createTemplate(
                request(List.of(Map.of("componentUId", "GalleryGrid")), Map.of("fontSize", "huge")), thumb()))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TEMPLATE_THEME);

        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_유효하면_업로드하고_객체키_저장_후_presigned를_반환한다() {
        when(storageService.upload(any(), eq("templates/thumbnails/")))
                .thenReturn("templates/thumbnails/uuid.jpg");
        when(storageService.presignedGetUrl("templates/thumbnails/uuid.jpg"))
                .thenReturn("https://signed/x");

        TemplateResponse response = service.createTemplate(
                request(List.of(Map.of("componentUId", "GalleryGrid")), null), thumb());

        org.mockito.ArgumentCaptor<Template> captor = org.mockito.ArgumentCaptor.forClass(Template.class);
        verify(templateRepository).save(captor.capture());
        assertThat(captor.getValue().getThumbnail()).isEqualTo("templates/thumbnails/uuid.jpg");
        assertThat(response.thumbnail()).isEqualTo("https://signed/x");
    }

    @Test
    void getTemplate_캐시에_있으면_DB를_조회하지_않고_presigned를_주입한다() {
        String uid = UUID.randomUUID().toString();
        TemplateResponse cached = new TemplateResponse(
                uid, "클래식", "templates/thumbnails/uuid.jpg", "클래식",
                List.of(Map.of("componentUId", "GalleryGrid")), null);
        when(redisCacheService.getCacheData("TPL:" + uid, TemplateResponse.class)).thenReturn(cached);
        when(storageService.presignedGetUrl("templates/thumbnails/uuid.jpg"))
                .thenReturn("https://signed/x");

        TemplateResponse response = service.getTemplate(uid);

        assertThat(response.thumbnail()).isEqualTo("https://signed/x");
        verifyNoInteractions(templateRepository);
    }

    @Test
    void getTemplate_캐시가_비면_DB를_조회하고_키응답을_캐시에_적재한다() {
        UUID uid = UUID.randomUUID();
        Template template = Template.builder()
                .name("클래식").category("클래식")
                .sections(List.of(Map.of("componentUId", "GalleryGrid"))).build();
        when(templateRepository.findTemplateByTemplateUid(uid)).thenReturn(Optional.of(template));

        TemplateResponse response = service.getTemplate(uid.toString());

        assertThat(response.name()).isEqualTo("클래식");
        // thumbnail(null)일 때 presigned 미호출
        org.mockito.Mockito.verifyNoInteractions(storageService);
        // 캐시에는 객체 키(null) 응답이 적재됨
        verify(redisCacheService).setCacheData(eq("TPL:" + uid), any(TemplateResponse.class), any());
    }

    @Test
    void getTemplate_없으면_TEMPLATE_NOT_FOUND() {
        when(templateRepository.findTemplateByTemplateUid(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTemplate(UUID.randomUUID().toString()))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TEMPLATE_NOT_FOUND);
    }

    @Test
    void getTemplate_uid가_UUID_형식이_아니면_TEMPLATE_NOT_FOUND() {
        assertThatThrownBy(() -> service.getTemplate("not-a-uuid"))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TEMPLATE_NOT_FOUND);

        verifyNoInteractions(templateRepository);
    }
}
