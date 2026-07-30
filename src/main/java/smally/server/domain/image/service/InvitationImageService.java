package smally.server.domain.image.service;

import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import smally.server.domain.image.dto.ImageUploadResponse;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.user.entity.User;

public interface InvitationImageService {

    /** S3에 올리고 PENDING 기록을 남긴다. 반환 URL은 업로드 직후 미리보기용이다. */
    ImageUploadResponse upload(MultipartFile file, User uploader);

    /** sectionValues에 등장하는 이미지를 청첩장에 연결하고, 빠진 것은 고아로 표시한다. */
    void link(Invitation invitation, Map<String, Object> sectionValues);

    /** 청첩장 삭제 전, 연결된 모든 이미지의 참조를 끊어 FK 위반을 막는다. */
    void unlinkAll(Invitation invitation);

    /** 조회 응답용으로 이미지 키를 presigned GET URL로 바꾼 사본을 만든다. */
    Map<String, Object> withPresignedUrls(Map<String, Object> sectionValues);
}
