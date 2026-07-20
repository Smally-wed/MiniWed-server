package smally.server.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import smally.server.core.config.S3Properties;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ImageException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

class S3StorageServiceImplTest {

    S3Client s3Client;
    S3Presigner s3Presigner;
    S3StorageServiceImpl service;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        s3Presigner = mock(S3Presigner.class);
        S3Properties props = new S3Properties();
        props.setBucket("test-bucket");
        props.setRegion("ap-northeast-2");
        props.setPresignedTtlSeconds(600);
        props.setMaxSizeBytes(5 * 1024 * 1024);
        service = new S3StorageServiceImpl(s3Client, s3Presigner, props);
    }

    private MockMultipartFile image(String name, String contentType, int size) {
        return new MockMultipartFile("thumbnail", name, contentType, new byte[size]);
    }

    @Test
    void upload_정상_이미지면_prefix로_시작하는_객체키를_반환한다() {
        MockMultipartFile file = image("cover.jpg", "image/jpeg", 1024);

        String key = service.upload(file, "templates/thumbnails/");

        assertThat(key).startsWith("templates/thumbnails/");
        assertThat(key).endsWith(".jpg");
    }

    @Test
    void upload_content_type이_이미지가_아니면_INVALID_IMAGE_TYPE() {
        MockMultipartFile file = image("a.pdf", "application/pdf", 1024);

        assertThatThrownBy(() -> service.upload(file, "templates/thumbnails/"))
                .isInstanceOf(ImageException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_TYPE);
    }

    @Test
    void upload_허용목록에_없는_이미지타입이면_INVALID_IMAGE_TYPE() {
        MockMultipartFile file = image("anim.gif", "image/gif", 1024);

        assertThatThrownBy(() -> service.upload(file, "templates/thumbnails/"))
                .isInstanceOf(ImageException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_TYPE);
    }

    @Test
    void upload_최대크기를_초과하면_IMAGE_TOO_LARGE() {
        MockMultipartFile file = image("big.png", "image/png", 6 * 1024 * 1024);

        assertThatThrownBy(() -> service.upload(file, "templates/thumbnails/"))
                .isInstanceOf(ImageException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_TOO_LARGE);
    }

    @Test
    void presignedGetUrl_객체키로_URL을_생성한다() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://signed.example/obj").toURL());
        when(s3Presigner.presignGetObject(any(java.util.function.Consumer.class)))
                .thenReturn(presigned);

        String url = service.presignedGetUrl("templates/thumbnails/abc.jpg");

        assertThat(url).isEqualTo("https://signed.example/obj");
    }
}
