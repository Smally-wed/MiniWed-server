package smally.server.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import smally.server.core.exception.exceptions.ImageException;
import smally.server.domain.image.entity.ImageUpload;
import smally.server.domain.image.enums.ImageStatus;
import smally.server.domain.image.repository.ImageUploadRepository;
import smally.server.domain.image.util.SectionValueImageScanner;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.template.entity.Template;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.enums.UserRole;

class InvitationImageServiceTest {

    private final ImageUploadRepository imageUploadRepository = mock(ImageUploadRepository.class);
    private final StorageService storageService = mock(StorageService.class);
    private final InvitationImageServiceImpl service = new InvitationImageServiceImpl(
            imageUploadRepository, storageService, new SectionValueImageScanner());

    private User user(Long id) {
        User user = User.builder().email("a@b.com").userRole(UserRole.USER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Invitation invitation(User owner) {
        return Invitation.builder()
                .user(owner)
                .template(Template.builder().name("t").category("모던")
                        .sections(List.of(Map.of("sectionId", "cover", "componentUId", "CoverBasic")))
                        .build())
                .build();
    }

    private ImageUpload upload(User uploader, String objectKey, ImageStatus status) {
        return ImageUpload.builder()
                .uploader(uploader).objectKey(objectKey).status(status).build();
    }

    @Test
    void 본인이_올린_PENDING_이미지는_청첩장에_연결된다() {
        User owner = user(7L);
        ImageUpload pending = upload(owner, "invitations/7/a.jpg", ImageStatus.PENDING);
        when(imageUploadRepository.findAllByObjectKeyIn(anyCollection())).thenReturn(List.of(pending));
        when(imageUploadRepository.findAllByInvitation(any())).thenReturn(List.of());

        Invitation invitation = invitation(owner);
        service.link(invitation, Map.of("cover", Map.of("photo", "invitations/7/a.jpg")));

        assertThat(pending.getStatus()).isEqualTo(ImageStatus.LINKED);
        assertThat(pending.getInvitation()).isEqualTo(invitation);
    }

    @Test
    void 남이_올린_이미지를_붙이려_하면_거부한다() {
        User owner = user(7L);
        ImageUpload othersImage = upload(user(99L), "invitations/99/a.jpg", ImageStatus.PENDING);
        when(imageUploadRepository.findAllByObjectKeyIn(anyCollection()))
                .thenReturn(List.of(othersImage));

        assertThatThrownBy(() -> service.link(
                invitation(owner), Map.of("cover", Map.of("photo", "invitations/99/a.jpg"))))
                .isInstanceOf(ImageException.class);
    }

    /** 프리픽스 스캔은 정밀하지 않다. 오탐 때문에 사용자의 저장이 실패하면 안 된다. */
    @Test
    void 업로드_기록이_없는_키는_무시한다() {
        User owner = user(7L);
        when(imageUploadRepository.findAllByObjectKeyIn(anyCollection())).thenReturn(List.of());
        when(imageUploadRepository.findAllByInvitation(any())).thenReturn(List.of());

        assertThatCode(() -> service.link(
                invitation(owner), Map.of("cover", Map.of("note", "invitations/오타난 텍스트"))))
                .doesNotThrowAnyException();
    }

    @Test
    void 이미_다른_청첩장에_연결된_이미지는_재연결하지_않고_거부한다() {
        // 재연결하면 원래 청첩장이 사진을 잃고 소유가 뒤엉킨다.
        User owner = user(7L);
        Invitation other = invitation(owner);
        ReflectionTestUtils.setField(other, "id", 1L);
        Invitation target = invitation(owner);
        ReflectionTestUtils.setField(target, "id", 2L);

        ImageUpload linkedElsewhere = upload(owner, "invitations/7/a.jpg", ImageStatus.LINKED);
        ReflectionTestUtils.setField(linkedElsewhere, "invitation", other);
        when(imageUploadRepository.findAllByObjectKeyIn(anyCollection()))
                .thenReturn(List.of(linkedElsewhere));

        assertThatThrownBy(() -> service.link(
                target, Map.of("cover", Map.of("photo", "invitations/7/a.jpg"))))
                .isInstanceOf(ImageException.class);
    }

    @Test
    void 같은_청첩장에_이미_연결된_이미지의_재저장은_허용한다() {
        User owner = user(7L);
        Invitation invitation = invitation(owner);
        ReflectionTestUtils.setField(invitation, "id", 1L);

        ImageUpload alreadyLinked = upload(owner, "invitations/7/a.jpg", ImageStatus.LINKED);
        ReflectionTestUtils.setField(alreadyLinked, "invitation", invitation);
        when(imageUploadRepository.findAllByObjectKeyIn(anyCollection()))
                .thenReturn(List.of(alreadyLinked));
        when(imageUploadRepository.findAllByInvitation(invitation)).thenReturn(List.of(alreadyLinked));

        assertThatCode(() -> service.link(
                invitation, Map.of("cover", Map.of("photo", "invitations/7/a.jpg"))))
                .doesNotThrowAnyException();
        assertThat(alreadyLinked.getStatus()).isEqualTo(ImageStatus.LINKED);
    }

    @Test
    void 이번_저장에서_빠진_이미지는_고아로_표시한다() {
        User owner = user(7L);
        Invitation invitation = invitation(owner);
        ImageUpload removed = upload(owner, "invitations/7/old.jpg", ImageStatus.LINKED);
        when(imageUploadRepository.findAllByObjectKeyIn(anyCollection())).thenReturn(List.of());
        when(imageUploadRepository.findAllByInvitation(invitation)).thenReturn(List.of(removed));

        service.link(invitation, Map.of("cover", Map.of("groomName", "철수")));

        assertThat(removed.getStatus()).isEqualTo(ImageStatus.ORPHANED);
    }

    @Test
    void unlinkAll은_연결된_이미지의_참조를_끊고_고아로_표시한다() {
        // 청첩장 삭제 전에 호출된다 — 참조가 남아있으면 FK 위반으로 삭제가 실패한다.
        User owner = user(7L);
        Invitation invitation = invitation(owner);
        ImageUpload linked = upload(owner, "invitations/7/a.jpg", ImageStatus.LINKED);
        ReflectionTestUtils.setField(linked, "invitation", invitation);
        when(imageUploadRepository.findAllByInvitation(invitation)).thenReturn(List.of(linked));

        service.unlinkAll(invitation);

        assertThat(linked.getInvitation()).isNull();
        assertThat(linked.getStatus()).isEqualTo(ImageStatus.ORPHANED);
    }

    @Test
    void 조회용_치환은_이미지_키를_presigned_URL로_바꾼다() {
        when(storageService.presignedGetUrl("invitations/7/a.jpg")).thenReturn("https://s3/signed");

        Map<String, Object> result = service.withPresignedUrls(
                Map.of("cover", Map.of("photo", "invitations/7/a.jpg")));

        @SuppressWarnings("unchecked")
        Map<String, Object> cover = (Map<String, Object>) result.get("cover");
        assertThat(cover.get("photo")).isEqualTo("https://s3/signed");
    }
}
