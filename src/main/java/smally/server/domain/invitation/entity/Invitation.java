package smally.server.domain.invitation.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Map;
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
        uniqueConstraints = @UniqueConstraint(name = "uk_invitation_slug", columnNames = "slug")
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

    @Builder
    private Invitation(User user, Template template, Map<String, Object> sectionValues) {
        this.user = user;
        this.template = template;
        this.sectionValues = sectionValues;
        this.status = InvitationStatus.DRAFT;
    }
}
