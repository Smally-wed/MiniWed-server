# 설계: Template thumbnail S3 업로드 + presigned GET 조회

- 작성일: 2026-07-18
- 상태: 승인됨 (구현 대기)
- 관련 ADR: ADR-001(사용자 사진 = presigned 직접 업로드), ADR-008(신규 작성 예정)

## 목적

Template 생성 시 썸네일 이미지 파일을 서버가 받아 AWS S3에 저장하고, DB에는 S3 객체 키만
보관한다. 템플릿 조회 응답을 만들 때마다 객체 키로부터 presigned GET URL을 생성해 내려준다.

## 배경 / 현재 상태

- `Template.thumbnail`(String)은 현재 클라이언트가 넘긴 값을 그대로 저장만 한다.
- 프로젝트에 S3 관련 코드(AWS SDK 의존성, S3 클라이언트, 설정)는 아직 전혀 없다.
- ADR-001은 **사용자(신랑·신부) 사진**을 대상으로 presigned URL 기반 클라이언트→S3 직접
  업로드를 결정했다. 템플릿 썸네일은 성격이 다르다(관리자가 템플릿당 1장, 저용량·저빈도)라서
  서버 경유 업로드를 채택한다. 이 차이는 ADR-008로 별도 기록한다.

## 결정 사항

### 1. 업로드 방식: 서버 경유(multipart)

컨트롤러가 `MultipartFile`을 받아 서버가 S3에 PutObject 한다. 서버가 바이너리를 중계한다.

### 2. 저장 형태: 객체 키만 DB에 저장

- DB `templates.thumbnail` 컬럼에는 S3 **객체 키**만 저장한다.
- 객체 키 구조: `templates/thumbnails/{uuid}.{ext}` (ext는 원본 확장자 또는 content-type 기반).

### 3. 조회: 요청마다 presigned GET URL 생성

- 응답을 만들 때 객체 키 → presigned GET URL로 변환해 내려준다.
- 만료되는 URL은 DB에도, Redis 캐시에도 저장하지 않는다.

## API 변경

`POST /api/template/v1` 을 `multipart/form-data`로 변경한다.

```
@PostMapping(value = "/v1", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
createTemplate(
    @RequestPart("thumbnail") MultipartFile thumbnail,
    @RequestPart("request") @Valid TemplateCreateRequest request
)
```

- `TemplateCreateRequest`에서 `String thumbnail` 필드를 제거한다(이제 파일로 받는다).
- `toTemplate()`을 `toTemplate(String objectKey)`로 변경해 서비스가 생성한 객체 키를 주입한다.

## 컴포넌트 구성

| 파일 | 역할 |
|---|---|
| `build.gradle` | AWS SDK v2 BOM + `software.amazon.awssdk:s3` 의존성 추가 |
| `application.yml` | `aws.s3` 설정(region, bucket, presigned-ttl, max-size). 자격증명은 환경변수/기본 자격증명 체인 |
| `core/config/S3Config` | `S3Client`, `S3Presigner` 빈 정의 |
| `core/config/S3Properties` | `@ConfigurationProperties(prefix = "aws.s3")` — bucket, region, presignedTtl, maxSize |
| `domain/image/service/StorageService` (interface) | `String upload(MultipartFile file, String keyPrefix)`, `String presignedGetUrl(String objectKey)` |
| `domain/image/service/S3StorageServiceImpl` | 검증 → 객체 키 생성 → PutObject → presigned URL 생성 |
| `core/exception/ErrorCode` | `INVALID_IMAGE_TYPE`, `IMAGE_TOO_LARGE`, `IMAGE_UPLOAD_FAILED` 추가 |
| `core/exception/exceptions/ImageException` | `BusinessException` 상속 |

## 데이터 흐름

### 생성 (create)
1. 클라이언트 → `POST /api/template/v1` (multipart: `thumbnail` 파일 + `request` JSON)
2. 컨트롤러 → `templateService.createTemplate(request, thumbnailFile)`
3. 서비스:
   - `category` / `sections`(recipe) / `theme` 검증 (기존 로직 유지)
   - `objectKey = storageService.upload(thumbnailFile, "templates/thumbnails/")`
   - `template = request.toTemplate(objectKey)`; `templateRepository.save(template)`
   - `cached = TemplateResponse.from(template)` — thumbnail = objectKey
   - Redis에 `cached` 저장 (기존 30일 TTL)
   - 반환: `cached.withThumbnail(storageService.presignedGetUrl(objectKey))`

### 조회 (get)
1. 캐시 또는 DB에서 `TemplateResponse`(thumbnail = objectKey) 확보 (기존 로직 유지)
2. 반환 직전 `response.withThumbnail(storageService.presignedGetUrl(objectKey))`로 URL 주입

## 캐시 정합성 (핵심)

- `TemplateResponse`는 Redis에 30일 캐싱된다. presigned URL은 수명이 짧아 캐시에 넣으면
  만료된다.
- 따라서 **캐시에는 객체 키가 담긴 `TemplateResponse`를 저장**하고, 컨트롤러로 반환하기
  직전에 presigned URL을 새로 채운다.
- `TemplateResponse`(record)에 `withThumbnail(String thumbnail)` 헬퍼를 추가해 불변 복사본을
  만든다.
- 객체 키가 `null`인 경우(썸네일 미존재)에는 presigned URL 생성을 건너뛰고 `null`을 유지한다.

## 에러 처리

- content-type이 `image/`로 시작하지 않으면 `INVALID_IMAGE_TYPE`(400).
- 파일 크기가 설정 `aws.s3.max-size` 초과 시 `IMAGE_TOO_LARGE`(400).
- S3 `SdkException` 등 업로드 실패 시 `IMAGE_UPLOAD_FAILED`(500).
- 모두 기존 `GlobalExceptionHandler` → `ErrorResponse` 흐름을 따른다.

## 범위 밖 (YAGNI)

- 템플릿 썸네일은 관리자·저용량·동기 업로드라 ADR-001의 `ImageUpload` 고아 추적/S3 lifecycle은
  연결하지 않는다. 템플릿 저장 실패 시 S3 객체가 남을 수 있으나 저위험이며 ADR-008에
  트레이드오프로 기록한다.
- CloudFront 도메인 연동은 이번 범위에 포함하지 않는다. 현재는 presigned GET로 서빙한다.
- 업로드 후 이미지 실검증(디코드/리사이즈), 썸네일 재생성은 포함하지 않는다.

## 테스트

- `S3StorageServiceImpl` (S3Client/S3Presigner 목킹):
  - 정상 이미지 업로드 시 기대한 prefix의 객체 키 반환
  - content-type이 이미지가 아니면 `ImageException(INVALID_IMAGE_TYPE)`
  - 최대 크기 초과 시 `ImageException(IMAGE_TOO_LARGE)`
  - `presignedGetUrl`이 객체 키로 URL 생성
- `TemplateServiceImpl` (StorageService 목킹):
  - 생성 시 객체 키가 `thumbnail`에 저장되고 응답에는 presigned URL이 채워짐
  - 조회 시 캐시된 키로부터 presigned URL이 채워짐

## 후속 (구현 순서 개요)

1. ADR-008 작성 후 사용자 검토(adr-workflow).
2. 의존성/설정/S3 빈.
3. StorageService + 구현체 + 에러 코드.
4. DTO/엔티티 시그니처 변경, 컨트롤러 multipart화, 서비스 배선.
5. 테스트.
