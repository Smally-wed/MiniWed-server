package smally.server.domain.image.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ImageException;
import smally.server.domain.image.dto.ImageUploadResponse;
import smally.server.domain.image.entity.ImageUpload;
import smally.server.domain.image.enums.ImageStatus;
import smally.server.domain.image.repository.ImageUploadRepository;
import smally.server.domain.image.util.SectionValueImageScanner;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.user.entity.User;

@Service
@RequiredArgsConstructor
public class InvitationImageServiceImpl implements InvitationImageService {

    private static final String KEY_PREFIX_FORMAT = "invitations/%d/";

    private final ImageUploadRepository imageUploadRepository;
    private final StorageService storageService;
    private final SectionValueImageScanner scanner;

    @Override
    public ImageUploadResponse upload(MultipartFile file, User uploader) {
        // S3 업로드(네트워크)는 트랜잭션 밖에서 수행한다.
        String objectKey = storageService.upload(
                file, KEY_PREFIX_FORMAT.formatted(uploader.getId()));

        // save()가 자체 트랜잭션으로 저장한다(SimpleJpaRepository).
        // 같은 클래스의 @Transactional 메서드를 직접 부르면 프록시를 타지 않으므로 감싸지 않는다.
        imageUploadRepository.save(ImageUpload.builder()
                .uploader(uploader)
                .objectKey(objectKey)
                .status(ImageStatus.PENDING)
                .build());

        return new ImageUploadResponse(objectKey, storageService.presignedGetUrl(objectKey));
    }

    @Override
    @Transactional
    public void link(Invitation invitation, Map<String, Object> sectionValues) {
        Set<String> keys = scanner.collectKeys(sectionValues);

        List<ImageUpload> found = keys.isEmpty()
                ? List.of()
                : imageUploadRepository.findAllByObjectKeyIn(keys);

        for (ImageUpload image : found) {
            // 남의 업로드 키를 붙이는 것은 명확한 권한 위반이다.
            if (!image.isUploadedBy(invitation.getUser().getId())) {
                throw new ImageException(ErrorCode.IMAGE_NOT_LINKABLE);
            }
            // 이미 다른 청첩장에 연결된 키는 재연결하지 않는다 — 원래 청첩장이 사진을 잃는다.
            if (image.isLinkedToOtherThan(invitation)) {
                throw new ImageException(ErrorCode.IMAGE_NOT_LINKABLE);
            }
            image.linkTo(invitation);
        }
        // 업로드 기록이 없는 키는 무시한다 — 프리픽스 스캔의 오탐으로 저장이 실패하면 안 된다.

        markRemovedAsOrphaned(invitation, keys);
    }

    private void markRemovedAsOrphaned(Invitation invitation, Set<String> keys) {
        imageUploadRepository.findAllByInvitation(invitation).stream()
                .filter(image -> !keys.contains(image.getObjectKey()))
                .forEach(ImageUpload::markOrphaned);
    }

    @Override
    @Transactional
    public void unlinkAll(Invitation invitation) {
        // 청첩장 삭제 전 FK 참조를 끊는다. 더티 체킹으로 반영되므로 별도 save 호출은 필요 없다.
        imageUploadRepository.findAllByInvitation(invitation)
                .forEach(ImageUpload::unlink);
    }

    @Override
    public Map<String, Object> withPresignedUrls(Map<String, Object> sectionValues) {
        return scanner.replaceKeys(sectionValues, storageService::presignedGetUrl);
    }
}
