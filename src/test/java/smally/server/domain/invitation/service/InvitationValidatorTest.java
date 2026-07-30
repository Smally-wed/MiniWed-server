package smally.server.domain.invitation.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.domain.component.service.InternalComponentService;
import smally.server.domain.template.dto.OptionDefinitionResponse;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.service.OptionDefinitionService;

class InvitationValidatorTest {

    private final InternalComponentService componentService = mock(InternalComponentService.class);
    private final OptionDefinitionService optionDefinitionService = mock(OptionDefinitionService.class);
    private final InvitationValidator validator =
            new InvitationValidator(componentService, optionDefinitionService);

    private TemplateResponse template() {
        return new TemplateResponse("tpl-uid", "모던", "key", "모던",
                List.of(
                        Map.of("sectionId", "cover", "componentUId", "CoverBasic"),
                        Map.of("sectionId", "gallery-1", "componentUId", "GalleryGrid",
                                "editable", List.of("columns"))),
                Map.of());
    }

    private void givenColumnsAllows(Object... allowed) {
        when(optionDefinitionService.getOptionDefinition("columns")).thenReturn(
                new OptionDefinitionResponse("columns", "열 수", "select", "component",
                        List.of(allowed), allowed.length == 0 ? null : allowed[0]));
    }

    @Test
    void 임시저장은_템플릿에_있는_섹션이면_통과한다() {
        assertThatCode(() -> validator.validateForDraft(
                template(), Map.of("cover", Map.of()), Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void 템플릿에_없는_sectionId는_거부한다() {
        assertThatThrownBy(() -> validator.validateForDraft(
                template(), Map.of("없는섹션", Map.of()), Map.of()))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNKNOWN_SECTION_ID);
    }

    @Test
    void editable이_아닌_옵션은_거부한다() {
        assertThatThrownBy(() -> validator.validateForDraft(
                template(), Map.of(), Map.of("cover", Map.of("columns", 3))))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INVITATION_OPTIONS);
    }

    @Test
    void 허용값_밖의_옵션값은_거부한다() {
        givenColumnsAllows(2, 3);

        assertThatThrownBy(() -> validator.validateForDraft(
                template(), Map.of(), Map.of("gallery-1", Map.of("columns", 99))))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INVITATION_OPTIONS);
    }

    @Test
    void 허용된_옵션값은_통과한다() {
        givenColumnsAllows(2, 3);

        assertThatCode(() -> validator.validateForDraft(
                template(), Map.of(), Map.of("gallery-1", Map.of("columns", 3))))
                .doesNotThrowAnyException();
    }

    /** 핵심 차이: 임시저장은 미완성을 허용하고, 발행은 모든 섹션을 완전 검증한다. */
    @Test
    void 임시저장은_스키마_완결성을_검사하지_않는다() {
        validator.validateForDraft(template(), Map.of("cover", Map.of()), Map.of());

        org.mockito.Mockito.verifyNoInteractions(componentService);
    }

    @Test
    void 발행은_값이_없는_섹션까지_전부_검증한다() {
        validator.validateForPublish(
                template(), Map.of("cover", Map.of("groomName", "철수")), Map.of());

        // cover만 값이 있고 gallery-1은 sectionValues에 값이 없는 상황에서도,
        // gallery-1에 대한 검증 호출 자체가 실제로 일어났는지 직접 증명한다.
        // (첫 호출에서 예외를 던지는 방식은 순회 순서에 우연히 의존해 이 버그를 못 잡는다)
        verify(componentService).validateComponentJsontData(eq("GalleryGrid"), eq(Map.of()), any());
    }

    @Test
    void 선택옵션_값이_문자열이면_거부한다() {
        assertThatThrownBy(() -> validator.validateForDraft(
                template(), Map.of(), Map.of("gallery-1", "아무문자열")))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INVITATION_OPTIONS);
    }

    @Test
    void 선택옵션_값이_리스트이면_거부한다() {
        assertThatThrownBy(() -> validator.validateForDraft(
                template(), Map.of(), Map.of("gallery-1", List.of("a", "b"))))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INVITATION_OPTIONS);
    }
}
