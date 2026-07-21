# 청첩장 저장 (Invitation Persistence) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 템플릿을 골라 청첩장을 만들고, 임시저장하며, 사진을 올리고, 발행해 하객이 무인증으로 열람할 수 있게 한다.

**Architecture:** `Invitation`은 `sectionId`로 키잉된 두 개의 jsonb(`sectionValues`, `selectedOptions`)를 갖는다. 검증은 `InvitationValidator`가 전담하며, 임시저장(DRAFT)에서는 구조·권한만, 발행 시에는 컴포넌트 `dataSchema` 완결성까지 검사한다. 사진은 서버 경유로 S3에 올리고 DB에는 객체 키만 두며, 저장 시 `sectionValues`를 재귀 순회해 키를 수집·연결한다.

**Tech Stack:** Spring Boot 4.1.0, Java 25, JPA/Hibernate(`@JdbcTypeCode(SqlTypes.JSON)`), PostgreSQL jsonb, Redis(템플릿 캐시), AWS S3 SDK v2, networknt json-schema-validator, JUnit 5

## Global Constraints

- 근거 문서: [ADR-009](../../adr/ADR-009-invitation-persistence-model.md), [설계 스펙](../specs/2026-07-21-invitation-persistence-design.md). 충돌 시 스펙이 우선하며, 스펙을 벗어나는 결정은 사용자에게 먼저 묻는다.
- 엔티티는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + `@Builder(private 생성자)` 패턴을 따른다. 세터를 쓰지 않고 도메인 메서드로 상태를 바꾼다.
- 컨트롤러 응답은 `ApiResponse.of(HttpStatus, body)`로 감싼다. 경로는 `/api/{도메인}/v1` 관례를 따른다.
- 예외는 `BusinessException` 하위 클래스 + `ErrorCode`로만 던진다. 컨트롤러에서 try/catch 하지 않는다(`GlobalExceptionHandler`가 처리).
- S3 업로드 같은 네트워크 호출은 트랜잭션 밖에서 수행한다.
- 각 Task 끝에서 커밋한다. 커밋 메시지는 기존 관례를 따른다: `feat : `, `fix : `, `refactor : `, `test : ` (콜론 앞뒤 공백).
- **빌드 훅:** 모든 파일 수정 후 `./gradlew build`가 자동 실행되며 PostgreSQL·Redis·`jwt.secret`이 필요하다. 훅이 실패를 보고해도 파일은 그대로 저장되어 있다. 훅이 막으면 우회하지 말고 사용자에게 이유를 설명한다.
- 이번 범위에 **RSVP·방명록·낙관적 잠금·페이징·ORPHANED 이미지 실삭제는 포함하지 않는다.**

---

## File Structure

**신규 생성**

| 파일 | 책임 |
|---|---|
| `domain/invitation/dto/InvitationCreateRequest.java` | 생성 요청 |
| `domain/invitation/dto/InvitationUpdateRequest.java` | 저장 요청 |
| `domain/invitation/dto/InvitationResponse.java` | 편집용 응답 |
| `domain/invitation/dto/InvitationSummaryResponse.java` | 목록 응답 |
| `domain/invitation/dto/PublicInvitationResponse.java` | 하객 공개 응답 |
| `domain/invitation/service/InvitationService(.Impl).java` | 생성·조회·저장·발행·삭제 |
| `domain/invitation/service/PublicInvitationService(.Impl).java` | 하객 공개 조회 (인증 경계가 달라 분리) |
| `domain/invitation/service/InvitationValidator.java` | 검증 파이프라인 전담 |
| `domain/invitation/service/SlugGenerator.java` | 추측 불가 slug 생성 |
| `domain/invitation/controller/InvitationController.java` | 소유자 API |
| `domain/invitation/controller/PublicInvitationController.java` | 무인증 API |
| `core/exception/exceptions/InvitationException.java` | 청첩장 예외 |
| `core/security/CurrentUser.java` | principal에서 userId 추출 |
| `domain/image/util/SectionValueImageScanner.java` | jsonb 재귀 순회(키 수집 + URL 치환) |
| `domain/image/service/InvitationImageService(.Impl).java` | 업로드 기록·연결·고아 처리 |
| `domain/image/controller/InvitationImageController.java` | 사진 업로드 API |

**수정**

| 파일 | 변경 |
|---|---|
| `domain/invitation/entity/Invitation.java` | `invitationUid`, `selectedOptions` 추가, 도메인 메서드 |
| `domain/invitation/repository/InvitationRepository.java` | 조회 메서드 추가 |
| `domain/image/entity/ImageUpload.java` | `uploader` 추가 |
| `domain/image/enums/ImageStatus.java` | `ORPHANED` 추가 |
| `domain/image/repository/ImageUploadRepository.java` | 조회 메서드 추가 |
| `core/exception/ErrorCode.java` | 에러코드 8개 추가 |
| `domain/component/service/ComponentServiceImpl.java` | `validateComponentJsontData` null 가드 |
| `domain/template/service/TemplateServiceImpl.java` | `sectionId` 필수·중복 검증 |
| `config/SecurityConfig.java` | 청첩장 경로 인가 규칙 |

**Task 순서:** 1~4는 의존성 없는 기반, 5~7은 검증·이미지, 8~10은 API. 각 Task는 독립적으로 테스트 가능하다.

---

### Task 1: `Invitation` 엔티티 재설계 + 에러코드

**Files:**
- Modify: `src/main/java/smally/server/domain/invitation/entity/Invitation.java`
- Modify: `src/main/java/smally/server/domain/invitation/repository/InvitationRepository.java`
- Modify: `src/main/java/smally/server/core/exception/ErrorCode.java`
- Create: `src/main/java/smally/server/core/exception/exceptions/InvitationException.java`
- Test: `src/test/java/smally/server/domain/invitation/entity/InvitationTest.java`

**Interfaces:**
- Consumes: 없음 (기반 Task)
- Produces:
  - `Invitation.builder().user(User).template(Template).sectionValues(Map).selectedOptions(Map).build()`
  - `void updateContent(Map<String,Object> sectionValues, Map<String,Object> selectedOptions)`
  - `void publish(String slug)` / `void unpublish()` / `boolean isOwnedBy(Long userId)`
  - `UUID getInvitationUid()`, `Map<String,Object> getSelectedOptions()`
  - `InvitationRepository.findByInvitationUid(UUID)`, `findBySlugAndStatus(String, InvitationStatus)`, `findAllByUserIdOrderByUpdatedAtDesc(Long)`, `existsBySlug(String)`
  - `ErrorCode.UNAUTHENTICATED_REQUIRED / INVITATION_NOT_FOUND / INVITATION_ACCESS_DENIED / INVITATION_ALREADY_PUBLISHED / UNKNOWN_SECTION_ID / INVALID_INVITATION_OPTIONS / IMAGE_NOT_LINKABLE / SLUG_GENERATION_FAILED`
  - `InvitationException(ErrorCode)`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/smally/server/domain/invitation/entity/InvitationTest.java`:

```java
package smally.server.domain.invitation.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import smally.server.domain.invitation.enums.InvitationStatus;
import smally.server.domain.template.entity.Template;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.enums.UserRole;

class InvitationTest {

    private User user(Long id) {
        User user = User.builder()
                .email("a@b.com").userRole(UserRole.USER).nickname("신랑").build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Template template() {
        return Template.builder().name("t").category("모던")
                .sections(java.util.List.of(Map.of("sectionId", "cover", "componentUId", "CoverBasic")))
                .build();
    }

    private Invitation invitation(Long userId) {
        return Invitation.builder().user(user(userId)).template(template()).build();
    }

    @Test
    void 생성_직후에는_DRAFT이고_slug와_발행시각이_없다() {
        Invitation invitation = invitation(7L);

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DRAFT);
        assertThat(invitation.getSlug()).isNull();
        assertThat(invitation.getPublishedAt()).isNull();
    }

    @Test
    void 내용을_저장하면_두_jsonb가_통째로_교체된다() {
        Invitation invitation = invitation(7L);

        invitation.updateContent(
                Map.of("cover", Map.of("groomName", "철수")),
                Map.of("gallery-1", Map.of("columns", 3)));

        assertThat(invitation.getSectionValues()).containsKey("cover");
        assertThat(invitation.getSelectedOptions()).containsKey("gallery-1");
    }

    @Test
    void 발행하면_slug와_발행시각이_채워진다() {
        Invitation invitation = invitation(7L);

        invitation.publish("abc123");

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PUBLISHED);
        assertThat(invitation.getSlug()).isEqualTo("abc123");
        assertThat(invitation.getPublishedAt()).isNotNull();
    }

    /** 이미 공유된 링크를 다른 청첩장이 넘겨받지 않도록 slug는 회수하지 않는다. */
    @Test
    void 발행을_취소해도_slug는_유지된다() {
        Invitation invitation = invitation(7L);
        invitation.publish("abc123");

        invitation.unpublish();

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DRAFT);
        assertThat(invitation.getPublishedAt()).isNull();
        assertThat(invitation.getSlug()).isEqualTo("abc123");
    }

    @Test
    void 소유자_판별은_사용자_id로_한다() {
        Invitation invitation = invitation(7L);

        assertThat(invitation.isOwnedBy(7L)).isTrue();
        assertThat(invitation.isOwnedBy(8L)).isFalse();
        assertThat(invitation.isOwnedBy(null)).isFalse();
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "smally.server.domain.invitation.entity.InvitationTest"`
Expected: 컴파일 실패 — `updateContent`, `publish`, `unpublish`, `isOwnedBy`, `getSelectedOptions` 심볼을 찾을 수 없음

- [ ] **Step 3: 엔티티 구현**

`Invitation.java`에서 `sectionValues` 필드 아래에 추가하고, 생성자·메서드를 수정한다.

```java
    @Column(nullable = false, unique = true, updatable = false, columnDefinition = "UUID")
    private UUID invitationUid;

    // 사용자가 고른 섹션별 옵션값. {sectionId → {optionKey → 값}} (ADR-009)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_options")
    private Map<String, Object> selectedOptions;

    @Builder
    private Invitation(User user, Template template,
                       Map<String, Object> sectionValues, Map<String, Object> selectedOptions) {
        this.user = user;
        this.template = template;
        this.sectionValues = sectionValues;
        this.selectedOptions = selectedOptions;
        this.status = InvitationStatus.DRAFT;
    }

    @PrePersist
    protected void onCreate() {
        if (this.invitationUid == null) {
            this.invitationUid = UuidCreator.getTimeOrderedEpoch();
        }
    }

    /** 편집기는 항상 전체 상태를 보내므로 부분 병합이 아닌 통째 교체다(스펙 §4.2). */
    public void updateContent(Map<String, Object> sectionValues, Map<String, Object> selectedOptions) {
        this.sectionValues = sectionValues;
        this.selectedOptions = selectedOptions;
    }

    public void publish(String slug) {
        this.slug = slug;
        this.status = InvitationStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    /** slug는 회수하지 않는다 — 이미 공유된 링크를 다른 청첩장이 넘겨받는 것을 막는다. */
    public void unpublish() {
        this.status = InvitationStatus.DRAFT;
        this.publishedAt = null;
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && user != null && userId.equals(user.getId());
    }
```

import 추가: `com.github.f4b6a3.uuid.UuidCreator`, `jakarta.persistence.PrePersist`, `java.util.UUID`.
`@Table`의 `uniqueConstraints`에 `@UniqueConstraint(name = "uk_invitation_uid", columnNames = "invitation_uid")`를 더한다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "smally.server.domain.invitation.entity.InvitationTest"`
Expected: 5개 테스트 PASS

- [ ] **Step 5: 리포지토리 메서드 추가**

`InvitationRepository.java`:

```java
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByInvitationUid(UUID invitationUid);

    Optional<Invitation> findBySlugAndStatus(String slug, InvitationStatus status);

    List<Invitation> findAllByUserIdOrderByUpdatedAtDesc(Long userId);

    boolean existsBySlug(String slug);
}
```

> 주의: 과거에 쿼리 메서드 이름 오타로 장애가 있었다(`docs/TROUBLE-component-repository-query-method-typo.md`).
> 프로퍼티 이름(`invitationUid`, `slug`, `status`, `user.id`, `updatedAt`)과 정확히 일치하는지 확인한다.

- [ ] **Step 6: 에러코드·예외 추가**

`ErrorCode.java`의 `IMAGE_UPLOAD_FAILED` 앞에 추가(마지막 항목의 세미콜론 위치 유지):

```java
    UNAUTHENTICATED_REQUIRED(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다."),
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "청첩장을 찾을 수 없습니다."),
    INVITATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "FORBIDDEN", "해당 청첩장에 대한 권한이 없습니다."),
    INVITATION_ALREADY_PUBLISHED(HttpStatus.CONFLICT, "CONFLICT", "이미 발행된 청첩장입니다."),
    UNKNOWN_SECTION_ID(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "템플릿에 없는 섹션입니다."),
    INVALID_INVITATION_OPTIONS(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "수정할 수 없거나 허용되지 않은 옵션 값입니다."),
    IMAGE_NOT_LINKABLE(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "연결할 수 없는 이미지입니다."),
    SLUG_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "공개 주소 생성에 실패했습니다."),
```

`InvitationException.java`:

```java
package smally.server.core.exception.exceptions;

import smally.server.core.exception.BusinessException;
import smally.server.core.exception.ErrorCode;

public class InvitationException extends BusinessException {
    public InvitationException(ErrorCode errorCode) {
        super(errorCode);
    }
}
```

- [ ] **Step 7: 전체 빌드 확인 후 커밋**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

```bash
git add src/main/java/smally/server/domain/invitation src/main/java/smally/server/core/exception src/test/java/smally/server/domain/invitation
git commit -m "feat : 청첩장 엔티티에 invitationUid와 selectedOptions 추가"
```

---

### Task 2: `SlugGenerator`

**Files:**
- Create: `src/main/java/smally/server/domain/invitation/service/SlugGenerator.java`
- Test: `src/test/java/smally/server/domain/invitation/service/SlugGeneratorTest.java`

**Interfaces:**
- Consumes: `InvitationRepository.existsBySlug(String)` (Task 1), `ErrorCode.SLUG_GENERATION_FAILED`, `InvitationException` (Task 1)
- Produces: `String SlugGenerator.generate()` — 22자 URL-safe 문자열, 중복이면 재시도

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/smally/server/domain/invitation/service/SlugGeneratorTest.java`:

```java
package smally.server.domain.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.domain.invitation.repository.InvitationRepository;

class SlugGeneratorTest {

    private final InvitationRepository repository = mock(InvitationRepository.class);
    private final SlugGenerator generator = new SlugGenerator(repository);

    @Test
    void slug는_22자_URL_안전_문자열이다() {
        when(repository.existsBySlug(anyString())).thenReturn(false);

        String slug = generator.generate();

        assertThat(slug).hasSize(22).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void 매번_다른_값을_만든다() {
        when(repository.existsBySlug(anyString())).thenReturn(false);

        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }

    @Test
    void 이미_쓰이는_slug면_다시_만든다() {
        when(repository.existsBySlug(anyString()))
                .thenReturn(true)
                .thenReturn(false);

        assertThat(generator.generate()).isNotNull();
    }

    @Test
    void 재시도_한도를_넘으면_예외를_던진다() {
        when(repository.existsBySlug(anyString())).thenReturn(true);

        assertThatThrownBy(generator::generate)
                .isInstanceOf(InvitationException.class);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "smally.server.domain.invitation.service.SlugGeneratorTest"`
Expected: 컴파일 실패 — `SlugGenerator` 클래스 없음

- [ ] **Step 3: 구현**

```java
package smally.server.domain.invitation.service;

import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.domain.invitation.repository.InvitationRepository;

/**
 * 하객 공개 URL에 쓰는 추측 불가 slug를 만든다.
 * 128비트 난수를 패딩 없는 URL-safe Base64로 인코딩해 22자가 된다.
 */
@Component
@RequiredArgsConstructor
public class SlugGenerator {

    private static final int RANDOM_BYTES = 16;
    private static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final InvitationRepository invitationRepository;

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String slug = randomSlug();
            if (!invitationRepository.existsBySlug(slug)) {
                return slug;
            }
        }
        throw new InvitationException(ErrorCode.SLUG_GENERATION_FAILED);
    }

    private String randomSlug() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "smally.server.domain.invitation.service.SlugGeneratorTest"`
Expected: 4개 테스트 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/domain/invitation/service/SlugGenerator.java src/test/java/smally/server/domain/invitation/service/SlugGeneratorTest.java
git commit -m "feat : 청첩장 공개 URL용 slug 생성기 작성"
```

---

### Task 3: `SectionValueImageScanner`

`sectionValues` jsonb를 재귀 순회해 이미지 키를 수집하고, 조회 시 presigned URL로 치환한다.
수집과 치환이 같은 순회 규칙을 쓰므로 한 클래스에 둔다.

**Files:**
- Create: `src/main/java/smally/server/domain/image/util/SectionValueImageScanner.java`
- Test: `src/test/java/smally/server/domain/image/util/SectionValueImageScannerTest.java`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `Set<String> SectionValueImageScanner.collectKeys(Object node)`
  - `Map<String,Object> SectionValueImageScanner.replaceKeys(Map<String,Object> sectionValues, UnaryOperator<String> resolver)`
  - `static final String IMAGE_KEY_PREFIX = "invitations/"`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/smally/server/domain/image/util/SectionValueImageScannerTest.java`:

```java
package smally.server.domain.image.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SectionValueImageScannerTest {

    private final SectionValueImageScanner scanner = new SectionValueImageScanner();

    @Test
    void 중첩된_맵과_리스트에서_이미지_키를_모은다() {
        Map<String, Object> sectionValues = Map.of(
                "cover", Map.of("photo", "invitations/7/cover.jpg"),
                "gallery-1", Map.of("photos", List.of(
                        "invitations/7/a.jpg", "invitations/7/b.jpg")));

        assertThat(scanner.collectKeys(sectionValues))
                .containsExactlyInAnyOrder(
                        "invitations/7/cover.jpg", "invitations/7/a.jpg", "invitations/7/b.jpg");
    }

    @Test
    void 프리픽스가_다른_문자열과_숫자는_무시한다() {
        Map<String, Object> sectionValues = Map.of(
                "cover", Map.of(
                        "groomName", "철수",
                        "guests", 120,
                        "thumbnail", "templates/thumbnails/x.jpg"));

        assertThat(scanner.collectKeys(sectionValues)).isEmpty();
    }

    @Test
    void 같은_키가_여러_번_나와도_한_번만_센다() {
        Map<String, Object> sectionValues = Map.of(
                "gallery-1", Map.of("photos", List.of("invitations/7/a.jpg")),
                "gallery-2", Map.of("photos", List.of("invitations/7/a.jpg")));

        assertThat(scanner.collectKeys(sectionValues)).containsExactly("invitations/7/a.jpg");
    }

    @Test
    void 치환은_원본을_바꾸지_않고_사본을_돌려준다() {
        Map<String, Object> sectionValues = Map.of(
                "cover", Map.of("photo", "invitations/7/cover.jpg"));

        Map<String, Object> replaced = scanner.replaceKeys(sectionValues, key -> "https://cdn/" + key);

        assertThat(nested(replaced)).isEqualTo("https://cdn/invitations/7/cover.jpg");
        assertThat(nested(sectionValues)).isEqualTo("invitations/7/cover.jpg");
    }

    @Test
    void 치환은_리스트_안의_키도_바꾼다() {
        Map<String, Object> sectionValues = Map.of(
                "gallery-1", Map.of("photos", List.of("invitations/7/a.jpg", "설명 텍스트")));

        Map<String, Object> replaced = scanner.replaceKeys(sectionValues, key -> "URL:" + key);

        @SuppressWarnings("unchecked")
        Map<String, Object> section = (Map<String, Object>) replaced.get("gallery-1");
        assertThat((List<?>) section.get("photos"))
                .containsExactly("URL:invitations/7/a.jpg", "설명 텍스트");
    }

    @Test
    void null을_넣어도_터지지_않는다() {
        assertThat(scanner.collectKeys(null)).isEmpty();
        assertThat(scanner.replaceKeys(null, key -> key)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Object nested(Map<String, Object> sectionValues) {
        return ((Map<String, Object>) sectionValues.get("cover")).get("photo");
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "smally.server.domain.image.util.SectionValueImageScannerTest"`
Expected: 컴파일 실패 — `SectionValueImageScanner` 클래스 없음

- [ ] **Step 3: 구현**

```java
package smally.server.domain.image.util;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/**
 * 청첩장 sectionValues(jsonb)를 재귀 순회한다.
 *
 * <p>이미지 필드의 이름·개수·중첩 구조는 컴포넌트마다 다르고 서버는 그것을 모른다(ADR-009).
 * 그래서 구조 대신 객체 키 프리픽스로 이미지를 알아본다.
 */
@Component
public class SectionValueImageScanner {

    public static final String IMAGE_KEY_PREFIX = "invitations/";

    /** 값 전체에서 이미지 객체 키를 모은다. 중복은 제거된다. */
    public Set<String> collectKeys(Object node) {
        Set<String> keys = new LinkedHashSet<>();
        collect(node, keys);
        return keys;
    }

    /** 이미지 키를 resolver가 준 값으로 바꾼 사본을 만든다. 원본은 건드리지 않는다. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> replaceKeys(Map<String, Object> sectionValues,
                                           UnaryOperator<String> resolver) {
        if (sectionValues == null) {
            return Map.of();
        }
        return (Map<String, Object>) replace(sectionValues, resolver);
    }

    private void collect(Object node, Set<String> keys) {
        switch (node) {
            case String value when isImageKey(value) -> keys.add(value);
            case Map<?, ?> map -> map.values().forEach(child -> collect(child, keys));
            case List<?> list -> list.forEach(child -> collect(child, keys));
            case null, default -> { }
        }
    }

    private Object replace(Object node, UnaryOperator<String> resolver) {
        return switch (node) {
            case String value when isImageKey(value) -> resolver.apply(value);
            case Map<?, ?> map -> {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, value) -> copy.put(String.valueOf(key), replace(value, resolver)));
                yield copy;
            }
            case List<?> list -> list.stream().map(child -> replace(child, resolver)).toList();
            case null, default -> node;
        };
    }

    private boolean isImageKey(String value) {
        return value.startsWith(IMAGE_KEY_PREFIX);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "smally.server.domain.image.util.SectionValueImageScannerTest"`
Expected: 6개 테스트 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/domain/image/util src/test/java/smally/server/domain/image/util
git commit -m "feat : sectionValues 이미지 키 수집/치환 스캐너 작성"
```

---

### Task 4: 템플릿 `sectionId` 필수화 + 컴포넌트 검증 NPE 수정

청첩장 값이 `sectionId`로 키잉되려면 템플릿이 `sectionId`를 보장해야 한다.
같은 Task에서, 뒤 Task가 재사용할 `validateComponentJsontData`의 NPE도 고친다.

**Files:**
- Modify: `src/main/java/smally/server/domain/template/service/TemplateServiceImpl.java` (`validateRecipe`)
- Modify: `src/main/java/smally/server/domain/component/service/ComponentServiceImpl.java` (`validateComponentJsontData`)
- Test: `src/test/java/smally/server/domain/template/service/TemplateRecipeValidationTest.java`
- Test: `src/test/java/smally/server/domain/component/service/ComponentDataValidationTest.java`

**Interfaces:**
- Consumes: `ErrorCode.INVALID_TEMPLATE_RECIPE` (기존)
- Produces: `validateComponentJsontData(String componentUid, Map data, Map optionData)` — `data`가 null이면 빈 맵으로, `optionSchema`가 null이면 옵션 검증 생략

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/smally/server/domain/template/service/TemplateRecipeValidationTest.java`:

```java
package smally.server.domain.template.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.TemplateException;
import smally.server.domain.component.dto.ComponentResponse;
import smally.server.domain.component.service.ComponentService;

class TemplateRecipeValidationTest {

    private final ComponentService componentService = mock(ComponentService.class);
    private final TemplateServiceImpl service = new TemplateServiceImpl(
            null, null, componentService, null, null, null);

    private void validateRecipe(List<Map<String, Object>> sections) {
        ReflectionTestUtils.invokeMethod(service, "validateRecipe", sections);
    }

    @Test
    void sectionId와_componentUId가_있으면_통과한다() {
        when(componentService.getComponent(anyString()))
                .thenReturn(new ComponentResponse(1L, "n", "t", "CoverBasic", Map.of()));

        assertThatCode(() -> validateRecipe(List.of(
                Map.of("sectionId", "cover", "componentUId", "CoverBasic"),
                Map.of("sectionId", "gallery-1", "componentUId", "GalleryGrid"))))
                .doesNotThrowAnyException();
    }

    @Test
    void sectionId가_없으면_거부한다() {
        assertThatThrownBy(() -> validateRecipe(List.of(
                Map.of("componentUId", "CoverBasic"))))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TEMPLATE_RECIPE);
    }

    /** 같은 sectionId가 둘이면 청첩장 값이 어느 인스턴스 것인지 알 수 없다. */
    @Test
    void sectionId가_중복되면_거부한다() {
        when(componentService.getComponent(anyString()))
                .thenReturn(new ComponentResponse(1L, "n", "t", "GalleryGrid", Map.of()));

        assertThatThrownBy(() -> validateRecipe(List.of(
                Map.of("sectionId", "gallery", "componentUId", "GalleryGrid"),
                Map.of("sectionId", "gallery", "componentUId", "GalleryGrid"))))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TEMPLATE_RECIPE);
    }
}
```

`src/test/java/smally/server/domain/component/service/ComponentDataValidationTest.java`:

```java
package smally.server.domain.component.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import smally.server.core.exception.exceptions.ComponentException;
import smally.server.core.validation.SchemaValidator;
import smally.server.domain.component.entity.Component;
import smally.server.domain.component.repository.ComponentRepository;
import tools.jackson.databind.ObjectMapper;

class ComponentDataValidationTest {

    private static final Map<String, Object> DATA_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("groomName", Map.of("type", "string")),
            "required", java.util.List.of("groomName"));

    private final ComponentRepository componentRepository = mock(ComponentRepository.class);
    private final ComponentServiceImpl service = new ComponentServiceImpl(
            componentRepository, null, new SchemaValidator(new ObjectMapper()), null);

    private void givenComponent(Map<String, Object> optionSchema) {
        Component component = Component.builder()
                .name("표지").componentUId("CoverBasic")
                .dataSchema(DATA_SCHEMA).optionSchema(optionSchema).build();
        when(componentRepository.findByComponentUId("CoverBasic"))
                .thenReturn(Optional.of(component));
    }

    /** optionSchema가 없는 컴포넌트에서 NPE가 나던 버그 회귀 테스트. */
    @Test
    void optionSchema가_없으면_옵션_검증을_건너뛴다() {
        givenComponent(null);

        assertThatCode(() -> service.validateComponentJsontData(
                "CoverBasic", Map.of("groomName", "철수"), Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void 데이터가_null이면_빈_값으로_보고_required_위반을_잡는다() {
        givenComponent(null);

        assertThatThrownBy(() -> service.validateComponentJsontData("CoverBasic", null, null))
                .isInstanceOf(ComponentException.class);
    }

    @Test
    void 스키마를_지키면_통과한다() {
        givenComponent(null);

        assertThatCode(() -> service.validateComponentJsontData(
                "CoverBasic", Map.of("groomName", "철수"), null))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "*TemplateRecipeValidationTest" --tests "*ComponentDataValidationTest"`
Expected: `sectionId가_없으면_거부한다`·`sectionId가_중복되면_거부한다` FAIL(예외가 안 남),
`optionSchema가_없으면_...` FAIL(NullPointerException)

- [ ] **Step 3: `validateRecipe` 수정**

`TemplateServiceImpl.validateRecipe()`를 통째로 교체한다.

```java
    /* 섹션마다 sectionId(템플릿 내 고유)와 실존하는 componentUId가 있어야 한다. */
    private void validateRecipe(List<Map<String, Object>> sections) {
        if (sections == null || sections.isEmpty()) {
            throw new TemplateException(ErrorCode.INVALID_TEMPLATE_RECIPE);
        }

        Set<String> sectionIds = new HashSet<>();
        for (Map<String, Object> section : sections) {
            if (!(section.get("sectionId") instanceof String sectionId) || sectionId.isBlank()) {
                throw new TemplateException(ErrorCode.INVALID_TEMPLATE_RECIPE);
            }
            // 같은 sectionId가 둘이면 청첩장 값이 어느 인스턴스 것인지 알 수 없다.
            if (!sectionIds.add(sectionId)) {
                throw new TemplateException(ErrorCode.INVALID_TEMPLATE_RECIPE);
            }
            if (!(section.get("componentUId") instanceof String componentUid) || componentUid.isBlank()) {
                throw new TemplateException(ErrorCode.INVALID_TEMPLATE_RECIPE);
            }
            componentService.getComponent(componentUid);
        }
    }
```

import 추가: `java.util.HashSet`, `java.util.Set`.

- [ ] **Step 4: `validateComponentJsontData` 수정**

```java
    @Override
    @Transactional(readOnly = true)
    @ExecutionTimeLog("컴포넌트 Json Data 검증")
    public void validateComponentJsontData(String componentUid, Map<String, Object> data,
                                           Map<String, Object> optionData) {
        Component component = componentRepository.findByComponentUId(componentUid)
                .orElseThrow(() -> new ComponentException(ErrorCode.COMPONENT_NOT_FOUND));

        // 값이 없는 섹션도 빈 값으로 검증에 태워 required 위반으로 잡는다.
        Map<String, Object> safeData = data == null ? Map.of() : data;
        if (!schemaValidator.validateData(component.getDataSchema(), safeData).isEmpty()) {
            throw new ComponentException(ErrorCode.INVALID_COMPONENT_DATA);
        }

        // 옵션 스키마가 없는 컴포넌트는 검증할 것이 없다.
        if (component.getOptionSchema() == null) {
            return;
        }
        Map<String, Object> safeOptions = optionData == null ? Map.of() : optionData;
        if (!schemaValidator.validateData(component.getOptionSchema(), safeOptions).isEmpty()) {
            throw new ComponentException(ErrorCode.INVALID_COMPONENT_OPTION_DATA);
        }
    }
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "*TemplateRecipeValidationTest" --tests "*ComponentDataValidationTest"`
Expected: 6개 테스트 PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/smally/server/domain/template src/main/java/smally/server/domain/component src/test/java/smally/server/domain/template src/test/java/smally/server/domain/component
git commit -m "feat : 템플릿 sectionId 필수화 및 컴포넌트 데이터 검증 null 가드"
```

---

### Task 5: 이미지 업로드 기록·연결

**Files:**
- Modify: `src/main/java/smally/server/domain/image/entity/ImageUpload.java`
- Modify: `src/main/java/smally/server/domain/image/enums/ImageStatus.java`
- Modify: `src/main/java/smally/server/domain/image/repository/ImageUploadRepository.java`
- Create: `src/main/java/smally/server/domain/image/service/InvitationImageService.java`
- Create: `src/main/java/smally/server/domain/image/service/InvitationImageServiceImpl.java`
- Create: `src/main/java/smally/server/domain/image/dto/ImageUploadResponse.java`
- Test: `src/test/java/smally/server/domain/image/service/InvitationImageServiceTest.java`

**Interfaces:**
- Consumes: `SectionValueImageScanner` (Task 3), `StorageService.upload/presignedGetUrl` (기존), `ErrorCode.IMAGE_NOT_LINKABLE` (Task 1)
- Produces:
  - `ImageUploadResponse InvitationImageService.upload(MultipartFile file, User uploader)`
  - `void InvitationImageService.link(Invitation invitation, Map<String,Object> sectionValues)`
  - `Map<String,Object> InvitationImageService.withPresignedUrls(Map<String,Object> sectionValues)`
  - `ImageStatus.ORPHANED`
  - `ImageUpload.linkTo(Invitation)`, `ImageUpload.markOrphaned()`, `ImageUpload.isUploadedBy(Long userId)`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/smally/server/domain/image/service/InvitationImageServiceTest.java`:

```java
package smally.server.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import smally.server.core.exception.exceptions.ImageException;
import smally.server.domain.image.entity.ImageUpload;
import smally.server.domain.image.enums.ImageStatus;
import smally.server.domain.image.repository.ImageUploadRepository;
import smally.server.domain.image.util.SectionValueImageScanner;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.template.entity.Template;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.enums.UserRole;

class InvitationImageServiceTest {

    private final ImageUploadRepository imageUploadRepository = mock(ImageUploadRepository.class);
    private final StorageService storageService = mock(StorageService.class);
    private final InvitationImageServiceImpl service = new InvitationImageServiceImpl(
            imageUploadRepository, storageService, new SectionValueImageScanner());

    private User user(Long id) {
        User user = User.builder().email("a@b.com").userRole(UserRole.USER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Invitation invitation(User owner) {
        return Invitation.builder()
                .user(owner)
                .template(Template.builder().name("t").category("모던")
                        .sections(List.of(Map.of("sectionId", "cover", "componentUId", "CoverBasic")))
                        .build())
                .build();
    }

    private ImageUpload upload(User uploader, String objectKey, ImageStatus status) {
        return ImageUpload.builder()
                .uploader(uploader).objectKey(objectKey).status(status).build();
    }

    @Test
    void 본인이_올린_PENDING_이미지는_청첩장에_연결된다() {
        User owner = user(7L);
        ImageUpload pending = upload(owner, "invitations/7/a.jpg", ImageStatus.PENDING);
        when(imageUploadRepository.findAllByObjectKeyIn(anyCollection())).thenReturn(List.of(pending));
        when(imageUploadRepository.findAllByInvitation(any())).thenReturn(List.of());

        Invitation invitation = invitation(owner);
        service.link(invitation, Map.of("cover", Map.of("photo", "invitations/7/a.jpg")));

        assertThat(pending.getStatus()).isEqualTo(ImageStatus.LINKED);
        assertThat(pending.getInvitation()).isEqualTo(invitation);
    }

    @Test
    void 남이_올린_이미지를_붙이려_하면_거부한다() {
        User owner = user(7L);
        ImageUpload othersImage = upload(user(99L), "invitations/99/a.jpg", ImageStatus.PENDING);
        when(imageUploadRepository.findAllByObjectKeyIn(anyCollection()))
                .thenReturn(List.of(othersImage));

        assertThatThrownBy(() -> service.link(
                invitation(owner), Map.of("cover", Map.of("photo", "invitations/99/a.jpg"))))
                .isInstanceOf(ImageException.class);
    }

    /** 프리픽스 스캔은 정밀하지 않다. 오탐 때문에 사용자의 저장이 실패하면 안 된다. */
    @Test
    void 업로드_기록이_없는_키는_무시한다() {
        User owner = user(7L);
        when(imageUploadRepository.findAllByObjectKeyIn(anyCollection())).thenReturn(List.of());
        when(imageUploadRepository.findAllByInvitation(any())).thenReturn(List.of());

        service.link(invitation(owner), Map.of("cover", Map.of("note", "invitations/오타난 텍스트")));

        // 예외 없이 끝나면 통과
    }

    @Test
    void 이번_저장에서_빠진_이미지는_고아로_표시한다() {
        User owner = user(7L);
        Invitation invitation = invitation(owner);
        ImageUpload removed = upload(owner, "invitations/7/old.jpg", ImageStatus.LINKED);
        when(imageUploadRepository.findAllByObjectKeyIn(anyCollection())).thenReturn(List.of());
        when(imageUploadRepository.findAllByInvitation(invitation)).thenReturn(List.of(removed));

        service.link(invitation, Map.of("cover", Map.of("groomName", "철수")));

        assertThat(removed.getStatus()).isEqualTo(ImageStatus.ORPHANED);
    }

    @Test
    void 조회용_치환은_이미지_키를_presigned_URL로_바꾼다() {
        when(storageService.presignedGetUrl("invitations/7/a.jpg")).thenReturn("https://s3/signed");

        Map<String, Object> result = service.withPresignedUrls(
                Map.of("cover", Map.of("photo", "invitations/7/a.jpg")));

        @SuppressWarnings("unchecked")
        Map<String, Object> cover = (Map<String, Object>) result.get("cover");
        assertThat(cover.get("photo")).isEqualTo("https://s3/signed");
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "*InvitationImageServiceTest"`
Expected: 컴파일 실패 — `InvitationImageServiceImpl`, `ImageStatus.ORPHANED`, `ImageUpload.builder().uploader(...)` 없음

- [ ] **Step 3: 엔티티·enum·리포지토리 수정**

`ImageStatus.java`:

```java
package smally.server.domain.image.enums;

public enum ImageStatus {
    PENDING,
    LINKED,
    ORPHANED
}
```

`ImageUpload.java` — `uploader` 필드와 도메인 메서드를 추가하고 빌더를 교체한다.

```java
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

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

    public boolean isUploadedBy(Long userId) {
        return userId != null && uploader != null && userId.equals(uploader.getId());
    }
```

import 추가: `smally.server.domain.user.entity.User`.

`ImageUploadRepository.java`:

```java
public interface ImageUploadRepository extends JpaRepository<ImageUpload, Long> {

    List<ImageUpload> findAllByObjectKeyIn(Collection<String> objectKeys);

    List<ImageUpload> findAllByInvitation(Invitation invitation);
}
```

`ImageUploadResponse.java`:

```java
package smally.server.domain.image.dto;

public record ImageUploadResponse(String objectKey, String url) {
}
```

- [ ] **Step 4: 서비스 구현**

`InvitationImageService.java`:

```java
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

    /** 조회 응답용으로 이미지 키를 presigned GET URL로 바꾼 사본을 만든다. */
    Map<String, Object> withPresignedUrls(Map<String, Object> sectionValues);
}
```

`InvitationImageServiceImpl.java`:

```java
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
    public Map<String, Object> withPresignedUrls(Map<String, Object> sectionValues) {
        return scanner.replaceKeys(sectionValues, storageService::presignedGetUrl);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "*InvitationImageServiceTest"`
Expected: 5개 테스트 PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/smally/server/domain/image src/test/java/smally/server/domain/image
git commit -m "feat : 청첩장 이미지 업로드 기록 및 연결/고아 처리 작성"
```

---

### Task 6: `InvitationValidator` — 검증 파이프라인

저장 경로와 발행 경로는 검증 강도만 다르다. 서비스에 섞으면 같은 코드가 두 곳에 흩어지므로 분리한다.

**Files:**
- Create: `src/main/java/smally/server/domain/invitation/service/InvitationValidator.java`
- Test: `src/test/java/smally/server/domain/invitation/service/InvitationValidatorTest.java`

**Interfaces:**
- Consumes: `TemplateResponse` (기존, `sections`·`theme` 보유), `InternalComponentService.validateComponentJsontData` (Task 4), `OptionDefinitionService.getOptionDefinition(String)` (기존), `ErrorCode.UNKNOWN_SECTION_ID / INVALID_INVITATION_OPTIONS` (Task 1)
- Produces:
  - `void validateForDraft(TemplateResponse template, Map<String,Object> sectionValues, Map<String,Object> selectedOptions)`
  - `void validateForPublish(TemplateResponse template, Map<String,Object> sectionValues, Map<String,Object> selectedOptions)`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/smally/server/domain/invitation/service/InvitationValidatorTest.java`:

```java
package smally.server.domain.invitation.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentException;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.domain.component.service.InternalComponentService;
import smally.server.domain.template.dto.OptionDefinitionResponse;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.service.OptionDefinitionService;

class InvitationValidatorTest {

    private final InternalComponentService componentService = mock(InternalComponentService.class);
    private final OptionDefinitionService optionDefinitionService = mock(OptionDefinitionService.class);
    private final InvitationValidator validator =
            new InvitationValidator(componentService, optionDefinitionService);

    private TemplateResponse template() {
        return new TemplateResponse("tpl-uid", "모던", "key", "모던",
                List.of(
                        Map.of("sectionId", "cover", "componentUId", "CoverBasic"),
                        Map.of("sectionId", "gallery-1", "componentUId", "GalleryGrid",
                                "editable", List.of("columns"))),
                Map.of());
    }

    private void givenColumnsAllows(Object... allowed) {
        when(optionDefinitionService.getOptionDefinition("columns")).thenReturn(
                new OptionDefinitionResponse("columns", "열 수", "select", "component",
                        List.of(allowed), allowed.length == 0 ? null : allowed[0]));
    }

    @Test
    void 임시저장은_템플릿에_있는_섹션이면_통과한다() {
        assertThatCode(() -> validator.validateForDraft(
                template(), Map.of("cover", Map.of()), Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void 템플릿에_없는_sectionId는_거부한다() {
        assertThatThrownBy(() -> validator.validateForDraft(
                template(), Map.of("없는섹션", Map.of()), Map.of()))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNKNOWN_SECTION_ID);
    }

    @Test
    void editable이_아닌_옵션은_거부한다() {
        assertThatThrownBy(() -> validator.validateForDraft(
                template(), Map.of(), Map.of("cover", Map.of("columns", 3))))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INVITATION_OPTIONS);
    }

    @Test
    void 허용값_밖의_옵션값은_거부한다() {
        givenColumnsAllows(2, 3);

        assertThatThrownBy(() -> validator.validateForDraft(
                template(), Map.of(), Map.of("gallery-1", Map.of("columns", 99))))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INVITATION_OPTIONS);
    }

    @Test
    void 허용된_옵션값은_통과한다() {
        givenColumnsAllows(2, 3);

        assertThatCode(() -> validator.validateForDraft(
                template(), Map.of(), Map.of("gallery-1", Map.of("columns", 3))))
                .doesNotThrowAnyException();
    }

    /** 핵심 차이: 임시저장은 미완성을 허용하고, 발행은 모든 섹션을 완전 검증한다. */
    @Test
    void 임시저장은_스키마_완결성을_검사하지_않는다() {
        validator.validateForDraft(template(), Map.of("cover", Map.of()), Map.of());

        org.mockito.Mockito.verifyNoInteractions(componentService);
    }

    @Test
    void 발행은_값이_없는_섹션까지_전부_검증한다() {
        doThrow(new ComponentException(ErrorCode.INVALID_COMPONENT_DATA))
                .when(componentService).validateComponentJsontData(anyString(), any(), any());

        assertThatThrownBy(() -> validator.validateForPublish(
                template(), Map.of("cover", Map.of("groomName", "철수")), Map.of()))
                .isInstanceOf(ComponentException.class);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "*InvitationValidatorTest"`
Expected: 컴파일 실패 — `InvitationValidator` 클래스 없음

- [ ] **Step 3: 구현**

```java
package smally.server.domain.invitation.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.domain.component.service.InternalComponentService;
import smally.server.domain.template.dto.OptionDefinitionResponse;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.service.OptionDefinitionService;

/**
 * 청첩장 값 검증. 저장 단계에 따라 강도가 다르다(ADR-009 결정 3).
 * - 임시저장(DRAFT): 구조·권한만. 미완성 상태를 허용해야 한다.
 * - 발행(PUBLISHED): 여기에 컴포넌트 dataSchema 완결성 검증을 더한다.
 */
@Component
@RequiredArgsConstructor
public class InvitationValidator {

    private static final String SECTION_ID = "sectionId";
    private static final String COMPONENT_UID = "componentUId";
    private static final String OPTIONS = "options";
    private static final String EDITABLE = "editable";

    private final InternalComponentService internalComponentService;
    private final OptionDefinitionService optionDefinitionService;

    public void validateForDraft(TemplateResponse template,
                                 Map<String, Object> sectionValues,
                                 Map<String, Object> selectedOptions) {
        Map<String, Map<String, Object>> sections = sectionsById(template);

        validateKnownSections(sections.keySet(), sectionValues);
        validateKnownSections(sections.keySet(), selectedOptions);
        validateOptions(sections, selectedOptions);
    }

    public void validateForPublish(TemplateResponse template,
                                   Map<String, Object> sectionValues,
                                   Map<String, Object> selectedOptions) {
        validateForDraft(template, sectionValues, selectedOptions);

        Map<String, Map<String, Object>> sections = sectionsById(template);
        sections.forEach((sectionId, section) -> internalComponentService.validateComponentJsontData(
                (String) section.get(COMPONENT_UID),
                asMap(sectionValues, sectionId),
                mergedOptions(section, selectedOptions)));
    }

    private Map<String, Map<String, Object>> sectionsById(TemplateResponse template) {
        return template.sections().stream()
                .collect(Collectors.toMap(
                        section -> (String) section.get(SECTION_ID),
                        section -> section,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    /**
     * 템플릿에 없는 sectionId를 허용하면 검증된 적 없는 임의 JSON이 jsonb에 쌓이고,
     * 나중에 템플릿이 그 섹션을 갖게 되면 그대로 살아난다.
     */
    private void validateKnownSections(Set<String> knownSectionIds, Map<String, Object> values) {
        if (values == null) {
            return;
        }
        values.keySet().stream()
                .filter(sectionId -> !knownSectionIds.contains(sectionId))
                .findFirst()
                .ifPresent(unknown -> {
                    throw new InvitationException(ErrorCode.UNKNOWN_SECTION_ID);
                });
    }

    private void validateOptions(Map<String, Map<String, Object>> sections,
                                 Map<String, Object> selectedOptions) {
        if (selectedOptions == null) {
            return;
        }
        selectedOptions.forEach((sectionId, raw) -> {
            Set<String> editable = editableKeys(sections.get(sectionId));
            asMapOrEmpty(raw).forEach((optionKey, value) -> {
                if (!editable.contains(optionKey)) {
                    throw new InvitationException(ErrorCode.INVALID_INVITATION_OPTIONS);
                }
                validateAllowedValue(optionKey, value);
            });
        });
    }

    private void validateAllowedValue(String optionKey, Object value) {
        OptionDefinitionResponse definition = optionDefinitionService.getOptionDefinition(optionKey);
        List<Object> allowed = definition.allowedValues();
        if (allowed != null && !allowed.isEmpty() && !allowed.contains(value)) {
            throw new InvitationException(ErrorCode.INVALID_INVITATION_OPTIONS);
        }
    }

    private Set<String> editableKeys(Map<String, Object> section) {
        if (section == null || !(section.get(EDITABLE) instanceof List<?> editable)) {
            return Set.of();
        }
        return editable.stream().map(String::valueOf).collect(Collectors.toSet());
    }

    /** 템플릿 고정옵션 위에 사용자가 고른 값을 덮는다. */
    private Map<String, Object> mergedOptions(Map<String, Object> section,
                                              Map<String, Object> selectedOptions) {
        Map<String, Object> merged = new LinkedHashMap<>(asMapOrEmpty(section.get(OPTIONS)));
        merged.putAll(asMap(selectedOptions, (String) section.get(SECTION_ID)));
        return merged;
    }

    private Map<String, Object> asMap(Map<String, Object> source, String key) {
        return source == null ? Map.of() : asMapOrEmpty(source.get(key));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMapOrEmpty(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*InvitationValidatorTest"`
Expected: 7개 테스트 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/domain/invitation/service/InvitationValidator.java src/test/java/smally/server/domain/invitation/service/InvitationValidatorTest.java
git commit -m "feat : 청첩장 검증 파이프라인 작성 (임시저장/발행 단계 분리)"
```

---

### Task 7: DTO + `InvitationService` (생성·조회·저장·발행·삭제)

**Files:**
- Create: `src/main/java/smally/server/domain/invitation/dto/InvitationCreateRequest.java`
- Create: `src/main/java/smally/server/domain/invitation/dto/InvitationUpdateRequest.java`
- Create: `src/main/java/smally/server/domain/invitation/dto/InvitationResponse.java`
- Create: `src/main/java/smally/server/domain/invitation/dto/InvitationSummaryResponse.java`
- Create: `src/main/java/smally/server/domain/invitation/service/InvitationService.java`
- Create: `src/main/java/smally/server/domain/invitation/service/InvitationServiceImpl.java`
- Test: `src/test/java/smally/server/domain/invitation/service/InvitationServiceImplTest.java`

**Interfaces:**
- Consumes: `InvitationRepository` (Task 1), `SlugGenerator.generate()` (Task 2), `InvitationImageService.link/withPresignedUrls` (Task 5), `InvitationValidator.validateForDraft/validateForPublish` (Task 6), `TemplateService.getTemplate(String)` (기존), `UserRepository.findById` (기존)
- Produces:
  - `InvitationResponse create(Long userId, InvitationCreateRequest request)`
  - `InvitationResponse get(Long userId, String invitationUid)`
  - `List<InvitationSummaryResponse> getMine(Long userId)`
  - `InvitationResponse update(Long userId, String invitationUid, InvitationUpdateRequest request)`
  - `InvitationResponse publish(Long userId, String invitationUid)`
  - `InvitationResponse unpublish(Long userId, String invitationUid)`
  - `void delete(Long userId, String invitationUid)`

- [ ] **Step 1: DTO 작성**

```java
// InvitationCreateRequest.java
package smally.server.domain.invitation.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record InvitationCreateRequest(
        @NotBlank(message = "템플릿 식별자는 필수입니다.")
        String templateUid,
        Map<String, Object> sectionValues,
        Map<String, Object> selectedOptions
) {
}
```

```java
// InvitationUpdateRequest.java
package smally.server.domain.invitation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** 부분 병합이 아닌 전체 교체다 — "지운 것"과 "안 보낸 것"을 구분할 수 없기 때문(스펙 §4.2). */
public record InvitationUpdateRequest(
        @NotNull(message = "섹션 값은 필수입니다.")
        Map<String, Object> sectionValues,
        Map<String, Object> selectedOptions
) {
}
```

```java
// InvitationResponse.java
package smally.server.domain.invitation.dto;

import java.time.Instant;
import java.util.Map;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.invitation.enums.InvitationStatus;

public record InvitationResponse(
        String invitationUid,
        String templateUid,
        InvitationStatus status,
        String slug,
        Map<String, Object> sectionValues,
        Map<String, Object> selectedOptions,
        Instant publishedAt,
        Instant updatedAt
) {
    /** sectionValues에는 presigned URL로 치환된 사본을 넣는다. */
    public static InvitationResponse of(Invitation invitation, Map<String, Object> sectionValues) {
        return new InvitationResponse(
                invitation.getInvitationUid() == null ? null : invitation.getInvitationUid().toString(),
                invitation.getTemplate().getTemplateUid().toString(),
                invitation.getStatus(),
                invitation.getSlug(),
                sectionValues,
                invitation.getSelectedOptions(),
                invitation.getPublishedAt(),
                invitation.getUpdatedAt());
    }
}
```

```java
// InvitationSummaryResponse.java
package smally.server.domain.invitation.dto;

import java.time.Instant;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.invitation.enums.InvitationStatus;

/** 목록에서는 jsonb 본문을 내려보내지 않는다. */
public record InvitationSummaryResponse(
        String invitationUid,
        String templateUid,
        InvitationStatus status,
        String slug,
        Instant updatedAt
) {
    public static InvitationSummaryResponse from(Invitation invitation) {
        return new InvitationSummaryResponse(
                invitation.getInvitationUid().toString(),
                invitation.getTemplate().getTemplateUid().toString(),
                invitation.getStatus(),
                invitation.getSlug(),
                invitation.getUpdatedAt());
    }
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`src/test/java/smally/server/domain/invitation/service/InvitationServiceImplTest.java`:

```java
package smally.server.domain.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.domain.image.service.InvitationImageService;
import smally.server.domain.invitation.dto.InvitationUpdateRequest;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.invitation.enums.InvitationStatus;
import smally.server.domain.invitation.repository.InvitationRepository;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.service.TemplateService;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.enums.UserRole;
import smally.server.domain.user.repository.UserRepository;

class InvitationServiceImplTest {

    private static final UUID INVITATION_UID = UUID.randomUUID();
    private static final UUID TEMPLATE_UID = UUID.randomUUID();

    private final InvitationRepository invitationRepository = mock(InvitationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TemplateService templateService = mock(TemplateService.class);
    private final InvitationValidator validator = mock(InvitationValidator.class);
    private final InvitationImageService imageService = mock(InvitationImageService.class);
    private final SlugGenerator slugGenerator = mock(SlugGenerator.class);

    private final InvitationServiceImpl service = new InvitationServiceImpl(
            invitationRepository, userRepository, templateService,
            validator, imageService, slugGenerator);

    private Invitation invitation;

    @BeforeEach
    void setUp() {
        User owner = User.builder().email("a@b.com").userRole(UserRole.USER).build();
        ReflectionTestUtils.setField(owner, "id", 7L);

        Template template = Template.builder().name("t").category("모던")
                .sections(List.of(Map.of("sectionId", "cover", "componentUId", "CoverBasic")))
                .build();
        ReflectionTestUtils.setField(template, "templateUid", TEMPLATE_UID);

        invitation = Invitation.builder().user(owner).template(template).build();
        ReflectionTestUtils.setField(invitation, "invitationUid", INVITATION_UID);

        when(invitationRepository.findByInvitationUid(INVITATION_UID))
                .thenReturn(Optional.of(invitation));
        when(templateService.getTemplate(anyString())).thenReturn(
                new TemplateResponse(TEMPLATE_UID.toString(), "t", "key", "모던",
                        List.of(Map.of("sectionId", "cover", "componentUId", "CoverBasic")),
                        Map.of()));
        when(imageService.withPresignedUrls(any())).thenReturn(Map.of());
    }

    @Test
    void 저장하면_검증과_이미지_연결을_거쳐_내용이_바뀐다() {
        Map<String, Object> values = Map.of("cover", Map.of("groomName", "철수"));

        service.update(7L, INVITATION_UID.toString(),
                new InvitationUpdateRequest(values, Map.of()));

        verify(validator).validateForDraft(any(), any(), any());
        verify(imageService).link(invitation, values);
        assertThat(invitation.getSectionValues()).isEqualTo(values);
    }

    @Test
    void 남의_청첩장은_403이다() {
        assertThatThrownBy(() -> service.update(99L, INVITATION_UID.toString(),
                new InvitationUpdateRequest(Map.of(), Map.of())))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITATION_ACCESS_DENIED);
    }

    @Test
    void 없는_청첩장은_404이다() {
        UUID unknown = UUID.randomUUID();
        when(invitationRepository.findByInvitationUid(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(7L, unknown.toString()))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITATION_NOT_FOUND);
    }

    @Test
    void uid_형식이_아니면_404이다() {
        assertThatThrownBy(() -> service.get(7L, "uid가-아님"))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITATION_NOT_FOUND);
    }

    @Test
    void 발행하면_완결성_검증을_거쳐_slug가_발급된다() {
        when(slugGenerator.generate()).thenReturn("abc123");

        service.publish(7L, INVITATION_UID.toString());

        verify(validator).validateForPublish(any(), any(), any());
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PUBLISHED);
        assertThat(invitation.getSlug()).isEqualTo("abc123");
    }

    @Test
    void 이미_발행된_청첩장은_다시_발행하지_않는다() {
        invitation.publish("abc123");

        assertThatThrownBy(() -> service.publish(7L, INVITATION_UID.toString()))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITATION_ALREADY_PUBLISHED);
    }

    @Test
    void 재발행할_때는_slug를_새로_만들지_않는다() {
        invitation.publish("abc123");
        invitation.unpublish();

        service.publish(7L, INVITATION_UID.toString());

        verify(slugGenerator, never()).generate();
        assertThat(invitation.getSlug()).isEqualTo("abc123");
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "*InvitationServiceImplTest"`
Expected: 컴파일 실패 — `InvitationServiceImpl` 클래스 없음

- [ ] **Step 4: 인터페이스 작성**

```java
package smally.server.domain.invitation.service;

import java.util.List;
import smally.server.domain.invitation.dto.InvitationCreateRequest;
import smally.server.domain.invitation.dto.InvitationResponse;
import smally.server.domain.invitation.dto.InvitationSummaryResponse;
import smally.server.domain.invitation.dto.InvitationUpdateRequest;

public interface InvitationService {

    InvitationResponse create(Long userId, InvitationCreateRequest request);

    InvitationResponse get(Long userId, String invitationUid);

    List<InvitationSummaryResponse> getMine(Long userId);

    InvitationResponse update(Long userId, String invitationUid, InvitationUpdateRequest request);

    InvitationResponse publish(Long userId, String invitationUid);

    InvitationResponse unpublish(Long userId, String invitationUid);

    void delete(Long userId, String invitationUid);
}
```

- [ ] **Step 5: 구현**

```java
package smally.server.domain.invitation.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.aop.ExecutionTimeLog;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.core.exception.exceptions.UserException;
import smally.server.domain.image.service.InvitationImageService;
import smally.server.domain.invitation.dto.InvitationCreateRequest;
import smally.server.domain.invitation.dto.InvitationResponse;
import smally.server.domain.invitation.dto.InvitationSummaryResponse;
import smally.server.domain.invitation.dto.InvitationUpdateRequest;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.invitation.enums.InvitationStatus;
import smally.server.domain.invitation.repository.InvitationRepository;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.service.TemplateService;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class InvitationServiceImpl implements InvitationService {

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final TemplateService templateService;
    private final InvitationValidator validator;
    private final InvitationImageService invitationImageService;
    private final SlugGenerator slugGenerator;

    @Override
    @Transactional
    @ExecutionTimeLog("청첩장 생성")
    public InvitationResponse create(Long userId, InvitationCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
        TemplateResponse template = templateService.getTemplate(request.templateUid());

        validator.validateForDraft(template, request.sectionValues(), request.selectedOptions());

        Invitation invitation = Invitation.builder()
                .user(user)
                .template(templateReference(request.templateUid()))
                .sectionValues(request.sectionValues())
                .selectedOptions(request.selectedOptions())
                .build();
        invitationRepository.save(invitation);

        invitationImageService.link(invitation, request.sectionValues());

        return toResponse(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationResponse get(Long userId, String invitationUid) {
        return toResponse(getOwned(userId, invitationUid));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitationSummaryResponse> getMine(Long userId) {
        return invitationRepository.findAllByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(InvitationSummaryResponse::from)
                .toList();
    }

    @Override
    @Transactional
    @ExecutionTimeLog("청첩장 저장")
    public InvitationResponse update(Long userId, String invitationUid,
                                     InvitationUpdateRequest request) {
        Invitation invitation = getOwned(userId, invitationUid);
        TemplateResponse template = currentTemplate(invitation);

        validator.validateForDraft(template, request.sectionValues(), request.selectedOptions());
        invitationImageService.link(invitation, request.sectionValues());
        invitation.updateContent(request.sectionValues(), request.selectedOptions());

        return toResponse(invitation);
    }

    @Override
    @Transactional
    @ExecutionTimeLog("청첩장 발행")
    public InvitationResponse publish(Long userId, String invitationUid) {
        Invitation invitation = getOwned(userId, invitationUid);
        if (invitation.getStatus() == InvitationStatus.PUBLISHED) {
            throw new InvitationException(ErrorCode.INVITATION_ALREADY_PUBLISHED);
        }

        validator.validateForPublish(
                currentTemplate(invitation),
                invitation.getSectionValues(),
                invitation.getSelectedOptions());

        // 재발행이면 기존 slug를 그대로 쓴다 — 공유된 링크가 살아 있어야 한다.
        String slug = invitation.getSlug() != null ? invitation.getSlug() : slugGenerator.generate();
        invitation.publish(slug);

        return toResponse(invitation);
    }

    @Override
    @Transactional
    public InvitationResponse unpublish(Long userId, String invitationUid) {
        Invitation invitation = getOwned(userId, invitationUid);
        invitation.unpublish();
        return toResponse(invitation);
    }

    @Override
    @Transactional
    public void delete(Long userId, String invitationUid) {
        invitationRepository.delete(getOwned(userId, invitationUid));
    }

    private Invitation getOwned(Long userId, String invitationUid) {
        Invitation invitation = invitationRepository.findByInvitationUid(parseUid(invitationUid))
                .orElseThrow(() -> new InvitationException(ErrorCode.INVITATION_NOT_FOUND));
        if (!invitation.isOwnedBy(userId)) {
            throw new InvitationException(ErrorCode.INVITATION_ACCESS_DENIED);
        }
        return invitation;
    }

    private UUID parseUid(String invitationUid) {
        try {
            return UUID.fromString(invitationUid);
        } catch (IllegalArgumentException e) {
            throw new InvitationException(ErrorCode.INVITATION_NOT_FOUND);
        }
    }

    private TemplateResponse currentTemplate(Invitation invitation) {
        return templateService.getTemplate(invitation.getTemplate().getTemplateUid().toString());
    }

    private Template templateReference(String templateUid) {
        return templateService.getTemplateEntity(templateUid);
    }

    private InvitationResponse toResponse(Invitation invitation) {
        Map<String, Object> presigned =
                invitationImageService.withPresignedUrls(invitation.getSectionValues());
        return InvitationResponse.of(invitation, presigned);
    }
}
```

> **필요한 추가 변경:** `TemplateService` 인터페이스에 `Template getTemplateEntity(String templateUid)`를
> 추가하고, `TemplateServiceImpl`의 기존 private 메서드 `getTemplateEntity`를 `public @Override`로 올린다.
> 청첩장이 `@ManyToOne Template` 연관을 맺으려면 캐시된 DTO가 아니라 영속 엔티티가 필요하다.

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew test --tests "*InvitationServiceImplTest"`
Expected: 7개 테스트 PASS

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/smally/server/domain/invitation src/main/java/smally/server/domain/template src/test/java/smally/server/domain/invitation
git commit -m "feat : 청첩장 생성/조회/저장/발행 서비스 작성"
```

---

### Task 8: 하객 공개 조회

**Files:**
- Create: `src/main/java/smally/server/domain/invitation/dto/PublicInvitationResponse.java`
- Create: `src/main/java/smally/server/domain/invitation/service/PublicInvitationService.java`
- Create: `src/main/java/smally/server/domain/invitation/service/PublicInvitationServiceImpl.java`
- Test: `src/test/java/smally/server/domain/invitation/service/PublicInvitationServiceImplTest.java`

**Interfaces:**
- Consumes: `InvitationRepository.findBySlugAndStatus` (Task 1), `TemplateService.getTemplate` (기존), `InvitationImageService.withPresignedUrls` (Task 5)
- Produces: `PublicInvitationResponse PublicInvitationService.getBySlug(String slug)`

- [ ] **Step 1: DTO 작성**

```java
package smally.server.domain.invitation.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 하객 렌더러가 API 한 번으로 받는 페이로드.
 * sections는 템플릿 레시피에 사용자가 고른 옵션을 덮어쓴 결과이고,
 * sectionValues의 이미지 키는 presigned URL로 치환되어 있다.
 */
public record PublicInvitationResponse(
        String invitationUid,
        List<Map<String, Object>> sections,
        Map<String, Object> theme,
        Map<String, Object> sectionValues,
        Instant publishedAt
) {
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`src/test/java/smally/server/domain/invitation/service/PublicInvitationServiceImplTest.java`:

```java
package smally.server.domain.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.domain.image.service.InvitationImageService;
import smally.server.domain.invitation.dto.PublicInvitationResponse;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.invitation.enums.InvitationStatus;
import smally.server.domain.invitation.repository.InvitationRepository;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.service.TemplateService;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.enums.UserRole;

class PublicInvitationServiceImplTest {

    private static final UUID TEMPLATE_UID = UUID.randomUUID();

    private final InvitationRepository invitationRepository = mock(InvitationRepository.class);
    private final TemplateService templateService = mock(TemplateService.class);
    private final InvitationImageService imageService = mock(InvitationImageService.class);

    private final PublicInvitationServiceImpl service =
            new PublicInvitationServiceImpl(invitationRepository, templateService, imageService);

    private Invitation published;

    @BeforeEach
    void setUp() {
        User owner = User.builder().email("a@b.com").userRole(UserRole.USER).build();
        ReflectionTestUtils.setField(owner, "id", 7L);

        Template template = Template.builder().name("t").category("모던")
                .sections(List.of(Map.of("sectionId", "gallery-1", "componentUId", "GalleryGrid")))
                .build();
        ReflectionTestUtils.setField(template, "templateUid", TEMPLATE_UID);

        published = Invitation.builder().user(owner).template(template)
                .sectionValues(Map.of("gallery-1", Map.of("photos", List.of("invitations/7/a.jpg"))))
                .selectedOptions(Map.of("gallery-1", Map.of("columns", 3)))
                .build();
        ReflectionTestUtils.setField(published, "invitationUid", UUID.randomUUID());
        published.publish("abc123");

        when(templateService.getTemplate(anyString())).thenReturn(
                new TemplateResponse(TEMPLATE_UID.toString(), "t", "key", "모던",
                        List.of(Map.of("sectionId", "gallery-1", "componentUId", "GalleryGrid",
                                "options", Map.of("columns", 2, "gap", 8))),
                        Map.of("fontFamily", "serif")));
        when(imageService.withPresignedUrls(any()))
                .thenReturn(Map.of("gallery-1", Map.of("photos", List.of("https://s3/signed"))));
    }

    @Test
    void 발행된_청첩장은_slug로_조회된다() {
        when(invitationRepository.findBySlugAndStatus("abc123", InvitationStatus.PUBLISHED))
                .thenReturn(Optional.of(published));

        PublicInvitationResponse response = service.getBySlug("abc123");

        assertThat(response.theme()).containsEntry("fontFamily", "serif");
        assertThat(response.publishedAt()).isNotNull();
    }

    @Test
    void 사용자가_고른_옵션이_템플릿_고정옵션_위에_덮인다() {
        when(invitationRepository.findBySlugAndStatus("abc123", InvitationStatus.PUBLISHED))
                .thenReturn(Optional.of(published));

        PublicInvitationResponse response = service.getBySlug("abc123");

        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) response.sections().getFirst().get("options");
        assertThat(options).containsEntry("columns", 3).containsEntry("gap", 8);
    }

    @Test
    void 이미지_키는_presigned_URL로_치환된다() {
        when(invitationRepository.findBySlugAndStatus("abc123", InvitationStatus.PUBLISHED))
                .thenReturn(Optional.of(published));

        PublicInvitationResponse response = service.getBySlug("abc123");

        assertThat(response.sectionValues().toString()).contains("https://s3/signed");
    }

    /** DRAFT는 findBySlugAndStatus에 걸리지 않으므로 존재 자체가 드러나지 않는다. */
    @Test
    void 발행되지_않았거나_없는_slug는_404이다() {
        when(invitationRepository.findBySlugAndStatus("없는slug", InvitationStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBySlug("없는slug"))
                .isInstanceOf(InvitationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITATION_NOT_FOUND);
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "*PublicInvitationServiceImplTest"`
Expected: 컴파일 실패 — `PublicInvitationServiceImpl` 클래스 없음

- [ ] **Step 4: 구현**

```java
package smally.server.domain.invitation.service;

import smally.server.domain.invitation.dto.PublicInvitationResponse;

public interface PublicInvitationService {

    /** 발행된 청첩장만 조회된다. DRAFT는 존재 자체가 드러나지 않는다. */
    PublicInvitationResponse getBySlug(String slug);
}
```

```java
package smally.server.domain.invitation.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.aop.ExecutionTimeLog;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.domain.image.service.InvitationImageService;
import smally.server.domain.invitation.dto.PublicInvitationResponse;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.invitation.enums.InvitationStatus;
import smally.server.domain.invitation.repository.InvitationRepository;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.service.TemplateService;

@Service
@RequiredArgsConstructor
public class PublicInvitationServiceImpl implements PublicInvitationService {

    private static final String SECTION_ID = "sectionId";
    private static final String OPTIONS = "options";

    private final InvitationRepository invitationRepository;
    private final TemplateService templateService;
    private final InvitationImageService invitationImageService;

    @Override
    @Transactional(readOnly = true)
    @ExecutionTimeLog("청첩장 공개 조회")
    public PublicInvitationResponse getBySlug(String slug) {
        Invitation invitation = invitationRepository
                .findBySlugAndStatus(slug, InvitationStatus.PUBLISHED)
                .orElseThrow(() -> new InvitationException(ErrorCode.INVITATION_NOT_FOUND));

        TemplateResponse template = templateService.getTemplate(
                invitation.getTemplate().getTemplateUid().toString());

        return new PublicInvitationResponse(
                invitation.getInvitationUid().toString(),
                mergeSelectedOptions(template.sections(), invitation.getSelectedOptions()),
                template.theme(),
                invitationImageService.withPresignedUrls(invitation.getSectionValues()),
                invitation.getPublishedAt());
    }

    /** 템플릿 고정옵션 위에 사용자가 고른 값을 덮은 사본을 만든다. 캐시된 템플릿 DTO는 건드리지 않는다. */
    private List<Map<String, Object>> mergeSelectedOptions(List<Map<String, Object>> sections,
                                                           Map<String, Object> selectedOptions) {
        return sections.stream().map(section -> {
            Map<String, Object> copy = new LinkedHashMap<>(section);
            Map<String, Object> options = new LinkedHashMap<>(asMap(section.get(OPTIONS)));
            options.putAll(asMap(selected(selectedOptions, section.get(SECTION_ID))));
            copy.put(OPTIONS, options);
            return copy;
        }).toList();
    }

    private Object selected(Map<String, Object> selectedOptions, Object sectionId) {
        return selectedOptions == null ? null : selectedOptions.get(String.valueOf(sectionId));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "*PublicInvitationServiceImplTest"`
Expected: 4개 테스트 PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/smally/server/domain/invitation src/test/java/smally/server/domain/invitation
git commit -m "feat : 하객용 청첩장 공개 조회 서비스 작성"
```

---

### Task 9: 컨트롤러 + 인증 + 인가 규칙

**Files:**
- Create: `src/main/java/smally/server/core/security/CurrentUser.java`
- Create: `src/main/java/smally/server/domain/invitation/controller/InvitationController.java`
- Create: `src/main/java/smally/server/domain/invitation/controller/PublicInvitationController.java`
- Create: `src/main/java/smally/server/domain/image/controller/InvitationImageController.java`
- Modify: `src/main/java/smally/server/config/SecurityConfig.java`
- Test: `src/test/java/smally/server/core/security/CurrentUserTest.java`

**Interfaces:**
- Consumes: `InvitationService` (Task 7), `PublicInvitationService` (Task 8), `InvitationImageService` (Task 5), `ErrorCode.UNAUTHENTICATED_REQUIRED` (Task 1)
- Produces: `Long CurrentUser.requireUserId(String principal)`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/smally/server/core/security/CurrentUserTest.java`:

```java
package smally.server.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;

class CurrentUserTest {

    @Test
    void principal_문자열을_userId로_바꾼다() {
        assertThat(CurrentUser.requireUserId("7")).isEqualTo(7L);
    }

    @Test
    void principal이_없으면_401이다() {
        assertThatThrownBy(() -> CurrentUser.requireUserId(null))
                .isInstanceOf(AuthException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED_REQUIRED);
    }

    @Test
    void principal이_숫자가_아니면_401이다() {
        assertThatThrownBy(() -> CurrentUser.requireUserId("anonymousUser"))
                .isInstanceOf(AuthException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED_REQUIRED);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "*CurrentUserTest"`
Expected: 컴파일 실패 — `CurrentUser` 클래스 없음

- [ ] **Step 3: `CurrentUser` 구현**

```java
package smally.server.core.security;

import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;

/**
 * JwtAuthenticationFilter가 principal에 넣은 userId 문자열을 꺼낸다.
 * 인증이 없으면 principal이 null이거나 "anonymousUser"다.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long requireUserId(String principal) {
        try {
            return Long.valueOf(principal);
        } catch (NumberFormatException | NullPointerException e) {
            throw new AuthException(ErrorCode.UNAUTHENTICATED_REQUIRED);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*CurrentUserTest"`
Expected: 3개 테스트 PASS

- [ ] **Step 5: 컨트롤러 작성**

```java
package smally.server.domain.invitation.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import smally.server.core.dto.ApiResponse;
import smally.server.core.security.CurrentUser;
import smally.server.domain.invitation.dto.*;
import smally.server.domain.invitation.service.InvitationService;

@RestController
@RequestMapping("/api/invitation")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping("/v1")
    public ResponseEntity<ApiResponse<InvitationResponse>> create(
            @AuthenticationPrincipal String principal,
            @RequestBody @Valid InvitationCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED,
                invitationService.create(CurrentUser.requireUserId(principal), request)));
    }

    @GetMapping("/v1/{invitationUid}")
    public ResponseEntity<ApiResponse<InvitationResponse>> get(
            @AuthenticationPrincipal String principal,
            @PathVariable String invitationUid) {
        return ResponseEntity.ok(ApiResponse.ok(
                invitationService.get(CurrentUser.requireUserId(principal), invitationUid)));
    }

    @GetMapping("/v1")
    public ResponseEntity<ApiResponse<List<InvitationSummaryResponse>>> getMine(
            @AuthenticationPrincipal String principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                invitationService.getMine(CurrentUser.requireUserId(principal))));
    }

    @PutMapping("/v1/{invitationUid}")
    public ResponseEntity<ApiResponse<InvitationResponse>> update(
            @AuthenticationPrincipal String principal,
            @PathVariable String invitationUid,
            @RequestBody @Valid InvitationUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                invitationService.update(CurrentUser.requireUserId(principal), invitationUid, request)));
    }

    @PostMapping("/v1/{invitationUid}/publish")
    public ResponseEntity<ApiResponse<InvitationResponse>> publish(
            @AuthenticationPrincipal String principal,
            @PathVariable String invitationUid) {
        return ResponseEntity.ok(ApiResponse.ok(
                invitationService.publish(CurrentUser.requireUserId(principal), invitationUid)));
    }

    @PostMapping("/v1/{invitationUid}/unpublish")
    public ResponseEntity<ApiResponse<InvitationResponse>> unpublish(
            @AuthenticationPrincipal String principal,
            @PathVariable String invitationUid) {
        return ResponseEntity.ok(ApiResponse.ok(
                invitationService.unpublish(CurrentUser.requireUserId(principal), invitationUid)));
    }

    @DeleteMapping("/v1/{invitationUid}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal String principal,
            @PathVariable String invitationUid) {
        invitationService.delete(CurrentUser.requireUserId(principal), invitationUid);
        return ResponseEntity.ok(ApiResponse.noContent());
    }
}
```

```java
package smally.server.domain.invitation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smally.server.core.dto.ApiResponse;
import smally.server.domain.invitation.dto.PublicInvitationResponse;
import smally.server.domain.invitation.service.PublicInvitationService;

/** 하객이 로그인 없이 여는 경로. 인증 경계가 달라 컨트롤러를 분리한다. */
@RestController
@RequestMapping("/api/public/invitation")
@RequiredArgsConstructor
public class PublicInvitationController {

    private final PublicInvitationService publicInvitationService;

    @GetMapping("/v1/{slug}")
    public ResponseEntity<ApiResponse<PublicInvitationResponse>> getBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(publicInvitationService.getBySlug(slug)));
    }
}
```

```java
package smally.server.domain.image.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import smally.server.core.dto.ApiResponse;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.UserException;
import smally.server.core.security.CurrentUser;
import smally.server.domain.image.dto.ImageUploadResponse;
import smally.server.domain.image.service.InvitationImageService;
import smally.server.domain.user.repository.UserRepository;

@RestController
@RequestMapping("/api/invitation")
@RequiredArgsConstructor
public class InvitationImageController {

    private final InvitationImageService invitationImageService;
    private final UserRepository userRepository;

    @PostMapping(value = "/v1/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageUploadResponse>> upload(
            @AuthenticationPrincipal String principal,
            @RequestPart("image") MultipartFile image) {
        Long userId = CurrentUser.requireUserId(principal);
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED,
                invitationImageService.upload(image, userRepository.findById(userId)
                        .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND)))));
    }
}
```

- [ ] **Step 6: 인가 규칙 좁히기**

`SecurityConfig.java`의 `authorizeHttpRequests` 블록을 교체한다.

```java
                .authorizeHttpRequests(auth -> auth
                        // 하객은 계정이 없다(ADR-003).
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/invitation/**").authenticated()
                        // 나머지 경로의 인가 규칙 정비는 별도 작업이다.
                        .anyRequest().permitAll())
```

- [ ] **Step 7: 전체 빌드 확인 후 커밋**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

```bash
git add src/main/java/smally/server src/test/java/smally/server/core/security
git commit -m "feat : 청첩장 API 컨트롤러 및 인가 규칙 작성"
```

---

### Task 10: 문서 갱신

**Files:**
- Modify: `docs/ERD.md` (§2.6 `invitations`, §2.7 `image_uploads`, §1 다이어그램)
- Modify: `docs/API-SPEC.md`
- Modify: `docs/adr/ADR-009-invitation-persistence-model.md` (후속 조치 체크)

- [ ] **Step 1: ERD 갱신**

`§1` mermaid의 `invitations`에 `invitation_uid`, `selected_options`를 추가하고 `image_uploads`에 `uploader_id`를 추가한다.
`§2.6` 표에 `invitation_uid`(varchar/UUID, UNIQUE, 불변)와 `selected_options`(jsonb) 행을 넣고,
`section_values` 예시를 `sectionId` 키 기반으로 교체한다(스펙 §3 예시 사용).
`§2.7` 표에 `uploader_id`(FK → users, NOT NULL) 행을 추가하고 `status` 설명에 `orphaned`를 넣는다.

- [ ] **Step 2: API 명세 갱신**

`docs/API-SPEC.md`에 스펙 §4 표의 9개 엔드포인트를 기존 문서 형식에 맞춰 추가한다.

- [ ] **Step 3: ADR 후속 조치 반영**

ADR-009 "후속 조치 필요 사항"에서 완료된 항목(ADR-001 표기, ERD 갱신)에 `(완료)`를 표시한다.

- [ ] **Step 4: 커밋**

```bash
git add docs
git commit -m "docs : 청첩장 저장 구현 반영해 ERD와 API 명세 갱신"
```

---

## 자체 점검 결과

**스펙 커버리지**

| 스펙 항목 | 담당 Task |
|---|---|
| §2.1 `Invitation` 변경 | 1 |
| §2.2 `sectionId` 필수화 | 4 |
| §2.3 `ImageUpload` 변경 | 5 |
| §2.4 리포지토리 쿼리 | 1 |
| §4 API 9개 | 7, 8, 9 |
| §4.3 slug 생성 | 2 |
| §5.1 저장 검증 | 6 |
| §5.2 발행 검증 | 6 |
| §5.3 기존 코드 수정 | 4 |
| §6.1 업로드 | 5 |
| §6.2 연결·고아 | 5 |
| §6.3 URL 치환 | 3, 5 |
| §7.1 에러코드 | 1 |
| §7.2 현재 사용자 | 9 |
| §7.3 인가 규칙 | 9 |
| §9 테스트 | 각 Task |
| §10 미해결 | 범위 밖(명시) |

**계획 수립 중 발견해 반영한 사항**

1. `InvitationServiceImpl`이 `@ManyToOne Template` 연관을 맺으려면 캐시된 `TemplateResponse`가 아니라 영속 엔티티가 필요하다 → Task 7에 `TemplateService.getTemplateEntity()` 공개를 포함했다.
2. `@Transactional protected` 메서드를 같은 클래스에서 호출하면 프록시를 타지 않는다 → Task 5에서 `repository.save()`를 직접 부르도록 바꿨다.
3. `PublicInvitationServiceImpl`의 옵션 병합은 **캐시된 템플릿 DTO를 변형하면 안 된다**(Redis에 30일 사는 객체다) → 사본을 만들도록 명시했다.
