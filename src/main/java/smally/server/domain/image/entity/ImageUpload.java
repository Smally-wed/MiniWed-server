package smally.server.domain.image.entity;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import smally.server.domain.common.entity.BaseEntity;
import smally.server.domain.image.enums.ImageStatus;
import smally.server.domain.invitation.entity.Invitation;

/**
 * 업로드 추적 (ADR-001) — 고아 객체(orphan) 정리용. 확정 전에는 invitation 미연결(null).
 */
@Entity
@Table(name = "image_uploads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageUpload extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_upload_id")
    private Long id;

    // 확정 전 null 가능 (고아 상태)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitation_id")
    private Invitation invitation;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageStatus status;

    @Builder
    private ImageUpload(Invitation invitation, String objectKey, ImageStatus status) {
        this.invitation = invitation;
        this.objectKey = objectKey;
        this.status = status != null ? status : ImageStatus.PENDING;
    }
}
