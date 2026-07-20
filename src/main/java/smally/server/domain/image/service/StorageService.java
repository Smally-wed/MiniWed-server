package smally.server.domain.image.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /**
     * 이미지 파일을 S3에 업로드하고 저장된 객체 키를 반환한다.
     * @param keyPrefix 예: "templates/thumbnails/"
     */
    String upload(MultipartFile file, String keyPrefix);

    /**
     * 객체 키로부터 presigned GET URL을 생성해 반환한다.
     */
    String presignedGetUrl(String objectKey);
}
