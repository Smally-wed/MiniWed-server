package smally.server.domain.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.domain.image.service.InvitationImageService;
import smally.server.domain.invitation.dto.InvitationUpdateRequest;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.invitation.enums.InvitationStatus;
import smally.server.domain.invitation.repository.InvitationRepository;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.service.TemplateService;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.enums.UserRole;
import smally.server.domain.user.service.InternalUserService;

class InvitationServiceImplTest {

    private static final UUID INVITATION_UID = UUID.randomUUID();
    private static final UUID TEMPLATE_UID = UUID.randomUUID();

    private final InvitationRepository invitationRepository = mock(InvitationRepository.class);
    private final InternalUserService userService = mock(InternalUserService.class);
    private final TemplateService templateService = mock(TemplateService.class);
    private final InvitationValidator validator = mock(InvitationValidator.class);
    private final InvitationImageService imageService = mock(InvitationImageService.class);
    private final SlugGenerator slugGenerator = mock(SlugGenerator.class);

    private final InvitationServiceImpl service = new InvitationServiceImpl(
            invitationRepository, userService, templateService,
            validator, imageService, slugGenerator);

    private Invitation invitation;

    @BeforeEach
    void setUp() {
        User owner = User.builder().email("a@b.com").userRole(UserRole.USER).build();
        ReflectionTestUtils.setField(owner, "id", 7L);

        Template template = Template.builder().name("t").category("모던")
                .sections(List.of(Map.of("sectionId", "cover", "componentUId", "CoverBasic")))
                .build();
        ReflectionTestUtils.setField(template, "templateUid", TEMPLATE_UID);

        invitation = Invitation.builder().user(owner).template(template).build();
        ReflectionTestUtils.setField(invitation, "invitationUid", INVITATION_UID);

        when(invitationRepository.findByInvitationUid(INVITATION_UID))
                .thenReturn(Optional.of(invitation));
        when(templateService.getTemplate(anyString())).thenReturn(
                new TemplateResponse(TEMPLATE_UID.toString(), "t", "key", "모던",
                        List.of(Map.of("sectionId", "cover", "componentUId", "CoverBasic")),
                        Map.of()));
        when(imageService.withPresignedUrls(any())).thenReturn(Map.of());
    }

    @Test
    void 저장하면_검증과_이미지_연결을_거쳐_내용이_바뀐다() {
        Map<String, Object> values = Map.of("cover", Map.of("groomName", "철수"));

        service.update(7L, INVITATION_UID.toString(),
                new InvitationUpdateRequest(values, Map.of()));

        verify(validator).validateForDraft(any(), any(), any());
        verify(imageService).link(invitation, values);
        assertThat(invitation.getSectionValues()).isEqualTo(values);
    }

    @Test
    void 남의_청첩장은_403이다() {
        assertThatThrownBy(() -> service.update(99L, INVITATION_UID.toString(),
                new InvitationUpdateRequest(Map.of(), Map.of())))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITATION_ACCESS_DENIED);
    }

    @Test
    void 없는_청첩장은_404이다() {
        UUID unknown = UUID.randomUUID();
        when(invitationRepository.findByInvitationUid(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(7L, unknown.toString()))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITATION_NOT_FOUND);
    }

    @Test
    void uid_형식이_아니면_404이다() {
        assertThatThrownBy(() -> service.get(7L, "uid가-아님"))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITATION_NOT_FOUND);
    }

    @Test
    void 발행하면_완결성_검증을_거쳐_slug가_발급된다() {
        when(slugGenerator.generate()).thenReturn("abc123");

        service.publish(7L, INVITATION_UID.toString());

        verify(validator).validateForPublish(any(), any(), any());
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PUBLISHED);
        assertThat(invitation.getSlug()).isEqualTo("abc123");
    }

    @Test
    void 이미_발행된_청첩장은_다시_발행하지_않는다() {
        invitation.publish("abc123");

        assertThatThrownBy(() -> service.publish(7L, INVITATION_UID.toString()))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITATION_ALREADY_PUBLISHED);
    }

    @Test
    void 재발행할_때는_slug를_새로_만들지_않는다() {
        invitation.publish("abc123");
        invitation.unpublish();

        service.publish(7L, INVITATION_UID.toString());

        verify(slugGenerator, never()).generate();
        assertThat(invitation.getSlug()).isEqualTo("abc123");
    }

    @Test
    void 발행된_청첩장은_발행을_취소하면_DRAFT로_돌아간다() {
        invitation.publish("abc123");

        service.unpublish(7L, INVITATION_UID.toString());

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DRAFT);
    }

    @Test
    void 발행되지_않은_청첩장은_발행을_취소할_수_없다() {
        // publish()가 INVITATION_ALREADY_PUBLISHED로 막는 것과 대칭.
        assertThatThrownBy(() -> service.unpublish(7L, INVITATION_UID.toString()))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITATION_NOT_PUBLISHED);
    }

    @Test
    void 삭제하면_이미지_연결을_끊은_후에_삭제한다() {
        // 이미지가 붙은 청첩장을 그냥 지우면 image_uploads FK 위반으로 500이 난다.
        // unlinkAll이 delete보다 먼저 호출돼야 한다.
        service.delete(7L, INVITATION_UID.toString());

        InOrder order = inOrder(imageService, invitationRepository);
        order.verify(imageService).unlinkAll(invitation);
        order.verify(invitationRepository).delete(invitation);
    }

    @Test
    void 남의_청첩장은_삭제할_수_없다() {
        assertThatThrownBy(() -> service.delete(99L, INVITATION_UID.toString()))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITATION_ACCESS_DENIED);
    }

    @Test
    void 발행된_청첩장을_저장하면_발행_수준_검증을_받는다() {
        // PUBLISHED 상태에서 임시저장 검증만 거치면 컴포넌트 스키마가 비어도 통과해
        // 하객 공개 조회에 깨진 청첩장이 노출된다(ADR-009 결정 2).
        invitation.publish("abc123");
        Map<String, Object> values = Map.of("cover", Map.of("groomName", "철수"));

        service.update(7L, INVITATION_UID.toString(),
                new InvitationUpdateRequest(values, Map.of()));

        verify(validator).validateForPublish(any(), any(), any());
        verify(validator, never()).validateForDraft(any(), any(), any());
    }

    @Test
    void 임시저장_상태의_청첩장을_저장하면_임시저장_검증을_받는다() {
        Map<String, Object> values = Map.of("cover", Map.of("groomName", "철수"));

        service.update(7L, INVITATION_UID.toString(),
                new InvitationUpdateRequest(values, Map.of()));

        verify(validator).validateForDraft(any(), any(), any());
        verify(validator, never()).validateForPublish(any(), any(), any());
    }
}
