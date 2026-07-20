package smally.server.domain.image.service;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import smally.server.core.config.S3Properties;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ImageException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Service
public class S3StorageServiceImpl implements StorageService {

    // 허용 content-type → 저장 확장자. 파일명이 아닌 content-type을 신뢰 기준으로 사용한다.
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    public S3StorageServiceImpl(S3Client s3Client, S3Presigner s3Presigner, S3Properties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @Override
    public String upload(MultipartFile file, String keyPrefix) {
        String extension = validate(file);
        String objectKey = keyPrefix + UUID.randomUUID() + extension;
        try {
            s3Client.putObject(
                    builder -> builder.bucket(properties.getBucket())
                            .key(objectKey)
                            .contentType(file.getContentType()),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | SdkException e) {
            throw new ImageException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
        return objectKey;
    }

    @Override
    public String presignedGetUrl(String objectKey) {
        return s3Presigner.presignGetObject(builder -> builder
                        .signatureDuration(Duration.ofSeconds(properties.getPresignedTtlSeconds()))
                        .getObjectRequest(get -> get.bucket(properties.getBucket()).key(objectKey)))
                .url()
                .toString();
    }

    /**
     * content-type을 허용목록(jpg/jpeg=image/jpeg, png=image/png)으로 검증하고
     * 저장에 쓸 확장자를 반환한다. 허용되지 않으면 INVALID_IMAGE_TYPE.
     */
    private String validate(MultipartFile file) {
        String contentType = file.getContentType();
        String extension = contentType == null ? null : ALLOWED_CONTENT_TYPES.get(contentType);
        if (extension == null) {
            throw new ImageException(ErrorCode.INVALID_IMAGE_TYPE);
        }
        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new ImageException(ErrorCode.IMAGE_TOO_LARGE);
        }
        return extension;
    }
}
