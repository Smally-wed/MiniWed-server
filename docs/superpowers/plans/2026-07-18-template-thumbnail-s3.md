# Template Thumbnail S3 업로드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Template 생성 시 썸네일 이미지 파일을 서버가 받아 S3에 저장하고, DB에는 객체 키만 저장하며, 조회 응답마다 presigned GET URL을 생성해 내려준다.

**Architecture:** 서버 경유(multipart) 업로드. `StorageService`가 S3 업로드/presigned 발급을 캡슐화한다. `Template.thumbnail`에는 객체 키만 저장하고, `TemplateService`가 create/get 반환 직전에 객체 키 → presigned GET URL로 변환한다. Redis 캐시에는 객체 키가 담긴 `TemplateResponse`를 저장한다.

**Tech Stack:** Spring Boot 4.1.0, Java 25, AWS SDK v2 (`software.amazon.awssdk:s3`), JUnit5 + Mockito + AssertJ, Gradle.

## Global Constraints

- Java 25 / Spring Boot 4.1.0. 새 의존성은 `build.gradle`에 추가.
- AWS SDK v2 버전: **최신 2.x (예: `2.29.x`)** — 정확한 패치 버전은 mavenCentral 최신값으로 맞춘다(불확실, 확인 필요). BOM으로 관리한다.
- 에러는 `BusinessException` 계열 + `ErrorCode`로 던지고 기존 `GlobalExceptionHandler` 흐름을 탄다.
- 서비스 구현은 `interface + Impl` 패턴을 따른다(기존 `TemplateService`/`TemplateServiceImpl`처럼).
- 단위 테스트는 Mockito로 외부 의존성(S3Client/S3Presigner, StorageService)을 목킹한다. DB/Redis/네트워크 접속 없이 통과해야 한다.
- Redis 캐시(`TPL:` 키, 30일 TTL)에는 presigned URL을 저장하지 않는다. 캐시에는 객체 키만.
- 객체 키 구조: `templates/thumbnails/{uuid}.{ext}`.
- 커밋은 각 Task 끝에서 수행한다. 사용자가 별도로 막지 않는 한 로컬 커밋만 하고 push는 하지 않는다.

**주의(빌드 훅):** 이 저장소는 Edit/Write마다 `./gradlew build`를 실행하는 훅이 있고, 전체 빌드는 Postgres/Redis/`jwt.secret`을 요구한다. 훅이 실패로 보고해도 파일은 저장된다. 각 Task의 테스트 검증은 전체 build가 아니라 아래의 **targeted 테스트 명령**(`--tests`)으로 확인한다. targeted 단위 테스트는 DB/Redis 없이 통과하도록 설계되어 있다.

---

## File Structure

**신규 생성**
- `src/main/java/smally/server/core/config/S3Properties.java` — `@ConfigurationProperties(prefix="aws.s3")`, bucket/region/presignedTtlSeconds/maxSizeBytes 보관.
- `src/main/java/smally/server/core/config/S3Config.java` — `S3Client`, `S3Presigner` 빈.
- `src/main/java/smally/server/domain/image/service/StorageService.java` — 업로드/presigned 인터페이스.
- `src/main/java/smally/server/domain/image/service/S3StorageServiceImpl.java` — 구현체.
- `src/main/java/smally/server/core/exception/exceptions/ImageException.java` — 이미지 예외.
- `src/test/java/smally/server/domain/image/service/S3StorageServiceImplTest.java` — 스토리지 단위 테스트.

**수정**
- `build.gradle` — AWS SDK v2 BOM + s3 의존성.
- `src/main/resources/application.yml` — `aws.s3` 설정.
- `src/test/resources/application.yml` — 테스트용 `aws.s3` 더미 설정(컨텍스트 로딩 대비).
- `src/main/java/smally/server/core/exception/ErrorCode.java` — 이미지 에러 코드 3개.
- `src/main/java/smally/server/domain/template/dto/TemplateCreateRequest.java` — `thumbnail` 필드 제거, `toTemplate(String objectKey)`.
- `src/main/java/smally/server/domain/template/dto/TemplateResponse.java` — `withThumbnail(String)` 헬퍼 추가.
- `src/main/java/smally/server/domain/template/service/TemplateService.java` — `createTemplate` 시그니처에 `MultipartFile` 추가.
- `src/main/java/smally/server/domain/template/service/TemplateServiceImpl.java` — StorageService 배선, presigned 주입.
- `src/main/java/smally/server/domain/template/controller/TemplateController.java` — multipart 수신.
- `src/test/java/smally/server/domain/template/service/TemplateServiceImplTest.java` — 생성자/시그니처 변경 반영 + 신규 케이스.
- `src/test/java/smally/server/domain/template/dto/TemplateCreateRequestTest.java` — thumbnail 제거 반영.

---

## Task 1: AWS SDK 의존성 + S3 설정/빈

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application.yml`
- Create: `src/main/java/smally/server/core/config/S3Properties.java`
- Create: `src/main/java/smally/server/core/config/S3Config.java`

**Interfaces:**
- Produces:
  - `S3Properties` getters: `String getBucket()`, `String getRegion()`, `long getPresignedTtlSeconds()`, `long getMaxSizeBytes()`.
  - 빈: `software.amazon.awssdk.services.s3.S3Client`, `software.amazon.awssdk.services.s3.presigner.S3Presigner`.

- [ ] **Step 1: build.gradle에 AWS SDK v2 추가**

`dependencies { ... }` 블록에 다음을 추가한다(BOM으로 버전 관리). 버전은 최신 2.x로 맞춘다(불확실 시 mavenCentral 확인).

```gradle
    implementation platform('software.amazon.awssdk:bom:2.29.52')
    implementation 'software.amazon.awssdk:s3'
```

- [ ] **Step 2: application.yml에 aws.s3 설정 추가**

`src/main/resources/application.yml` 맨 아래에 추가한다. 자격증명은 AWS 기본 자격증명 체인(환경변수/IAM 역할)을 사용하므로 키를 yml에 넣지 않는다.

```yaml
aws:
  s3:
    bucket: ${S3_BUCKET}
    region: ${AWS_REGION:ap-northeast-2}
    presigned-ttl-seconds: 600        # presigned GET URL 만료(초) = 10분
    max-size-bytes: 5242880           # 썸네일 최대 크기 = 5MB
```

- [ ] **Step 3: 테스트용 application.yml에 더미 설정 추가**

`src/test/resources/application.yml`에 아래를 추가한다(컨텍스트 로딩 테스트가 있을 때 바인딩 실패 방지).

```yaml
aws:
  s3:
    bucket: test-bucket
    region: ap-northeast-2
    presigned-ttl-seconds: 600
    max-size-bytes: 5242880
```

- [ ] **Step 4: S3Properties 작성**

```java
package smally.server.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.s3")
public class S3Properties {

    private String bucket;
    private String region;
    private long presignedTtlSeconds;
    private long maxSizeBytes;

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public long getPresignedTtlSeconds() { return presignedTtlSeconds; }
    public void setPresignedTtlSeconds(long presignedTtlSeconds) { this.presignedTtlSeconds = presignedTtlSeconds; }

    public long getMaxSizeBytes() { return maxSizeBytes; }
    public void setMaxSizeBytes(long maxSizeBytes) { this.maxSizeBytes = maxSizeBytes; }
}
```

- [ ] **Step 5: S3Config 작성**

```java
package smally.server.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    private final S3Properties properties;

    public S3Config(S3Properties properties) {
        this.properties = properties;
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
```

- [ ] **Step 6: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (AWS SDK 클래스 해석됨).

- [ ] **Step 7: Commit**

```bash
git add build.gradle src/main/resources/application.yml src/test/resources/application.yml \
        src/main/java/smally/server/core/config/S3Properties.java \
        src/main/java/smally/server/core/config/S3Config.java
git commit -m "feat: AWS S3 SDK 의존성 및 S3Client/S3Presigner 설정 추가"
```

---

## Task 2: 에러 코드 + StorageService + S3StorageServiceImpl (TDD)

**Files:**
- Modify: `src/main/java/smally/server/core/exception/ErrorCode.java`
- Create: `src/main/java/smally/server/core/exception/exceptions/ImageException.java`
- Create: `src/main/java/smally/server/domain/image/service/StorageService.java`
- Create: `src/main/java/smally/server/domain/image/service/S3StorageServiceImpl.java`
- Test: `src/test/java/smally/server/domain/image/service/S3StorageServiceImplTest.java`

**Interfaces:**
- Consumes: `S3Properties`(Task 1), `S3Client`, `S3Presigner`.
- Produces:
  - `StorageService.upload(MultipartFile file, String keyPrefix) -> String objectKey`
  - `StorageService.presignedGetUrl(String objectKey) -> String url`
  - `ErrorCode.INVALID_IMAGE_TYPE`, `ErrorCode.IMAGE_TOO_LARGE`, `ErrorCode.IMAGE_UPLOAD_FAILED`
  - `ImageException(ErrorCode)`

- [ ] **Step 1: ErrorCode에 이미지 코드 추가**

`ErrorCode.java`의 마지막 enum 상수(`INVALID_TEMPLATE_THEME(...)`)의 세미콜론을 콤마로 바꾸고 아래 3개를 추가한다.

```java
    INVALID_TEMPLATE_THEME(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "theme 값이 옵션 정의의 허용값에 없습니다."),
    INVALID_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "이미지 파일만 업로드할 수 있습니다."),
    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "이미지 크기가 허용 범위를 초과했습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "이미지 업로드에 실패했습니다.");
```

- [ ] **Step 2: ImageException 작성**

```java
package smally.server.core.exception.exceptions;

import smally.server.core.exception.BusinessException;
import smally.server.core.exception.ErrorCode;

public class ImageException extends BusinessException {
    public ImageException(ErrorCode errorCode) {
        super(errorCode);
    }
}
```

- [ ] **Step 3: StorageService 인터페이스 작성**

```java
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
```

- [ ] **Step 4: 실패하는 테스트 작성**

`src/test/java/smally/server/domain/image/service/S3StorageServiceImplTest.java`:

```java
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
```

- [ ] **Step 5: 테스트 실패 확인**

Run: `./gradlew test --tests "smally.server.domain.image.service.S3StorageServiceImplTest"`
Expected: FAIL — `S3StorageServiceImpl` 클래스가 없어 컴파일 실패.

- [ ] **Step 6: S3StorageServiceImpl 구현**

```java
package smally.server.domain.image.service;

import java.io.IOException;
import java.time.Duration;
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
        validate(file);
        String objectKey = keyPrefix + UUID.randomUUID() + extension(file);
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

    private void validate(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ImageException(ErrorCode.INVALID_IMAGE_TYPE);
        }
        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new ImageException(ErrorCode.IMAGE_TOO_LARGE);
        }
    }

    private String extension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot).toLowerCase();
    }
}
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew test --tests "smally.server.domain.image.service.S3StorageServiceImplTest"`
Expected: PASS (4개 테스트).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/smally/server/core/exception/ErrorCode.java \
        src/main/java/smally/server/core/exception/exceptions/ImageException.java \
        src/main/java/smally/server/domain/image/service/StorageService.java \
        src/main/java/smally/server/domain/image/service/S3StorageServiceImpl.java \
        src/test/java/smally/server/domain/image/service/S3StorageServiceImplTest.java
git commit -m "feat: S3 이미지 업로드/presigned GET StorageService 추가"
```

---

## Task 3: DTO/응답 시그니처 변경 (TDD)

**Files:**
- Modify: `src/main/java/smally/server/domain/template/dto/TemplateCreateRequest.java`
- Modify: `src/main/java/smally/server/domain/template/dto/TemplateResponse.java`
- Modify: `src/test/java/smally/server/domain/template/dto/TemplateCreateRequestTest.java`

**Interfaces:**
- Produces:
  - `TemplateCreateRequest`: 필드 `name, category, sections, theme` (thumbnail 없음). `Template toTemplate(String objectKey)`.
  - `TemplateResponse`: `TemplateResponse withThumbnail(String thumbnail)` — thumbnail만 교체한 불변 복사본. objectKey가 null이면 호출측에서 그대로 null 유지.

- [ ] **Step 1: 기존 TemplateCreateRequestTest 확인 후 thumbnail 참조 제거**

`TemplateCreateRequestTest.java`를 열어 `new TemplateCreateRequest(...)` 생성자 호출에서 thumbnail 인자(2번째 String)를 제거하고, thumbnail을 검증하던 단언이 있으면 삭제한다. (record 필드가 4개로 바뀜에 맞춘다.)

- [ ] **Step 2: TemplateResponse.withThumbnail 실패 테스트 추가**

`src/test/java/smally/server/domain/template/dto/TemplateResponseTest.java`에 다음 테스트를 추가한다.

```java
    @Test
    void withThumbnail_thumbnail만_교체한_복사본을_만든다() {
        TemplateResponse origin = new TemplateResponse(
                "uid", "클래식", "templates/thumbnails/a.jpg", "클래식",
                java.util.List.of(), null);

        TemplateResponse replaced = origin.withThumbnail("https://signed/x");

        org.assertj.core.api.Assertions.assertThat(replaced.thumbnail()).isEqualTo("https://signed/x");
        org.assertj.core.api.Assertions.assertThat(replaced.name()).isEqualTo("클래식");
        org.assertj.core.api.Assertions.assertThat(replaced.templateUid()).isEqualTo("uid");
    }
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests "smally.server.domain.template.dto.TemplateResponseTest"`
Expected: FAIL — `withThumbnail` 메서드 없음(컴파일 실패).

- [ ] **Step 4: TemplateResponse에 withThumbnail 추가**

`TemplateResponse.java`에 메서드를 추가한다.

```java
    public TemplateResponse withThumbnail(String thumbnail) {
        return new TemplateResponse(templateUid, name, thumbnail, category, sections, theme);
    }
```

- [ ] **Step 5: TemplateCreateRequest에서 thumbnail 제거 + toTemplate 변경**

`TemplateCreateRequest.java`를 아래로 바꾼다.

```java
package smally.server.domain.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import smally.server.domain.template.entity.Template;

public record TemplateCreateRequest(
        @NotBlank(message = "템플릿 이름은 필수입니다.")
        String name,

        @NotBlank(message = "카테고리는 필수입니다.")
        String category,
        @NotNull(message = "섹션 구성은 필수입니다.")
        List<Map<String, Object>> sections,
        Map<String, Object> theme
) {
    public Template toTemplate(String thumbnailObjectKey) {
        return Template.builder()
                .name(name)
                .thumbnail(thumbnailObjectKey)
                .category(category)
                .sections(sections)
                .theme(theme)
                .build();
    }
}
```

- [ ] **Step 6: DTO 테스트 통과 확인**

Run: `./gradlew test --tests "smally.server.domain.template.dto.TemplateResponseTest" --tests "smally.server.domain.template.dto.TemplateCreateRequestTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/smally/server/domain/template/dto/TemplateCreateRequest.java \
        src/main/java/smally/server/domain/template/dto/TemplateResponse.java \
        src/test/java/smally/server/domain/template/dto/TemplateResponseTest.java \
        src/test/java/smally/server/domain/template/dto/TemplateCreateRequestTest.java
git commit -m "refactor: TemplateCreateRequest thumbnail 제거, TemplateResponse.withThumbnail 추가"
```

---

## Task 4: TemplateService 배선 (TDD)

**Files:**
- Modify: `src/main/java/smally/server/domain/template/service/TemplateService.java`
- Modify: `src/main/java/smally/server/domain/template/service/TemplateServiceImpl.java`
- Modify: `src/test/java/smally/server/domain/template/service/TemplateServiceImplTest.java`

**Interfaces:**
- Consumes: `StorageService`(Task 2), `TemplateCreateRequest.toTemplate(String)`(Task 3), `TemplateResponse.withThumbnail(String)`(Task 3).
- Produces:
  - `TemplateService.createTemplate(TemplateCreateRequest request, MultipartFile thumbnail) -> TemplateResponse`
  - `TemplateService.getTemplate(String templateUId) -> TemplateResponse` (반환 thumbnail은 presigned URL)

- [ ] **Step 1: 인터페이스 시그니처 변경**

`TemplateService.java`:

```java
package smally.server.domain.template.service;

import org.springframework.web.multipart.MultipartFile;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;

public interface TemplateService {
    TemplateResponse createTemplate(TemplateCreateRequest request, MultipartFile thumbnail);

    TemplateResponse getTemplate(String templateUId);
}
```

- [ ] **Step 2: 기존 테스트를 새 시그니처로 갱신**

`TemplateServiceImplTest.java`를 아래에 맞춰 수정한다.

1. `StorageService` 목 추가 및 생성자 인자 반영:

```java
    @Mock TemplateRepository templateRepository;
    @Mock CategoryService categoryService;
    @Mock ComponentService componentService;
    @Mock OptionDefinitionService optionDefinitionService;
    @Mock RedisCacheService redisCacheService;
    @Mock smally.server.domain.image.service.StorageService storageService;

    TemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TemplateServiceImpl(
                templateRepository, categoryService, componentService,
                optionDefinitionService, redisCacheService, storageService);
    }
```

2. `request(...)` 헬퍼를 thumbnail 없는 4-인자 생성자로 바꾸고, 더미 파일 헬퍼를 추가한다:

```java
    private TemplateCreateRequest request(List<Map<String, Object>> sections, Map<String, Object> theme) {
        return new TemplateCreateRequest("클래식", "클래식", sections, theme);
    }

    private org.springframework.web.multipart.MultipartFile thumb() {
        return new org.springframework.mock.web.MockMultipartFile(
                "thumbnail", "c.jpg", "image/jpeg", new byte[]{1});
    }
```

3. 기존 `createTemplate(request(...))` 호출을 모두 `createTemplate(request(...), thumb())`로 바꾼다. 검증 실패(테스트: 섹션 비어있음/componentUId 없음/컴포넌트 없음/theme 키·값)들에서는 업로드가 일어나지 않아야 하므로, 그 테스트들에는 storageService 스텁을 두지 않는다(호출 시 검증에서 먼저 예외).

4. "유효하면 저장한다" 테스트를 업로드·presigned 검증까지 확장한다:

```java
    @Test
    void createTemplate_유효하면_업로드하고_객체키_저장_후_presigned를_반환한다() {
        when(storageService.upload(any(), eq("templates/thumbnails/")))
                .thenReturn("templates/thumbnails/uuid.jpg");
        when(storageService.presignedGetUrl("templates/thumbnails/uuid.jpg"))
                .thenReturn("https://signed/x");

        TemplateResponse response = service.createTemplate(
                request(List.of(Map.of("componentUId", "GalleryGrid")), null), thumb());

        org.mockito.ArgumentCaptor<Template> captor = org.mockito.ArgumentCaptor.forClass(Template.class);
        verify(templateRepository).save(captor.capture());
        assertThat(captor.getValue().getThumbnail()).isEqualTo("templates/thumbnails/uuid.jpg");
        assertThat(response.thumbnail()).isEqualTo("https://signed/x");
    }
```

5. `getTemplate_캐시에_있으면_DB를_조회하지_않는다` 테스트를 presigned 주입까지 반영한다. 캐시된 응답의 thumbnail(객체 키)로 presigned를 생성해 반환하므로:

```java
    @Test
    void getTemplate_캐시에_있으면_DB를_조회하지_않고_presigned를_주입한다() {
        String uid = UUID.randomUUID().toString();
        TemplateResponse cached = new TemplateResponse(
                uid, "클래식", "templates/thumbnails/uuid.jpg", "클래식",
                List.of(Map.of("componentUId", "GalleryGrid")), null);
        when(redisCacheService.getCacheData("TPL:" + uid, TemplateResponse.class)).thenReturn(cached);
        when(storageService.presignedGetUrl("templates/thumbnails/uuid.jpg"))
                .thenReturn("https://signed/x");

        TemplateResponse response = service.getTemplate(uid);

        assertThat(response.thumbnail()).isEqualTo("https://signed/x");
        verifyNoInteractions(templateRepository);
    }
```

6. `getTemplate_캐시가_비면...` 테스트에서 `template`에 thumbnail이 null이므로, presigned가 호출되지 않고 캐시에는 객체 키(null) 응답이 적재됨을 반영한다. 기존 단언은 유지하되, 캐시 저장 검증은 presigned 주입 전 값(thumbnail=null)으로 이뤄져야 한다:

```java
    @Test
    void getTemplate_캐시가_비면_DB를_조회하고_키응답을_캐시에_적재한다() {
        UUID uid = UUID.randomUUID();
        Template template = Template.builder()
                .name("클래식").category("클래식")
                .sections(List.of(Map.of("componentUId", "GalleryGrid"))).build();
        when(templateRepository.findTemplateByTemplateUid(uid)).thenReturn(Optional.of(template));

        TemplateResponse response = service.getTemplate(uid.toString());

        assertThat(response.name()).isEqualTo("클래식");
        // thumbnail(null)일 때 presigned 미호출
        org.mockito.Mockito.verifyNoInteractions(storageService);
        // 캐시에는 객체 키(null) 응답이 적재됨
        verify(redisCacheService).setCacheData(eq("TPL:" + uid), any(TemplateResponse.class), any());
    }
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests "smally.server.domain.template.service.TemplateServiceImplTest"`
Expected: FAIL — `TemplateServiceImpl` 생성자/`createTemplate` 시그니처 불일치로 컴파일 실패.

- [ ] **Step 4: TemplateServiceImpl 구현**

`TemplateServiceImpl.java`를 아래로 수정한다(변경점: StorageService 필드/생성자 주입, createTemplate에 MultipartFile, 업로드→객체키 저장, create/get 반환에 presigned 주입 헬퍼).

```java
package smally.server.domain.template.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.TemplateException;
import smally.server.domain.component.service.ComponentService;
import smally.server.domain.image.service.StorageService;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.entity.OptionDefinition;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.repository.TemplateRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateServiceImpl implements TemplateService {

    private final static String TEMPLATE_CACHE_KEY = "TPL:";
    private final static Duration TEMPLATE_CACHE_TTL = Duration.ofDays(30);
    private final static String THUMBNAIL_KEY_PREFIX = "templates/thumbnails/";

    private final TemplateRepository templateRepository;
    private final CategoryService categoryService;
    private final ComponentService componentService;
    private final OptionDefinitionService optionDefinitionService;
    private final RedisCacheService redisCacheService;
    private final StorageService storageService;

    @Override
    @Transactional
    public TemplateResponse createTemplate(TemplateCreateRequest request, MultipartFile thumbnail) {
        categoryService.validateExists(request.category());
        validateRecipe(request.sections());
        validateTheme(request.theme());

        String objectKey = storageService.upload(thumbnail, THUMBNAIL_KEY_PREFIX);

        Template template = request.toTemplate(objectKey);
        templateRepository.save(template);

        TemplateResponse response = TemplateResponse.from(template);
        if (response.templateUid() != null) {
            redisCacheService.setCacheData(
                    TEMPLATE_CACHE_KEY + response.templateUid(), response, TEMPLATE_CACHE_TTL);
        }

        return withPresignedThumbnail(response);
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateResponse getTemplate(String templateUId) {
        String templateKey = TEMPLATE_CACHE_KEY + templateUId;
        TemplateResponse cached = redisCacheService.getCacheData(templateKey, TemplateResponse.class);

        if (cached != null) {
            return withPresignedThumbnail(cached);
        }
        log.warn("[Cache-miss] template detail cache miss key : {}", templateUId);
        TemplateResponse response = TemplateResponse.from(getTemplateEntity(templateUId));
        redisCacheService.setCacheData(templateKey, response, TEMPLATE_CACHE_TTL);

        return withPresignedThumbnail(response);
    }

    private TemplateResponse withPresignedThumbnail(TemplateResponse response) {
        String objectKey = response.thumbnail();
        if (objectKey == null || objectKey.isBlank()) {
            return response;
        }
        return response.withThumbnail(storageService.presignedGetUrl(objectKey));
    }

    private void validateRecipe(List<Map<String, Object>> sections) {
        if (sections == null || sections.isEmpty()) {
            throw new TemplateException(ErrorCode.INVALID_TEMPLATE_RECIPE);
        }

        for (Map<String, Object> section : sections) {
            Object rawUid = section.get("componentUId");
            if (!(rawUid instanceof String componentUid) || componentUid.isBlank()) {
                throw new TemplateException(ErrorCode.INVALID_TEMPLATE_RECIPE);
            }
            componentService.getComponent(componentUid);
        }
    }

    private void validateTheme(Map<String, Object> theme) {
        if (theme == null) {
            return;
        }

        theme.forEach((key, value) -> {
            OptionDefinition definition = optionDefinitionService.getOptionDefinition(key);
            List<Object> allowed = definition.getAllowedValues();
            if (allowed != null && !allowed.isEmpty() && !allowed.contains(value)) {
                throw new TemplateException(ErrorCode.INVALID_TEMPLATE_THEME);
            }
        });
    }

    private Template getTemplateEntity(String templateUId) {
        UUID uid;
        try {
            uid = UUID.fromString(templateUId);
        } catch (IllegalArgumentException e) {
            throw new TemplateException(ErrorCode.TEMPLATE_NOT_FOUND);
        }

        return templateRepository.findTemplateByTemplateUid(uid)
                .orElseThrow(() -> new TemplateException(ErrorCode.TEMPLATE_NOT_FOUND));
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "smally.server.domain.template.service.TemplateServiceImplTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/smally/server/domain/template/service/TemplateService.java \
        src/main/java/smally/server/domain/template/service/TemplateServiceImpl.java \
        src/test/java/smally/server/domain/template/service/TemplateServiceImplTest.java
git commit -m "feat: TemplateService 썸네일 업로드 및 조회 시 presigned 주입"
```

---

## Task 5: 컨트롤러 multipart 수신

**Files:**
- Modify: `src/main/java/smally/server/domain/template/controller/TemplateController.java`

**Interfaces:**
- Consumes: `TemplateService.createTemplate(TemplateCreateRequest, MultipartFile)`(Task 4).

- [ ] **Step 1: createTemplate를 multipart로 변경**

`TemplateController.java`의 `createTemplate`를 아래로 바꾼다. import에 `org.springframework.http.MediaType`, `org.springframework.web.multipart.MultipartFile` 추가.

```java
    @PostMapping(value = "/v1", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TemplateResponse>> createTemplate(
            @RequestPart("thumbnail") MultipartFile thumbnail,
            @RequestPart("request") @Valid TemplateCreateRequest templateCreateRequest
    ) {
        return ResponseEntity.ok(
                ApiResponse.of(
                        HttpStatus.CREATED,
                        templateService.createTemplate(templateCreateRequest, thumbnail)
                ));
    }
```

`getTemplate` 메서드는 그대로 둔다.

- [ ] **Step 2: 컴파일 및 전체 컴파일 확인**

Run: `./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 관련 단위 테스트 일괄 확인**

Run: `./gradlew test --tests "smally.server.domain.image.*" --tests "smally.server.domain.template.*"`
Expected: PASS (스토리지/DTO/서비스 테스트 모두 통과).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/smally/server/domain/template/controller/TemplateController.java
git commit -m "feat: 템플릿 생성 API multipart(썸네일 파일) 수신"
```

---

## 검증 (수동)

실제 S3 연동은 자격증명/버킷이 있는 환경에서 확인한다.

- `S3_BUCKET`, `AWS_REGION`, AWS 자격증명(환경변수/IAM)이 설정된 상태로 앱 기동.
- `POST /api/template/v1` 를 `multipart/form-data`로 호출:
  - part `thumbnail`: 이미지 파일
  - part `request`: `application/json`으로 `{ "name", "category", "sections", "theme" }`
- 응답 `thumbnail`이 presigned GET URL(서명 쿼리 포함)인지, 그 URL로 이미지가 열리는지 확인.
- `GET /api/template/v1/{uid}` 응답 `thumbnail`이 매 호출마다 유효한 presigned URL인지 확인.
- S3 콘솔에서 `templates/thumbnails/{uuid}.{ext}` 객체 존재 확인.

---

## Self-Review 결과

- **스펙 커버리지:** 업로드(서버 경유·Task 2,5) / 객체 키 저장(Task 3,4) / 조회 presigned(Task 4) / 캐시 정합성(Task 4, 캐시엔 키·반환 시 URL) / 에러 처리(Task 2) / 설정(Task 1) / 테스트(각 Task) — 스펙 항목 모두 대응됨.
- **범위 밖 준수:** ImageUpload 고아 추적·CloudFront 미포함(설계와 일치).
- **타입 일관성:** `upload(MultipartFile,String)→String`, `presignedGetUrl(String)→String`, `toTemplate(String)`, `withThumbnail(String)`, `createTemplate(TemplateCreateRequest,MultipartFile)` — Task 간 시그니처 일치 확인.
- **불확실 항목:** AWS SDK BOM 정확한 패치 버전(2.29.52)은 mavenCentral 최신값으로 조정 필요(Global Constraints에 명시).
