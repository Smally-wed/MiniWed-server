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
import smally.server.domain.user.entity.User;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

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
    private ImageUpload(User uploader, Invitation invitation, String objectKey, ImageStatus status) {
        this.uploader = uploader;
        this.invitation = invitation;
        this.objectKey = objectKey;
        this.status = status != null ? status : ImageStatus.PENDING;
    }

    public void linkTo(Invitation invitation) {
        this.invitation = invitation;
        this.status = ImageStatus.LINKED;
    }

    /** 값에서 빠진 이미지. 실제 S3 삭제는 후속 배치가 한다(ADR-009). */
    public void markOrphaned() {
        this.status = ImageStatus.ORPHANED;
    }

    /**
     * 청첩장이 삭제될 때 FK 참조를 끊는다. 참조가 남아있는 채로 청첩장이 삭제되면
     * image_uploads.invitation_id FK 위반으로 DataIntegrityViolationException이 난다.
     * markOrphaned()와 의미가 겹치지 않도록 별도 메서드로 둔다 — markOrphaned()는
     * "이번 저장에서 값이 빠졌다"는 의미로 이미 다른 호출처(link 처리 중 고아 표시)가 있어
     * 그 동작(참조 유지)까지 바꾸면 기존 흐름이 영향을 받을 수 있다.
     */
    public void unlink() {
        this.invitation = null;
        this.status = ImageStatus.ORPHANED;
    }

    public boolean isUploadedBy(Long userId) {
        return userId != null && uploader != null && userId.equals(uploader.getId());
    }

    /**
     * 이미 다른 청첩장에 연결된 이미지인지. 같은 청첩장의 재저장은 정상이므로 제외한다.
     * 한 이미지는 한 청첩장만 가리키므로(N:1), 다른 청첩장에 재연결하면 원래 청첩장이
     * 그 사진을 잃고 소유가 뒤엉킨다.
     */
    public boolean isLinkedToOtherThan(Invitation invitation) {
        return this.invitation != null
                && !this.invitation.getId().equals(invitation.getId());
    }
}
