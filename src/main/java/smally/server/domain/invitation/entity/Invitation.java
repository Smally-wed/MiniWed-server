package smally.server.domain.invitation.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import smally.server.domain.common.entity.BaseEntity;
import smally.server.domain.invitation.enums.InvitationStatus;
import smally.server.domain.template.entity.Template;
import smally.server.domain.user.entity.User;

@Entity
@Table(
        name = "invitations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_invitation_slug", columnNames = "slug"),
                @UniqueConstraint(name = "uk_invitation_uid", columnNames = "invitation_uid")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invitation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    // 추측 불가 무작위 문자열. 발행 시 발급 (발행 전 null)
    @Column(unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status;

    // 섹션 입력값. 사진은 S3 키/URL만 (ADR-001, ADR-002)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "section_values")
    private Map<String, Object> sectionValues;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false, unique = true, updatable = false, columnDefinition = "UUID")
    private UUID invitationUid;

    // 사용자가 고른 섹션별 옵션값. {sectionId → {optionKey → 값}} (ADR-009)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_options")
    private Map<String, Object> selectedOptions;

    @Builder
    private Invitation(User user, Template template,
                       Map<String, Object> sectionValues, Map<String, Object> selectedOptions) {
        this.user = user;
        this.template = template;
        this.sectionValues = sectionValues;
        this.selectedOptions = selectedOptions;
        this.status = InvitationStatus.DRAFT;
    }

    @PrePersist
    protected void onCreate() {
        if (this.invitationUid == null) {
            this.invitationUid = UuidCreator.getTimeOrderedEpoch();
        }
    }

    public void updateContent(Map<String, Object> sectionValues, Map<String, Object> selectedOptions) {
        this.sectionValues = sectionValues;
        this.selectedOptions = selectedOptions;
    }

    public void publish(String slug) {
        this.slug = slug;
        this.status = InvitationStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void unpublish() {
        this.status = InvitationStatus.DRAFT;
        this.publishedAt = null;
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && user != null && userId.equals(user.getId());
    }
}
