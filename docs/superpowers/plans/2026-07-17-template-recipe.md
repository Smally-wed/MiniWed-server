# Template 레시피 전환 (계획 C) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 `Template`(데이터 검증용 스키마 덩어리)을 **레시피**(`sections` 순서 리스트 + `theme`)로 전환한다. 등록 시 참조 컴포넌트 존재(레시피 유효성)와 theme 값(OptionDefinition 카탈로그)을 검증한다. (ADR-007 하위 계획 C)

**Architecture:** `Template`에서 `section_schema`/`variants`를 걷어내고 `sections`(`List<Map>` jsonb)+`theme`(`Map` jsonb)를 넣는다. 데이터 검증은 컴포넌트 레벨로 이미 옮겼으므로(계획 A′) 템플릿의 `isValidTemplate`/`InternalTemplateService`/`VariantResponse`는 제거한다. 등록 검증은 `ComponentService.getComponent`(존재)와 `OptionDefinitionService.getOptionDefinition`(+allowedValues)로 수행한다. 이 리팩터는 **기존 커밋된 Template 코드**를 뜯으므로 결합이 강하다 — Task 1이 프로덕션 전체를 원자적으로 바꾸고(컴파일 green 유지 위해 스테일 테스트 3개 삭제), Task 2가 새 테스트를 붙인다.

**Tech Stack:** Spring Boot 4.1.0, Java 25, Spring Data JPA, PostgreSQL jsonb(`@JdbcTypeCode(SqlTypes.JSON)`), Jackson 3.

## Global Constraints

- **선행 의존(작업 트리)**: 계획 A′의 `ComponentService.getComponent(String componentUid)` → `ComponentResponse`(없으면 `COMPONENT_NOT_FOUND`), 계획 B의 `OptionDefinitionService.getOptionDefinition(String)`(없으면 `OPTION_DEFINITION_NOT_FOUND`)+`OptionDefinition.getAllowedValues()`. 둘 다 존재한다(확인함).
- **컴포넌트 참조 키**: 레시피는 컴포넌트를 PK(`id`)가 아니라 **`componentUId`(String, 고유·불변)**로 지목한다. 이게 컴포넌트의 외부 식별자이자 프론트 연결 키이기 때문이다(ADR-007 결정 1).
- **sections 형태**: `List<Map<String,Object>>`(요청·엔티티 공통, 코드베이스의 jsonb-Map 관례). 각 항목: `{ "componentUId": "<string>", "options": {…고정값}, "editable": [ …키 ] }`. 서버는 `componentUId`만 방어적으로 추출·검증한다(options/editable 세부 검증은 후속).
- **theme 형태**: `Map<String,Object>`(`{ optionKey: 값 }`). nullable.
- **제거 대상**: `Template.sectionSchema/variants`, `VariantResponse`(+테스트), `InternalTemplateService`+`isValidTemplate`. (외부 참조 없음을 확인함. `optionsSchema` 필드는 이미 작업 트리에서 제거되어 있다.)
- **엔진**: 이 계획은 `SchemaValidator`를 쓰지 않는다(데이터검증은 컴포넌트/청첩장으로 이동). `TemplateServiceImpl`에서 `SchemaValidator` 의존 제거.
- **불사용 에러코드**: `INVALID_SECTION_VALUES`는 이제 미사용이 되지만, 커밋된 enum이라 **제거하지 않고 남긴다**(후속 정리). 새 코드만 추가. (`INVALID_TEMPLATE_OPTIONS_SCHEMA`는 이미 작업 트리에서 제거됐다.)
- **선행 조건**: 작업 트리의 `compileTestJava`는 지금 실패 상태다(컴포넌트 테스트가 옛 모델 참조 + 아래 Task 1이 지울 템플릿 테스트 3개). 이 계획의 Step 10 컴파일 확인은 그 정리가 끝난 뒤에야 green이 된다.
- **NPE 방지**: `TemplateResponse.from`은 `templateUid`가 null이면 null을 담는다(미영속 엔티티에서 NPE 방지 — 종전 잠재 버그 해소).
- **응답 포맷**: `ApiResponse.of(HttpStatus, body)`. 등록은 기존 관례(`ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED, …))`).
- **테스트 스타일**: JUnit 5 + Mockito(`@ExtendWith(MockitoExtension.class)`) + AssertJ. 한글_언더스코어.
- **빌드 훅**: 모든 Edit/Write가 전체 `./gradlew build`(Postgres/Redis/jwt.secret 필요)를 돈다. 인프라 부재로 실패해도 파일 반영됨.
- **커밋 금지(이 세션)**: 커밋 스텝은 건너뛴다. 작업 트리에만 남긴다.
- **범위 밖**: Invitation 저장 검증(계획 D), 템플릿 수정/삭제, sections의 options/editable 세부 검증, 컴포넌트 상세를 조인해 응답에 펼치기.

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

- [ ] **Step 1: Template 엔티티 교체**

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

- [ ] **Step 2: TemplateCreateRequest 교체**

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

- [ ] **Step 3: TemplateResponse 교체 (전체 상세 + templateUid null-guard)**

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

- [ ] **Step 4: 삭제 — VariantResponse, InternalTemplateService**

```bash
git rm -q src/main/java/smally/server/domain/template/dto/VariantResponse.java \
          src/main/java/smally/server/domain/template/service/InternalTemplateService.java
```
(git rm이 permission 등으로 막히면 파일을 직접 삭제한다.)

- [ ] **Step 5: TemplateService 인터페이스 교체**

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

- [ ] **Step 6: TemplateServiceImpl 교체 (레시피·theme 검증, isValidTemplate 제거)**

`src/main/java/smally/server/domain/template/service/TemplateServiceImpl.java` 전체를 아래로 교체:

```java
package smally.server.domain.template.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
public class TemplateServiceImpl implements TemplateService {

    private final TemplateRepository templateRepository;
    private final CategoryService categoryService;
    private final ComponentService componentService;
    private final OptionDefinitionService optionDefinitionService;

    @Override
    @Transactional
    public TemplateResponse createTemplate(TemplateCreateRequest request) {
        categoryService.validateExists(request.category());
        validateRecipe(request.sections());
        validateTheme(request.theme());

        Template template = request.toTemplate();
        templateRepository.save(template);
        return TemplateResponse.from(template);
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateResponse getTemplate(String templateUId) {
        return TemplateResponse.from(getTemplateEntity(templateUId));
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

- [ ] **Step 7: TemplateController 교체 (getVariant → getTemplate)**

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

- [ ] **Step 8: ErrorCode 추가**

`ErrorCode.java`에서 마지막 항목 `INVALID_OPTION_DEFAULT(...)`(계획 B에서 추가됨)를 다음으로 교체(세미콜론은 마지막에 유지):

```java
    INVALID_OPTION_DEFAULT(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "기본값이 허용값 목록에 없습니다."),
    INVALID_TEMPLATE_RECIPE(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "템플릿 레시피가 유효하지 않습니다."),
    INVALID_TEMPLATE_THEME(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "theme 값이 옵션 정의의 허용값에 없습니다.");
```

- [ ] **Step 9: 스테일 테스트 3개 삭제**

```bash
git rm -q src/test/java/smally/server/domain/template/dto/VariantResponseTest.java \
          src/test/java/smally/server/domain/template/dto/TemplateCreateRequestTest.java \
          src/test/java/smally/server/domain/template/service/TemplateServiceImplTest.java
```

- [ ] **Step 10: 컴파일 확인 (리팩터가 green인지)**

Run: `./gradlew compileJava compileTestJava --console=plain`
Expected: BUILD SUCCESSFUL (삭제된 필드/타입에 대한 참조 없음). 실패 시 남은 참조를 찾아 정리.

- [ ] **Step 11: 커밋 — 건너뜀** (이 세션 커밋 금지)

---

### Task 2: 새 테스트 (레시피·theme 검증, DTO 매핑, 응답)

Task 1이 지운 커버리지를 새 모델 기준으로 다시 세운다.

**Files:**
- Test: `src/test/java/smally/server/domain/template/dto/TemplateCreateRequestTest.java`
- Test: `src/test/java/smally/server/domain/template/dto/TemplateResponseTest.java`
- Test: `src/test/java/smally/server/domain/template/service/TemplateServiceImplTest.java`

**Interfaces:**
- Consumes: `TemplateCreateRequest`/`TemplateResponse`/`TemplateService`(Task 1), `ComponentService.getComponent(String)`(A′), `OptionDefinitionService.getOptionDefinition(String)`+`OptionDefinition`(B).

- [ ] **Step 1: TemplateCreateRequestTest 작성**

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

- [ ] **Step 2: TemplateResponseTest 작성**

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

- [ ] **Step 3: 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.TemplateCreateRequestTest' --tests 'smally.server.domain.template.dto.TemplateResponseTest'`
Expected: PASS (Task 1이 프로덕션을 이미 바꿔놨으므로 바로 통과)

- [ ] **Step 4: TemplateServiceImplTest 작성**

`src/test/java/smally/server/domain/template/service/TemplateServiceImplTest.java`:

```java
package smally.server.domain.template.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentException;
import smally.server.core.exception.exceptions.OptionDefinitionException;
import smally.server.core.exception.exceptions.TemplateException;
import smally.server.domain.component.service.ComponentService;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.entity.OptionDefinition;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.repository.TemplateRepository;

@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    @Mock TemplateRepository templateRepository;
    @Mock CategoryService categoryService;
    @Mock ComponentService componentService;
    @Mock OptionDefinitionService optionDefinitionService;

    TemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TemplateServiceImpl(
                templateRepository, categoryService, componentService, optionDefinitionService);
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
    void getTemplate_없으면_TEMPLATE_NOT_FOUND() {
        when(templateRepository.findTemplateByTemplateUid(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTemplate(UUID.randomUUID().toString()))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TEMPLATE_NOT_FOUND);
    }
}
```

> 참고: `유효하면_저장한다`는 theme=null이라 optionDefinitionService를 안 탄다. `componentService.getComponent("GalleryGrid")`는 stub 없이 mock 기본값(null 반환)이며, 서비스는 반환값을 쓰지 않고 "예외 없이 돌아오면 존재"로만 취급하므로 통과한다. `categoryService.validateExists`는 void mock이라 no-op. `TemplateResponse.from`의 null-guard 덕에 미영속 엔티티에서도 NPE가 없다(ReflectionTestUtils 불필요).

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.service.TemplateServiceImplTest'`
Expected: 7개 PASS

- [ ] **Step 6: 커밋 — 건너뜀**

---

## 완료 기준

- `Template` = `sections`(List<Map> jsonb) + `theme`(Map jsonb) + 메타. `section_schema`/`variants` 제거.
- `VariantResponse`/`isValidTemplate`/`InternalTemplateService` 제거(외부 참조 없음 확인됨).
- 등록 시 레시피 유효성(각 섹션의 `componentUId`가 문자열이고 해당 컴포넌트가 존재) + theme 값(OptionDefinition 카탈로그) 검증.
- `TemplateResponse`는 전체 상세, `templateUid` null-guard.
- 새 테스트: TemplateCreateRequest 1, TemplateResponse 1, TemplateServiceImpl 7 = 9. 리팩터 후 전체 컴파일 green.

## 다음 계획 (ADR-007 하위)

- **계획 D**: Invitation 저장 시 `templateUid`로 템플릿을 로드 → 각 섹션 인스턴스의 `componentUId`로 `Component.data_schema`를 로드 → `SchemaValidator.validateData`로 `section_values` 검증. + `selected_options`(편집 가능 옵션) 검증.
