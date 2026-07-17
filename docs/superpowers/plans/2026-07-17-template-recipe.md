# Template 레시피 전환 (계획 C) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **개정 이력**
> - 2026-07-17 (초안): 작성. 착수 전.
> - 2026-07-17 (개정 1): 착수 전 상태에서 **실제 코드베이스와 대조해 재작성**. 초안이 틀린 전제 3개를 고치고, 사용자 정책 결정 3개를 반영했다. 아래 "초안 대비 변경점" 참조.
> - 2026-07-17 (실행 완료, 현재): 전 태스크 실행. `clean build` **59 tests / 0 failures**. 실행 중 개정 1도 예측 못 한 사실 2개를 발견했다 — 아래 "실행 기록" 참조. **미커밋**(사용자 검토 후 직접 커밋).

## 실행 기록 (2026-07-17)

개정 1은 "`compileTestJava`만 고치면 된다"고 봤지만, 컴파일을 고치자 **그동안 한 번도 실행된 적 없던 테스트 스위트**가 처음 돌면서 잠복 실패 5개가 드러났다. 계획서가 예측하지 못한 부분이다.

1. **🔴 기동 불가 버그 발견 (계획 범위 밖, 그러나 차단 요인)** — `ComponentRepository.findbyComponentUid`(소문자 `b`). Spring Data가 `By` 구분자를 못 찾아 메서드 이름 전체를 프로퍼티로 해석 → `PropertyReferenceException` → `componentRepository` 빈 생성 실패 → **ApplicationContext 로드 실패 = 서버가 뜨지 않음**. 커밋 `3117208` 이후 계속 존재했으나, `compileTestJava`가 깨져 `test` 태스크에 도달하지 못한 탓에 `contextLoads()`가 실행된 적이 없어 아무도 몰랐다.
   - **계획 C가 이 메서드에 직접 의존한다**(`validateRecipe` → `componentService.getComponent` → `findbyComponentUid`). 고치지 않으면 계획 C가 동작할 수 없어 수정했다: `findByComponentUId`(+ `ComponentServiceImpl` 호출부). 프로퍼티명을 엔티티 필드 `componentUId`와 철자까지 일치시켰다.
   - 상세: [docs/TROUBLE-component-repository-query-method-typo.md](../../TROUBLE-component-repository-query-method-typo.md)
2. **스테일 Component 테스트가 1개가 아니라 4개였다** — 개정 1은 `ComponentResponseTest`만 깨졌다고 봤다(컴파일 기준으로는 맞음). 그러나 **실행 기준**으로는 3개가 더 실패했다: `ComponentCreateRequestTest`(`toComponent()`가 매핑하지 않는 `componentType`을 String과 비교), `ComponentServiceImplTest.createComponent_유효하면_저장한다`(`getComponentByName` 미스텁 → `ComponentResponse.from`에서 NPE), `ComponentServiceImplTest.getComponent_...`·`ComponentTypeServiceImplTest.validateExists_...`(존재하지 않는 경로 `findById`/`existsByName`를 스텁 → `UnnecessaryStubbingException`). 모두 A′ 부채이며 사용자 승인 범위("테스트 수정")에서 정리했다.

**교훈**: "컴파일이 깨져 있다"는 단순 부채가 아니라 **회귀 탐지 능력의 상실**이었다. 기동 불가 버그가 그 뒤에 숨어 있었다.

**Goal:** 기존 `Template`(데이터 검증용 스키마 덩어리)을 **레시피**(`sections` 순서 리스트 + `theme`)로 전환한다. 등록 시 참조 컴포넌트 존재(레시피 유효성)와 theme 값(OptionDefinition 카탈로그)을 검증한다. 조회는 Component와 동일한 Redis 캐싱을 태운다. (ADR-007 하위 계획 C)

**Architecture:** `Template`에서 `section_schema`/`variants`를 걷어내고 `sections`(`List<Map>` jsonb)+`theme`(`Map` jsonb)를 넣는다. 데이터 검증은 컴포넌트 레벨로 이미 옮겼으므로(계획 A′) 템플릿의 `isValidTemplate`/`InternalTemplateService`/`VariantResponse`는 제거한다. 등록 검증은 `ComponentService.getComponent`(존재)와 `OptionDefinitionService.getOptionDefinition`(+allowedValues)로 수행한다. 이 리팩터는 **기존 커밋된 Template 코드**를 뜯으므로 결합이 강하다 — Task 0이 먼저 깨진 빌드를 복구하고, Task 1이 프로덕션 전체를 원자적으로 바꾸고, Task 2가 새 테스트를 붙인다.

**Tech Stack:** Spring Boot 4.1.0, Java 25, Spring Data JPA, PostgreSQL jsonb(`@JdbcTypeCode(SqlTypes.JSON)`), Redis(`RedisCacheService`), Jackson 3.

---

## 초안 대비 변경점 (왜 개정했는가)

**초안이 틀렸던 전제 (실제 코드 확인 결과):**

1. **"스테일 테스트 3개만 지우면 컴파일 green"** → 틀림. 현재 `compileTestJava`는 **8 errors / 5 files**로 깨져 있다. 초안이 센 3개(`TemplateServiceImplTest`, `TemplateCreateRequestTest`, `VariantResponseTest`) 외에 **`ComponentResponseTest`**도 깨져 있고, 이건 **커밋된** 계획 A′ 쪽 부채라 계획 C가 지운다고 없어지지 않는다. → **Task 0 신설.**
2. **"컴포넌트 테스트가 옛 모델 참조"** → 절반만 맞음. `ComponentCreateRequestTest`/`ComponentServiceImplTest`/`ComponentTypeServiceImplTest`는 **이미 새 모델로 컴파일된다**. 깨진 건 `ComponentResponseTest` 하나뿐(`.componentType(String)`·`.frontendBinding(String)` 빌더가 현재 `Component`에 없음 — `componentType`은 `@Setter` 주입으로, `frontendBinding`은 `componentUId`로 바뀜).
3. **"계획 A′는 작업 트리에 존재"** → 틀림. A′는 **커밋됨**(`3117208`, `b3e051e`). 따라서 A′ 파일 수정은 이 계획에서 별도 커밋 대상이다.

**사용자 정책 결정 (2026-07-17 반영):**

4. **Template 조회에 Redis 캐싱 적용** — Component와 동일 패턴(`TPL:` 키, TTL 30일). 초안의 `TemplateServiceImpl`에는 캐싱이 없었다. → Task 1 Step 6, Task 2 Step 4 변경.
5. **레시피 전환은 원안대로 전부 제거** — `sectionSchema`·`variants`·`VariantResponse`·`InternalTemplateService` 모두 제거 확정.
6. **Component 테스트 4개를 `domain/component` 패키지로 이동** — 현재 main은 `domain/component`인데 test는 `domain/template` 아래에 있어 미러링이 깨져 있다. → Task 0에 포함.

**미해결로 남기는 것(사용자 확인 필요, 이 계획 범위 밖):**

- **캐싱 도입 시점 vs 성능 연구의 충돌**: [성능 연구 계획](./2026-07-17-performance-research.md)의 연구 C는 *"이 규모에서 캐시가 애초에 이득인가, C1(캐시 없음)이 이기면 Redis를 넣지 않는다"*를 아직 안 닫았다. Template 캐싱은 그 결론을 앞질러 들어간다. 연구 C 결과에 따라 되돌릴 수 있음을 전제로 진행한다(사용자 결정).
- **캐시 stale read**: 템플릿 수정/삭제가 범위 밖이라 지금은 무효화 경로가 없다(등록 시 적재만). 수정 기능이 생기면 무효화가 **필수**다 — 성능 연구의 탈락 조건("stale read 0건")과 직결.
- **`ComponentResponse.frontendBinding` 네이밍**: 레코드 필드는 `frontendBinding`인데 실제 값은 `componentUId`이고, 레시피 JSON 키도 `componentUId`다. 한 개념에 이름이 둘. 정리는 후속.
- **`Component.@Builder`에 `componentType` 부재**: 빌드 후 `@Setter`로 주입하는 2단 구조라 불변식이 깨질 수 있다(타입 없는 `Component` 생성 가능). 후속.

---

## Global Constraints

- **선행 의존(확인 완료)**: `ComponentService.getComponent(String componentUid)` → `ComponentResponse`(없으면 `COMPONENT_NOT_FOUND`) — **커밋됨**. `OptionDefinitionService.getOptionDefinition(String)` → `OptionDefinition`(없으면 `OPTION_DEFINITION_NOT_FOUND`) + `OptionDefinition.getAllowedValues()` — **작업 트리(계획 B, 미커밋)**.
- **컴포넌트 참조 키**: 레시피는 컴포넌트를 PK(`id`)가 아니라 **`componentUId`(String, 고유·불변)**로 지목한다. 이게 컴포넌트의 외부 식별자이자 프론트 연결 키이기 때문이다(ADR-007 결정 1).
- **sections 형태**: `List<Map<String,Object>>`(요청·엔티티 공통, 코드베이스의 jsonb-Map 관례). 각 항목: `{ "componentUId": "<string>", "options": {…고정값}, "editable": [ …키 ] }`. 서버는 `componentUId`만 방어적으로 추출·검증한다(options/editable 세부 검증은 후속).
- **theme 형태**: `Map<String,Object>`(`{ optionKey: 값 }`). nullable.
- **제거 대상**: `Template.sectionSchema/variants`, `VariantResponse`(+테스트), `InternalTemplateService`+`isValidTemplate`. (외부 참조 없음을 확인함. `optionsSchema` 필드는 이미 작업 트리에서 제거되어 있다.)
- **캐싱**: `RedisCacheService`(`getCacheData(key, Class)`/`setCacheData(key, value, ttl)`/`deleteCacheData(key)`) 재사용. 예외를 삼키고 null을 반환하므로 Redis 장애 시 DB로 자연 폴백된다. 값 직렬화는 `GenericJacksonJsonRedisSerializer`(레코드 OK).
- **엔진**: 이 계획은 `SchemaValidator`를 쓰지 않는다(데이터검증은 컴포넌트/청첩장으로 이동). `TemplateServiceImpl`에서 `SchemaValidator` 의존 제거.
- **불사용 에러코드**: `INVALID_SECTION_VALUES`는 이제 미사용이 되지만, 커밋된 enum이라 **제거하지 않고 남긴다**(후속 정리). `INVALID_TEMPLATE_OPTIONS_SCHEMA`는 이미 작업 트리에서 제거됐다.
- **NPE 방지**: `TemplateResponse.from`은 `templateUid`가 null이면 null을 담는다(미영속 엔티티에서 NPE 방지 — 종전 잠재 버그 해소).
- **응답 포맷**: `ApiResponse.of(HttpStatus, body)`. 등록은 기존 관례(`ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED, …))`).
- **테스트 스타일**: JUnit 5 + Mockito(`@ExtendWith(MockitoExtension.class)`) + AssertJ. 한글_언더스코어.
- **빌드 훅**: 모든 Edit/Write가 전체 `./gradlew build`(Postgres/Redis/jwt.secret 필요)를 돈다. 인프라 부재로 실패해도 파일 반영됨.
- **커밋 금지(이 세션)**: 커밋 스텝은 건너뛴다. 작업 트리에만 남긴다.
- **범위 밖**: Invitation 저장 검증(계획 D), 템플릿 수정/삭제(+캐시 무효화), sections의 options/editable 세부 검증, 컴포넌트 상세를 조인해 응답에 펼치기, 템플릿 목록 조회.

---

### Task 0: 빌드 복구 — Component 테스트 수정 + 패키지 이동

**계획 A′(커밋됨)가 남긴 부채.** 현재 `compileTestJava`가 깨져 있어 이후 어떤 Task도 검증할 수 없다. 먼저 green으로 되돌린다. Task 1과 파일이 겹치지 않으므로 독립적이다.

**Files:**
- Move: `src/test/java/smally/server/domain/template/dto/ComponentCreateRequestTest.java` → `src/test/java/smally/server/domain/component/dto/ComponentCreateRequestTest.java`
- Move+Modify: `src/test/java/smally/server/domain/template/dto/ComponentResponseTest.java` → `src/test/java/smally/server/domain/component/dto/ComponentResponseTest.java`
- Move: `src/test/java/smally/server/domain/template/service/ComponentServiceImplTest.java` → `src/test/java/smally/server/domain/component/service/ComponentServiceImplTest.java`
- Move: `src/test/java/smally/server/domain/template/service/ComponentTypeServiceImplTest.java` → `src/test/java/smally/server/domain/component/service/ComponentTypeServiceImplTest.java`

**Interfaces:**
- Consumes: `Component.builder().name/.componentUId/.dataSchema/.optionSchema` + `setComponentType(ComponentType)`, `ComponentType.builder().name(String)`, `ComponentResponse(id, name, componentType, frontendBinding, optionSchema)`.
- Produces: 없음(테스트 전용). `compileTestJava` green.

- [x] **Step 0-1: 디렉터리 생성 + 4개 파일 이동**

```bash
mkdir -p src/test/java/smally/server/domain/component/dto \
         src/test/java/smally/server/domain/component/service
git mv src/test/java/smally/server/domain/template/dto/ComponentCreateRequestTest.java \
       src/test/java/smally/server/domain/component/dto/ComponentCreateRequestTest.java
git mv src/test/java/smally/server/domain/template/dto/ComponentResponseTest.java \
       src/test/java/smally/server/domain/component/dto/ComponentResponseTest.java
git mv src/test/java/smally/server/domain/template/service/ComponentServiceImplTest.java \
       src/test/java/smally/server/domain/component/service/ComponentServiceImplTest.java
git mv src/test/java/smally/server/domain/template/service/ComponentTypeServiceImplTest.java \
       src/test/java/smally/server/domain/component/service/ComponentTypeServiceImplTest.java
```
(`git mv`가 permission 등으로 막히면 일반 `mv`로 옮긴다.)

- [x] **Step 0-2: 이동한 4개 파일의 package 선언 교체 + 같은 패키지가 된 import 제거**

각 파일 1행의 `package smally.server.domain.template.dto;` → `package smally.server.domain.component.dto;`,
`package smally.server.domain.template.service;` → `package smally.server.domain.component.service;`.

같은 패키지가 되어 **불필요해진 import를 지운다**(남겨도 컴파일은 되지만 정리):
- `ComponentCreateRequestTest`: `import smally.server.domain.component.dto.ComponentCreateRequest;` 제거
- `ComponentResponseTest`: `import smally.server.domain.component.dto.ComponentResponse;` 제거
- `ComponentServiceImplTest`: `import smally.server.domain.component.service.ComponentServiceImpl;`, `import smally.server.domain.component.service.ComponentTypeService;` 제거
- `ComponentTypeServiceImplTest`: `import smally.server.domain.component.service.ComponentTypeServiceImpl;` 제거

**다른 패키지 참조 import는 그대로 둔다** (`...component.entity.Component`, `...component.repository.ComponentRepository`, `...component.dto.ComponentCreateRequest`(service 테스트 쪽) 등).

- [x] **Step 0-3: ComponentResponseTest를 현재 Component 모델에 맞게 수정**

`src/test/java/smally/server/domain/component/dto/ComponentResponseTest.java` 전체를 아래로 교체.
빌더에서 `.componentType("gallery")`(현 모델엔 없음 → `@Setter` 주입)와 `.frontendBinding("GalleryGrid")`(→ `.componentUId`)를 고친다. `ComponentResponse.from`이 `getComponentType().getName()`을 호출하므로 **실제 `ComponentType` 인스턴스가 필요**하다.

```java
package smally.server.domain.component.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.component.entity.Component;
import smally.server.domain.component.entity.ComponentType;

class ComponentResponseTest {

    @Test
    void from_editor_필드를_노출하고_dataSchema는_담지_않는다() {
        Map<String, Object> optionSchema = Map.of("type", "object");
        Component component = Component.builder()
                .name("클래식 갤러리")
                .componentUId("GalleryGrid")
                .dataSchema(Map.of("type", "object")) // 서버 검증 전용
                .optionSchema(optionSchema)
                .build();
        component.setComponentType(ComponentType.builder().name("gallery").build());

        ComponentResponse response = ComponentResponse.from(component);

        assertThat(response.name()).isEqualTo("클래식 갤러리");
        assertThat(response.componentType()).isEqualTo("gallery");
        assertThat(response.frontendBinding()).isEqualTo("GalleryGrid");
        assertThat(response.optionSchema()).isEqualTo(optionSchema);
        // dataSchema 접근자는 존재하지 않는다(레코드 컴포넌트 5개): 컴파일 계약으로 보장
    }
}
```

- [x] **Step 0-4: Component 테스트만 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.component.*' --console=plain`
Expected: PASS. (아직 Template 스테일 테스트 3개가 남아 `compileTestJava` 전체는 깨진 상태 — Task 1 Step 9에서 정리된다. 이 스텝이 컴파일 단계에서 막히면 Task 1 Step 9를 먼저 수행하고 되돌아온다.)

- [x] **Step 0-5: 커밋 — 건너뜀** (이 세션 커밋 금지)

---

### Task 1: Template 레시피 리팩터링 (프로덕션 + 스테일 테스트 제거)

`Template` 도메인을 레시피 모델로 원자적으로 전환한다. 결합이 강해 프로덕션을 한 번에 바꾸고, 삭제된 필드를 참조하는 기존 테스트 3개를 제거해 컴파일을 green으로 유지한다.

**Files:**
- Modify: `src/main/java/smally/server/domain/template/entity/Template.java`
- Modify: `src/main/java/smally/server/domain/template/dto/TemplateCreateRequest.java`
- Modify: `src/main/java/smally/server/domain/template/dto/TemplateResponse.java`
- Delete: `src/main/java/smally/server/domain/template/dto/VariantResponse.java`
- Delete: `src/main/java/smally/server/domain/template/service/InternalTemplateService.java`
- Modify: `src/main/java/smally/server/domain/template/service/TemplateService.java`
- Modify: `src/main/java/smally/server/domain/template/service/TemplateServiceImpl.java`
- Modify: `src/main/java/smally/server/domain/template/controller/TemplateController.java`
- Modify: `src/main/java/smally/server/core/exception/ErrorCode.java`
- Delete: `src/test/java/smally/server/domain/template/dto/VariantResponseTest.java`
- Delete: `src/test/java/smally/server/domain/template/dto/TemplateCreateRequestTest.java`
- Delete: `src/test/java/smally/server/domain/template/service/TemplateServiceImplTest.java`

**Interfaces:**
- Produces:
  - `Template` — getters `getTemplateUid/getName/getThumbnail/getCategory/getSections/getTheme`; 빌더 `.name/.thumbnail/.category/.sections/.theme`
  - `TemplateCreateRequest(String name, String thumbnail, String category, List<Map<String,Object>> sections, Map<String,Object> theme)` + `toTemplate()`
  - `TemplateResponse(String templateUid, String name, String thumbnail, String category, List<Map<String,Object>> sections, Map<String,Object> theme)` + `from(Template)`
  - `TemplateService`: `TemplateResponse createTemplate(TemplateCreateRequest)`, `TemplateResponse getTemplate(String templateUId)`
  - `ErrorCode.INVALID_TEMPLATE_RECIPE`, `ErrorCode.INVALID_TEMPLATE_THEME`

- [x] **Step 1: Template 엔티티 교체**

`src/main/java/smally/server/domain/template/entity/Template.java` 전체를 아래로 교체:

```java
package smally.server.domain.template.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import smally.server.domain.common.entity.BaseEntity;

@Entity
@Table(name = "templates", indexes = @Index(name = "idx_template_uid", columnList = "templateUid"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Template extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, columnDefinition = "UUID")
    private UUID templateUid;

    @Column(nullable = false)
    private String name;

    @Column
    private String thumbnail;

    @Column
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sections", nullable = false)
    private List<Map<String, Object>> sections;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "theme")
    private Map<String, Object> theme;

    @Builder
    private Template(String name, String thumbnail, String category,
                     List<Map<String, Object>> sections, Map<String, Object> theme) {
        this.name = name;
        this.thumbnail = thumbnail;
        this.category = category;
        this.sections = sections;
        this.theme = theme;
    }

    @PrePersist
    protected void onCreate() {
        if (this.templateUid == null) {
            this.templateUid = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
```

- [x] **Step 2: TemplateCreateRequest 교체**

`src/main/java/smally/server/domain/template/dto/TemplateCreateRequest.java` 전체를 아래로 교체:

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
        String thumbnail,
        @NotBlank(message = "카테고리는 필수입니다.")
        String category,
        @NotNull(message = "섹션 구성은 필수입니다.")
        List<Map<String, Object>> sections,
        Map<String, Object> theme
) {
    public Template toTemplate() {
        return Template.builder()
                .name(name)
                .thumbnail(thumbnail)
                .category(category)
                .sections(sections)
                .theme(theme)
                .build();
    }
}
```

- [x] **Step 3: TemplateResponse 교체 (전체 상세 + templateUid null-guard)**

`src/main/java/smally/server/domain/template/dto/TemplateResponse.java` 전체를 아래로 교체:

```java
package smally.server.domain.template.dto;

import java.util.List;
import java.util.Map;
import smally.server.domain.template.entity.Template;

public record TemplateResponse(
        String templateUid,
        String name,
        String thumbnail,
        String category,
        List<Map<String, Object>> sections,
        Map<String, Object> theme
) {
    public static TemplateResponse from(Template template) {
        return new TemplateResponse(
                template.getTemplateUid() == null ? null : template.getTemplateUid().toString(),
                template.getName(),
                template.getThumbnail(),
                template.getCategory(),
                template.getSections(),
                template.getTheme()
        );
    }
}
```

- [x] **Step 4: 삭제 — VariantResponse, InternalTemplateService**

```bash
git rm -q src/main/java/smally/server/domain/template/dto/VariantResponse.java \
          src/main/java/smally/server/domain/template/service/InternalTemplateService.java
```
(git rm이 permission 등으로 막히면 파일을 직접 삭제한다.)

- [x] **Step 5: TemplateService 인터페이스 교체**

`src/main/java/smally/server/domain/template/service/TemplateService.java` 전체를 아래로 교체(`getVariant`/`VariantResponse` 제거, `getTemplate` 추가):

```java
package smally.server.domain.template.service;

import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;

public interface TemplateService {
    TemplateResponse createTemplate(TemplateCreateRequest request);
    TemplateResponse getTemplate(String templateUId);
}
```

- [x] **Step 6: TemplateServiceImpl 교체 (레시피·theme 검증 + Redis 캐싱, isValidTemplate 제거)**

`src/main/java/smally/server/domain/template/service/TemplateServiceImpl.java` 전체를 아래로 교체.
캐싱은 `ComponentServiceImpl`과 동일 패턴(상수 3개 → 조회 시 캐시 우선, miss 시 `log.warn` 후 DB → 적재; 등록 시 적재). 목록 캐시는 템플릿 목록 API가 없으므로 두지 않는다.

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
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.TemplateException;
import smally.server.domain.component.service.ComponentService;
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

    private final TemplateRepository templateRepository;
    private final CategoryService categoryService;
    private final ComponentService componentService;
    private final OptionDefinitionService optionDefinitionService;
    private final RedisCacheService redisCacheService;

    @Override
    @Transactional
    public TemplateResponse createTemplate(TemplateCreateRequest request) {
        categoryService.validateExists(request.category());
        validateRecipe(request.sections());
        validateTheme(request.theme());

        Template template = request.toTemplate();
        templateRepository.save(template);

        TemplateResponse response = TemplateResponse.from(template);
        if (response.templateUid() != null) {
            redisCacheService.setCacheData(
                    TEMPLATE_CACHE_KEY + response.templateUid(), response, TEMPLATE_CACHE_TTL);
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateResponse getTemplate(String templateUId) {
        String templateKey = TEMPLATE_CACHE_KEY + templateUId;
        TemplateResponse cached = redisCacheService.getCacheData(templateKey, TemplateResponse.class);

        if (cached != null) {
            return cached;
        }
        log.warn("[Cache-miss] template detail cache miss key : {}", templateUId);
        TemplateResponse response = TemplateResponse.from(getTemplateEntity(templateUId));
        redisCacheService.setCacheData(templateKey, response, TEMPLATE_CACHE_TTL);

        return response;
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
            componentService.getComponent(componentUid); // 없으면 COMPONENT_NOT_FOUND
        }
    }

    private void validateTheme(Map<String, Object> theme) {
        if (theme == null) {
            return;
        }
        theme.forEach((key, value) -> {
            OptionDefinition definition = optionDefinitionService.getOptionDefinition(key); // 없으면 OPTION_DEFINITION_NOT_FOUND
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

> 캐싱 주의: `createTemplate`의 `templateUid`는 `@PrePersist`에서 채워지므로 `save()` 이후에 읽어야 한다. mock 리포지토리를 쓰는 단위 테스트에서는 `@PrePersist`가 안 돌아 null이므로 `templateUid != null` 가드가 "TPL:null" 키 적재를 막는다(Component 패턴 대비 추가한 가드).

- [x] **Step 7: TemplateController 교체 (getVariant → getTemplate)**

`src/main/java/smally/server/domain/template/controller/TemplateController.java` 전체를 아래로 교체:

```java
package smally.server.domain.template.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smally.server.core.dto.ApiResponse;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.service.TemplateService;

@RestController
@RequestMapping("/api/template")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @PostMapping("/v1")
    public ResponseEntity<ApiResponse<TemplateResponse>> createTemplate(
            @RequestBody @Valid TemplateCreateRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.of(HttpStatus.CREATED, templateService.createTemplate(request)));
    }

    @GetMapping("/v1/{templateUID}")
    public ResponseEntity<ApiResponse<TemplateResponse>> getTemplate(
            @PathVariable String templateUID
    ) {
        return ResponseEntity.ok(
                ApiResponse.of(HttpStatus.OK, templateService.getTemplate(templateUID)));
    }
}
```

- [x] **Step 8: ErrorCode 추가**

`ErrorCode.java`에서 마지막 항목 `INVALID_OPTION_DEFAULT(...)`(계획 B에서 추가됨)를 다음으로 교체(세미콜론은 마지막에 유지):

```java
    INVALID_OPTION_DEFAULT(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "기본값이 허용값 목록에 없습니다."),
    INVALID_TEMPLATE_RECIPE(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "템플릿 레시피가 유효하지 않습니다."),
    INVALID_TEMPLATE_THEME(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "theme 값이 옵션 정의의 허용값에 없습니다.");
```

- [x] **Step 9: 스테일 테스트 3개 삭제**

```bash
git rm -q src/test/java/smally/server/domain/template/dto/VariantResponseTest.java \
          src/test/java/smally/server/domain/template/dto/TemplateCreateRequestTest.java \
          src/test/java/smally/server/domain/template/service/TemplateServiceImplTest.java
```

- [x] **Step 10: 컴파일 확인 (리팩터가 green인지)**

Run: `./gradlew compileJava compileTestJava --console=plain`
Expected: BUILD SUCCESSFUL. **Task 0이 끝났다는 전제 하에** 이 스텝에서 처음으로 전체가 green이 된다(초안은 Task 0 없이 green을 기대했으나 `ComponentResponseTest` 때문에 불가능했다). 실패 시 남은 참조를 찾아 정리.

- [x] **Step 11: 커밋 — 건너뜀** (이 세션 커밋 금지)

---

### Task 2: 새 테스트 (레시피·theme 검증, 캐싱, DTO 매핑, 응답)

Task 1이 지운 커버리지를 새 모델 기준으로 다시 세운다.

**Files:**
- Test: `src/test/java/smally/server/domain/template/dto/TemplateCreateRequestTest.java`
- Test: `src/test/java/smally/server/domain/template/dto/TemplateResponseTest.java`
- Test: `src/test/java/smally/server/domain/template/service/TemplateServiceImplTest.java`

**Interfaces:**
- Consumes: `TemplateCreateRequest`/`TemplateResponse`/`TemplateService`(Task 1), `ComponentService.getComponent(String)`(A′), `OptionDefinitionService.getOptionDefinition(String)`+`OptionDefinition`(B), `RedisCacheService`(core).

- [x] **Step 1: TemplateCreateRequestTest 작성**

`src/test/java/smally/server/domain/template/dto/TemplateCreateRequestTest.java`:

```java
package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.template.entity.Template;

class TemplateCreateRequestTest {

    @Test
    void toTemplate_sections와_theme를_매핑한다() {
        List<Map<String, Object>> sections = List.of(
                Map.of("componentUId", "GalleryGrid", "options", Map.of("titleSize", "L"), "editable", List.of("titleSize")));
        Map<String, Object> theme = Map.of("fontSize", "large");

        TemplateCreateRequest request = new TemplateCreateRequest(
                "클래식 화이트", "https://cdn/thumb.png", "클래식", sections, theme);

        Template template = request.toTemplate();

        assertThat(template.getName()).isEqualTo("클래식 화이트");
        assertThat(template.getCategory()).isEqualTo("클래식");
        assertThat(template.getSections()).isEqualTo(sections);
        assertThat(template.getTheme()).isEqualTo(theme);
    }
}
```

- [x] **Step 2: TemplateResponseTest 작성**

`src/test/java/smally/server/domain/template/dto/TemplateResponseTest.java`:

```java
package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.template.entity.Template;

class TemplateResponseTest {

    @Test
    void from_필드를_노출하고_templateUid가_null이면_null을_담는다() {
        List<Map<String, Object>> sections = List.of(Map.of("componentUId", "GalleryGrid"));
        Map<String, Object> theme = Map.of("fontSize", "large");
        Template template = Template.builder()
                .name("클래식 화이트").thumbnail("t.png").category("클래식")
                .sections(sections).theme(theme)
                .build(); // templateUid는 @PrePersist 전이라 null

        TemplateResponse response = TemplateResponse.from(template);

        assertThat(response.templateUid()).isNull();
        assertThat(response.name()).isEqualTo("클래식 화이트");
        assertThat(response.sections()).isEqualTo(sections);
        assertThat(response.theme()).isEqualTo(theme);
    }
}
```

- [x] **Step 3: 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.TemplateCreateRequestTest' --tests 'smally.server.domain.template.dto.TemplateResponseTest'`
Expected: PASS (Task 1이 프로덕션을 이미 바꿔놨으므로 바로 통과)

- [x] **Step 4: TemplateServiceImplTest 작성 (캐싱 포함 9개)**

`src/test/java/smally/server/domain/template/service/TemplateServiceImplTest.java`:

```java
package smally.server.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentException;
import smally.server.core.exception.exceptions.OptionDefinitionException;
import smally.server.core.exception.exceptions.TemplateException;
import smally.server.domain.component.service.ComponentService;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.entity.OptionDefinition;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.repository.TemplateRepository;

@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    @Mock TemplateRepository templateRepository;
    @Mock CategoryService categoryService;
    @Mock ComponentService componentService;
    @Mock OptionDefinitionService optionDefinitionService;
    @Mock RedisCacheService redisCacheService;

    TemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TemplateServiceImpl(
                templateRepository, categoryService, componentService,
                optionDefinitionService, redisCacheService);
    }

    private TemplateCreateRequest request(List<Map<String, Object>> sections, Map<String, Object> theme) {
        return new TemplateCreateRequest("클래식", null, "클래식", sections, theme);
    }

    @Test
    void createTemplate_섹션이_비어있으면_INVALID_TEMPLATE_RECIPE() {
        assertThatThrownBy(() -> service.createTemplate(request(List.of(), null)))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TEMPLATE_RECIPE);

        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_섹션에_componentUId가_없으면_INVALID_TEMPLATE_RECIPE() {
        assertThatThrownBy(() -> service.createTemplate(
                request(List.of(Map.of("options", Map.of("titleSize", "L"))), null)))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TEMPLATE_RECIPE);

        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_참조_컴포넌트가_없으면_거절하고_저장하지_않는다() {
        doThrow(new ComponentException(ErrorCode.COMPONENT_NOT_FOUND))
                .when(componentService).getComponent("NoSuchComponent");

        assertThatThrownBy(() -> service.createTemplate(
                request(List.of(Map.of("componentUId", "NoSuchComponent")), null)))
                .isInstanceOf(ComponentException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMPONENT_NOT_FOUND);

        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_theme_키가_카탈로그에_없으면_거절한다() {
        doThrow(new OptionDefinitionException(ErrorCode.OPTION_DEFINITION_NOT_FOUND))
                .when(optionDefinitionService).getOptionDefinition("fontSize");

        assertThatThrownBy(() -> service.createTemplate(
                request(List.of(Map.of("componentUId", "GalleryGrid")), Map.of("fontSize", "large"))))
                .isInstanceOf(OptionDefinitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OPTION_DEFINITION_NOT_FOUND);

        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_theme_값이_허용값에_없으면_INVALID_TEMPLATE_THEME() {
        OptionDefinition fontSize = OptionDefinition.builder()
                .key("fontSize").label("폰트 크기").controlType("select").scope("global")
                .allowedValues(List.of("small", "large")).defaultValue("small")
                .build();
        when(optionDefinitionService.getOptionDefinition("fontSize")).thenReturn(fontSize);

        assertThatThrownBy(() -> service.createTemplate(
                request(List.of(Map.of("componentUId", "GalleryGrid")), Map.of("fontSize", "huge"))))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TEMPLATE_THEME);

        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_유효하면_저장한다() {
        assertThatCode(() -> service.createTemplate(
                request(List.of(Map.of("componentUId", "GalleryGrid")), null))).doesNotThrowAnyException();

        verify(templateRepository).save(any(Template.class));
    }

    @Test
    void getTemplate_캐시에_있으면_DB를_조회하지_않는다() {
        String uid = UUID.randomUUID().toString();
        TemplateResponse cached = new TemplateResponse(
                uid, "클래식", null, "클래식", List.of(Map.of("componentUId", "GalleryGrid")), null);
        when(redisCacheService.getCacheData("TPL:" + uid, TemplateResponse.class)).thenReturn(cached);

        assertThat(service.getTemplate(uid)).isEqualTo(cached);

        verifyNoInteractions(templateRepository);
    }

    @Test
    void getTemplate_캐시가_비면_DB를_조회하고_캐시에_적재한다() {
        UUID uid = UUID.randomUUID();
        Template template = Template.builder()
                .name("클래식").category("클래식")
                .sections(List.of(Map.of("componentUId", "GalleryGrid"))).build();
        when(templateRepository.findTemplateByTemplateUid(uid)).thenReturn(Optional.of(template));

        TemplateResponse response = service.getTemplate(uid.toString());

        assertThat(response.name()).isEqualTo("클래식");
        verify(redisCacheService).setCacheData(eq("TPL:" + uid), eq(response), any());
    }

    @Test
    void getTemplate_없으면_TEMPLATE_NOT_FOUND() {
        when(templateRepository.findTemplateByTemplateUid(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTemplate(UUID.randomUUID().toString()))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TEMPLATE_NOT_FOUND);
    }
}
```

> 참고: `유효하면_저장한다`는 theme=null이라 optionDefinitionService를 안 탄다. `componentService.getComponent("GalleryGrid")`는 stub 없이 mock 기본값(null 반환)이며, 서비스는 반환값을 쓰지 않고 "예외 없이 돌아오면 존재"로만 취급하므로 통과한다. `categoryService.validateExists`는 void mock이라 no-op. mock 리포지토리는 `@PrePersist`를 돌리지 않아 `templateUid`가 null이므로 `TemplateResponse.from`의 null-guard가 NPE를 막고, 서비스의 `templateUid != null` 가드가 "TPL:null" 적재를 막는다(ReflectionTestUtils 불필요).

- [x] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.service.TemplateServiceImplTest'`
Expected: 9개 PASS

- [x] **Step 6: 전체 테스트 확인**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL (Task 0 + Task 1 + Task 2 합산 green)

- [x] **Step 7: 커밋 — 건너뜀**

---

## 완료 기준

- **`compileTestJava` green** — Task 0이 `ComponentResponseTest`를 고치고 Component 테스트 4개를 `domain/component` 아래로 옮겼다(초안엔 없던 선결 조건).
- `Template` = `sections`(List<Map> jsonb) + `theme`(Map jsonb) + 메타. `section_schema`/`variants` 제거.
- `VariantResponse`/`isValidTemplate`/`InternalTemplateService` 제거(외부 참조 없음 확인됨).
- 등록 시 레시피 유효성(각 섹션의 `componentUId`가 문자열이고 해당 컴포넌트가 존재) + theme 값(OptionDefinition 카탈로그) 검증.
- `getTemplate` 조회가 `TPL:` 캐시를 우선 사용하고 miss 시 DB → 적재(Component와 동일 패턴).
- `TemplateResponse`는 전체 상세, `templateUid` null-guard.
- 새 테스트: TemplateCreateRequest 1, TemplateResponse 1, TemplateServiceImpl 9 = 11. 전체 테스트 green.

## 다음 계획 (ADR-007 하위)

- **계획 D**: Invitation 저장 시 `templateUid`로 템플릿을 로드 → 각 섹션 인스턴스의 `componentUId`로 `Component.data_schema`를 로드 → `SchemaValidator.validateData`로 `section_values` 검증. + `selected_options`(편집 가능 옵션) 검증.
- **후속 정리**: 미사용 `INVALID_SECTION_VALUES` 제거, `ComponentResponse.frontendBinding` 네이밍 통일, `Component.@Builder`에 `componentType` 포함(2단 생성 제거), 템플릿 수정/삭제 + 캐시 무효화.
