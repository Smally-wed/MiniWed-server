package smally.server.domain.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.domain.image.service.InvitationImageService;
import smally.server.domain.invitation.dto.PublicInvitationResponse;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.invitation.enums.InvitationStatus;
import smally.server.domain.invitation.repository.InvitationRepository;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.service.TemplateService;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.enums.UserRole;

class PublicInvitationServiceImplTest {

    private static final UUID TEMPLATE_UID = UUID.randomUUID();

    private final InvitationRepository invitationRepository = mock(InvitationRepository.class);
    private final TemplateService templateService = mock(TemplateService.class);
    private final InvitationImageService imageService = mock(InvitationImageService.class);

    private final PublicInvitationServiceImpl service =
            new PublicInvitationServiceImpl(invitationRepository, templateService, imageService);

    private Invitation published;

    @BeforeEach
    void setUp() {
        User owner = User.builder().email("a@b.com").userRole(UserRole.USER).build();
        ReflectionTestUtils.setField(owner, "id", 7L);

        Template template = Template.builder().name("t").category("모던")
                .sections(List.of(Map.of("sectionId", "gallery-1", "componentUId", "GalleryGrid")))
                .build();
        ReflectionTestUtils.setField(template, "templateUid", TEMPLATE_UID);

        published = Invitation.builder().user(owner).template(template)
                .sectionValues(Map.of("gallery-1", Map.of("photos", List.of("invitations/7/a.jpg"))))
                .selectedOptions(Map.of("gallery-1", Map.of("columns", 3)))
                .build();
        ReflectionTestUtils.setField(published, "invitationUid", UUID.randomUUID());
        published.publish("abc123");

        when(templateService.getTemplate(anyString())).thenReturn(
                new TemplateResponse(TEMPLATE_UID.toString(), "t", "key", "모던",
                        List.of(Map.of("sectionId", "gallery-1", "componentUId", "GalleryGrid",
                                "options", Map.of("columns", 2, "gap", 8))),
                        Map.of("fontFamily", "serif")));
        when(imageService.withPresignedUrls(any()))
                .thenReturn(Map.of("gallery-1", Map.of("photos", List.of("https://s3/signed"))));
    }

    @Test
    void 발행된_청첩장은_slug로_조회된다() {
        when(invitationRepository.findBySlugAndStatus("abc123", InvitationStatus.PUBLISHED))
                .thenReturn(Optional.of(published));

        PublicInvitationResponse response = service.getBySlug("abc123");

        assertThat(response.theme()).containsEntry("fontFamily", "serif");
        assertThat(response.publishedAt()).isNotNull();
    }

    @Test
    void 사용자가_고른_옵션이_템플릿_고정옵션_위에_덮인다() {
        when(invitationRepository.findBySlugAndStatus("abc123", InvitationStatus.PUBLISHED))
                .thenReturn(Optional.of(published));

        PublicInvitationResponse response = service.getBySlug("abc123");

        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) response.sections().getFirst().get("options");
        assertThat(options).containsEntry("columns", 3).containsEntry("gap", 8);
    }

    @Test
    void 이미지_키는_presigned_URL로_치환된다() {
        when(invitationRepository.findBySlugAndStatus("abc123", InvitationStatus.PUBLISHED))
                .thenReturn(Optional.of(published));

        PublicInvitationResponse response = service.getBySlug("abc123");

        assertThat(response.sectionValues().toString()).contains("https://s3/signed");
    }

    /** DRAFT는 findBySlugAndStatus에 걸리지 않으므로 존재 자체가 드러나지 않는다. */
    @Test
    void 발행되지_않았거나_없는_slug는_404이다() {
        when(invitationRepository.findBySlugAndStatus("없는slug", InvitationStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBySlug("없는slug"))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITATION_NOT_FOUND);
    }
}
