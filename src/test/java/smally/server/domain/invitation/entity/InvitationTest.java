package smally.server.domain.invitation.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import smally.server.domain.invitation.enums.InvitationStatus;
import smally.server.domain.template.entity.Template;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.enums.UserRole;

class InvitationTest {

    private User user(Long id) {
        User user = User.builder()
                .email("a@b.com").userRole(UserRole.USER).nickname("신랑").build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Template template() {
        return Template.builder().name("t").category("모던")
                .sections(java.util.List.of(Map.of("sectionId", "cover", "componentUId", "CoverBasic")))
                .build();
    }

    private Invitation invitation(Long userId) {
        return Invitation.builder().user(user(userId)).template(template()).build();
    }

    @Test
    void 생성_직후에는_DRAFT이고_slug와_발행시각이_없다() {
        Invitation invitation = invitation(7L);

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DRAFT);
        assertThat(invitation.getSlug()).isNull();
        assertThat(invitation.getPublishedAt()).isNull();
    }

    @Test
    void 내용을_저장하면_두_jsonb가_통째로_교체된다() {
        Invitation invitation = invitation(7L);

        invitation.updateContent(
                Map.of("cover", Map.of("groomName", "철수")),
                Map.of("gallery-1", Map.of("columns", 3)));

        assertThat(invitation.getSectionValues()).containsKey("cover");
        assertThat(invitation.getSelectedOptions()).containsKey("gallery-1");
    }

    @Test
    void 발행하면_slug와_발행시각이_채워진다() {
        Invitation invitation = invitation(7L);

        invitation.publish("abc123");

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PUBLISHED);
        assertThat(invitation.getSlug()).isEqualTo("abc123");
        assertThat(invitation.getPublishedAt()).isNotNull();
    }

    /** 이미 공유된 링크를 다른 청첩장이 넘겨받지 않도록 slug는 회수하지 않는다. */
    @Test
    void 발행을_취소해도_slug는_유지된다() {
        Invitation invitation = invitation(7L);
        invitation.publish("abc123");

        invitation.unpublish();

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DRAFT);
        assertThat(invitation.getPublishedAt()).isNull();
        assertThat(invitation.getSlug()).isEqualTo("abc123");
    }

    @Test
    void 소유자_판별은_사용자_id로_한다() {
        Invitation invitation = invitation(7L);

        assertThat(invitation.isOwnedBy(7L)).isTrue();
        assertThat(invitation.isOwnedBy(8L)).isFalse();
        assertThat(invitation.isOwnedBy(null)).isFalse();
    }
}
