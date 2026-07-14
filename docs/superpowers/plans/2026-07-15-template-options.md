# 템플릿 옵션(options_schema) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 템플릿에 사용자 선택지 정의(`options_schema`)를 추가하고, 등록 시 메타검증·조회 시 프론트 노출까지 서버 측을 구현한다.

**Architecture:** `section_schema`(콘텐츠 데이터 검증) 옆에 두 번째 JSON Schema `options_schema`(선택지 정의)를 나란히 둔다. 인라인 networknt 검증 코드를 공용 `SchemaValidator`로 추출해 메타검증(등록)·데이터검증(청첩장)이 같은 엔진을 공유한다. `options_schema`는 기존 `VariantResponse`(프론트 선택지 엔드포인트)로 노출한다.

**Tech Stack:** Spring Boot 4.1.0, Java 25, networknt json-schema-validator 3.0.6(신 API, draft 2020-12), Jackson 3(`tools.jackson`), PostgreSQL jsonb(Hibernate `@JdbcTypeCode(SqlTypes.JSON)`).

## Global Constraints

- **JSON Schema 검증기**: `com.networknt:json-schema-validator:3.0.6` 신 API만 사용 — `SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)`, `registry.getSchema(SchemaLocation)` / `getSchema(String, InputFormat.JSON)`, `schema.validate(String, InputFormat.JSON)` → `List<com.networknt.schema.Error>`, `Error.getMessage()`.
- **Jackson 3**: `tools.jackson.databind.ObjectMapper`. `writeValueAsString`은 unchecked `JacksonException`을 던지므로 try/catch 불필요(기존 코드와 동일). 테스트에서는 `new ObjectMapper()` 사용 가능.
- **테스트 스타일**: JUnit 5 + Mockito(`@ExtendWith(MockitoExtension.class)`, `@Mock`) + AssertJ(`assertThat`, `assertThatThrownBy`). 메서드명은 한글_언더스코어.
- **예외**: `TemplateException(ErrorCode)`, `BusinessException`은 `@Getter`로 `getErrorCode()` 노출(assertj `hasFieldOrPropertyWithValue("errorCode", ...)` 가능).
- **`options_schema`는 선택(nullable)**: 옵션이 없는 템플릿도 허용. 메타검증은 값이 있을 때만 수행.
- **빌드 훅**: 모든 Edit/Write가 `./gradlew build` 전체를 돌린다(Postgres/Redis/jwt.secret 필요). `@SpringBootTest` 전체는 인프라가 없으면 실패할 수 있으나, 이 계획의 실질 검증은 각 태스크의 **단위/슬라이스 테스트**(Spring 컨텍스트 불필요)다. 훅이 인프라 부재로 실패해도 파일은 반영된다.
- **커밋**: 태스크마다 커밋. 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- **범위 밖(스펙 §7 후속)**: 청첩장 `selected_options` 검증 연동, `x-editable:false` 후처리, section_schema 메타검증 미구현분(별개 과제).

---

### Task 1: SchemaValidator 공용 컴포넌트

인라인 networknt 사용을 공용 컴포넌트로 추출한다. 메타검증(스키마 자체가 유효한 JSON Schema인가)과 데이터검증(값이 스키마를 지키는가)을 한 곳에서 제공한다.

**Files:**
- Create: `src/main/java/smally/server/core/validation/SchemaValidator.java`
- Test: `src/test/java/smally/server/core/validation/SchemaValidatorTest.java`

**Interfaces:**
- Produces:
  - `List<String> validateSchema(Map<String,Object> schema)` — 메타검증. 위반 메시지 목록(빈 목록=통과).
  - `List<String> validateData(Map<String,Object> schema, Map<String,Object> data)` — 데이터검증. 위반 메시지 목록(빈 목록=통과).

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/core/validation/SchemaValidatorTest.java`:

```java
package smally.server.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SchemaValidatorTest {

    private final SchemaValidator validator = new SchemaValidator(new ObjectMapper());

    @Test
    void validateSchema_유효한_스키마면_빈_목록() {
        assertThat(validator.validateSchema(Map.of("type", "object"))).isEmpty();
    }

    @Test
    void validateSchema_type이_문자열이_아니면_위반() {
        assertThat(validator.validateSchema(Map.of("type", 123))).isNotEmpty();
    }

    @Test
    void validateData_enum에_없는_값이면_위반() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("fontSize", Map.of("enum", List.of("small", "large"))));
        assertThat(validator.validateData(schema, Map.of("fontSize", "huge"))).isNotEmpty();
    }

    @Test
    void validateData_enum에_있는_값이면_통과() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("fontSize", Map.of("enum", List.of("small", "large"))));
        assertThat(validator.validateData(schema, Map.of("fontSize", "small"))).isEmpty();
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests 'smally.server.core.validation.SchemaValidatorTest'`
Expected: 컴파일 실패 (`SchemaValidator` 클래스 없음)

- [ ] **Step 3: SchemaValidator 구현**

`src/main/java/smally/server/core/validation/SchemaValidator.java`:

```java
package smally.server.core.validation;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * JSON Schema 공용 검증기.
 * - validateSchema: 스키마 자체가 유효한 JSON Schema(draft 2020-12)인지 메타검증.
 * - validateData:   값이 주어진 스키마를 지키는지 데이터검증.
 * 둘 다 위반 메시지 목록을 반환한다(빈 목록 = 통과). ErrorCode 매핑은 호출자 책임.
 */
@Component
@RequiredArgsConstructor
public class SchemaValidator {

    private static final SchemaRegistry REGISTRY =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private static final SchemaLocation META_2020_12 =
            SchemaLocation.of("https://json-schema.org/draft/2020-12/schema");

    private final ObjectMapper objectMapper;

    public List<String> validateSchema(Map<String, Object> schema) {
        Schema metaSchema = REGISTRY.getSchema(META_2020_12);
        List<Error> errors = metaSchema.validate(
                objectMapper.writeValueAsString(schema), InputFormat.JSON);
        return errors.stream().map(Error::getMessage).toList();
    }

    public List<String> validateData(Map<String, Object> schema, Map<String, Object> data) {
        Schema compiled = REGISTRY.getSchema(
                objectMapper.writeValueAsString(schema), InputFormat.JSON);
        List<Error> errors = compiled.validate(
                objectMapper.writeValueAsString(data), InputFormat.JSON);
        return errors.stream().map(Error::getMessage).toList();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.core.validation.SchemaValidatorTest'`
Expected: 4개 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/core/validation/SchemaValidator.java src/test/java/smally/server/core/validation/SchemaValidatorTest.java
git commit -m "feat : JSON Schema 공용 SchemaValidator 추출(메타검증+데이터검증)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: isValidTemplate를 SchemaValidator로 리팩터링

기존 `TemplateServiceImpl.isValidTemplate`의 인라인 networknt 코드를 `SchemaValidator.validateData` 호출로 교체한다. 동작(검증 실패 시 `INVALID_SECTION_VALUES`)은 그대로 유지한다.

**Files:**
- Modify: `src/main/java/smally/server/domain/template/service/TemplateServiceImpl.java`
- Test: `src/test/java/smally/server/domain/template/service/TemplateServiceImplTest.java`

**Interfaces:**
- Consumes: `SchemaValidator.validateData(Map, Map)` (Task 1)
- Produces: `TemplateServiceImpl` 생성자 시그니처 변경 → `(TemplateRepository, CategoryService, SchemaValidator)` (기존 `ObjectMapper` 필드 제거)

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/template/service/TemplateServiceImplTest.java`:

```java
package smally.server.domain.template.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import smally.server.core.exception.exceptions.TemplateException;
import smally.server.core.validation.SchemaValidator;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.repository.TemplateRepository;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    @Mock TemplateRepository templateRepository;
    @Mock CategoryService categoryService;

    SchemaValidator schemaValidator = new SchemaValidator(new ObjectMapper());
    TemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TemplateServiceImpl(templateRepository, categoryService, schemaValidator);
    }

    @Test
    void isValidTemplate_입력값이_스키마를_어기면_INVALID_SECTION_VALUES() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("greeting", Map.of("type", "string")),
                "required", List.of("greeting"));
        Template template = Template.builder().name("t").category("c").sectionSchema(schema).build();
        when(templateRepository.findTemplateByTemplateUid(any())).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.isValidTemplate(UUID.randomUUID().toString(), Map.of()))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_SECTION_VALUES);
    }

    @Test
    void isValidTemplate_입력값이_스키마를_지키면_통과() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("greeting", Map.of("type", "string")),
                "required", List.of("greeting"));
        Template template = Template.builder().name("t").category("c").sectionSchema(schema).build();
        when(templateRepository.findTemplateByTemplateUid(any())).thenReturn(Optional.of(template));

        assertThatCode(() -> service.isValidTemplate(UUID.randomUUID().toString(), Map.of("greeting", "안녕")))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.service.TemplateServiceImplTest'`
Expected: 컴파일 실패 (생성자 시그니처가 아직 `(…, ObjectMapper)`)

- [ ] **Step 3: TemplateServiceImpl 리팩터링**

`src/main/java/smally/server/domain/template/service/TemplateServiceImpl.java` — import/필드/메서드 교체.

import 블록에서 networknt·ObjectMapper 관련을 제거하고 `SchemaValidator`·`List`를 남긴다. 파일을 아래로 만든다:

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
import smally.server.core.validation.SchemaValidator;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.dto.VariantResponse;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.repository.TemplateRepository;

@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService, InternalTemplateService {

    private final TemplateRepository templateRepository;
    private final CategoryService categoryService;
    private final SchemaValidator schemaValidator;

    @Override
    @Transactional
    public TemplateResponse createTemplate(TemplateCreateRequest templateCreateRequest) {
        categoryService.validateExists(templateCreateRequest.category());

        Template template = templateCreateRequest.toTemplate();

        templateRepository.save(template);
        return TemplateResponse.from(template);
    }

    @Override
    @Transactional(readOnly = true)
    public VariantResponse getVariant(String templateUId) {
        Template template = getTemplate(templateUId);

        return VariantResponse.from(template);
    }

    @Override
    @Transactional(readOnly = true)
    public void isValidTemplate(String templateUId, Map<String, Object> jsonData) {
        Template template = getTemplate(templateUId);

        List<String> errors = schemaValidator.validateData(template.getSectionSchema(), jsonData);

        if (!errors.isEmpty()) {
            throw new TemplateException(ErrorCode.INVALID_SECTION_VALUES);
        }
    }

    Template getTemplate(String templateUId) {
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

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.service.TemplateServiceImplTest'`
Expected: 2개 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/domain/template/service/TemplateServiceImpl.java src/test/java/smally/server/domain/template/service/TemplateServiceImplTest.java
git commit -m "refactor : isValidTemplate를 공용 SchemaValidator로 교체

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Template 엔티티 + TemplateCreateRequest에 optionsSchema 추가

`options_schema`(jsonb) 저장 필드와 등록 요청 수신 필드를 추가한다.

**Files:**
- Modify: `src/main/java/smally/server/domain/template/entity/Template.java`
- Modify: `src/main/java/smally/server/domain/template/dto/TemplateCreateRequest.java`
- Test: `src/test/java/smally/server/domain/template/dto/TemplateCreateRequestTest.java`

**Interfaces:**
- Produces:
  - `Template.getOptionsSchema()` → `Map<String,Object>`; 빌더에 `.optionsSchema(Map)` 추가
  - `TemplateCreateRequest.optionsSchema()` → `Map<String,Object>`; 생성자 순서 `(name, thumbnail, category, sectionSchema, optionsSchema, variants)`

- [ ] **Step 1: 실패 테스트 작성**

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
    void toTemplate_optionsSchema를_매핑한다() {
        Map<String, Object> options = Map.of(
                "design", Map.of("fontSize", Map.of("enum", List.of("small", "large"))));
        TemplateCreateRequest request = new TemplateCreateRequest(
                "클래식 화이트", "https://cdn/thumb.png", "클래식",
                Map.of("type", "object"), options, Map.of("color", List.of("white")));

        Template template = request.toTemplate();

        assertThat(template.getOptionsSchema()).isEqualTo(options);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.TemplateCreateRequestTest'`
Expected: 컴파일 실패 (`optionsSchema` 필드/`getOptionsSchema()` 없음)

- [ ] **Step 3-a: Template 엔티티 수정**

`Template.java`에서 `variants` 필드 아래에 `optionsSchema` 필드를 추가하고, 빌더 생성자에 파라미터를 추가한다.

`variants` 필드 선언 뒤에 삽입:

```java
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_schema")
    private Map<String, Object> optionsSchema;
```

빌더 생성자를 교체:

```java
    @Builder
    private Template(String name, String thumbnail, String category,
                     Map<String, Object> sectionSchema, Map<String, Object> variants,
                     Map<String, Object> optionsSchema) {
        this.name = name;
        this.thumbnail = thumbnail;
        this.category = category;
        this.sectionSchema = sectionSchema;
        this.variants = variants;
        this.optionsSchema = optionsSchema;
    }
```

- [ ] **Step 3-b: TemplateCreateRequest 수정**

`TemplateCreateRequest.java`를 아래로 교체:

```java
package smally.server.domain.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import smally.server.domain.template.entity.Template;

import java.util.Map;

public record TemplateCreateRequest(
        @NotBlank(message = "템플릿 이름은 필수입니다.")
        String name,
        String thumbnail,
        @NotBlank(message = "카테고리는 필수입니다.")
        String category,
        @NotNull(message = "섹션 스키마는 필수입니다.")
        Map<String, Object> sectionSchema,
        Map<String, Object> optionsSchema,
        Map<String, Object> variants
) {
    public Template toTemplate() {
        return Template.builder()
                .name(name)
                .thumbnail(thumbnail)
                .category(category)
                .sectionSchema(sectionSchema)
                .optionsSchema(optionsSchema)
                .variants(variants)
                .build();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.TemplateCreateRequestTest'`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/domain/template/entity/Template.java src/main/java/smally/server/domain/template/dto/TemplateCreateRequest.java src/test/java/smally/server/domain/template/dto/TemplateCreateRequestTest.java
git commit -m "feat : Template·TemplateCreateRequest에 options_schema 추가

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: createTemplate에서 optionsSchema 메타검증 + ErrorCode 추가

등록 시 `options_schema`가 유효한 JSON Schema인지 메타검증하고, 위반이면 400으로 거절한다. `options_schema`가 없으면(nullable) 검증을 건너뛴다.

**Files:**
- Modify: `src/main/java/smally/server/core/exception/ErrorCode.java`
- Modify: `src/main/java/smally/server/domain/template/service/TemplateServiceImpl.java:createTemplate`
- Test: `src/test/java/smally/server/domain/template/service/TemplateServiceImplTest.java` (Task 2 파일에 추가)

**Interfaces:**
- Consumes: `SchemaValidator.validateSchema(Map)` (Task 1), `TemplateCreateRequest.optionsSchema()` (Task 3)
- Produces: `ErrorCode.INVALID_TEMPLATE_OPTIONS_SCHEMA`

- [ ] **Step 1: 실패 테스트 작성 (TemplateServiceImplTest에 추가)**

기존 import에 다음을 추가:

```java
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.springframework.test.util.ReflectionTestUtils;
import smally.server.domain.template.dto.TemplateCreateRequest;
```

클래스에 테스트 두 개 추가:

```java
    @Test
    void createTemplate_optionsSchema가_유효하지_않으면_거절하고_저장하지_않는다() {
        Map<String, Object> invalidOptions = Map.of("type", 123); // type은 문자열이어야 함
        TemplateCreateRequest request = new TemplateCreateRequest(
                "t", null, "클래식", Map.of("type", "object"), invalidOptions, null);

        assertThatThrownBy(() -> service.createTemplate(request))
                .isInstanceOf(TemplateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TEMPLATE_OPTIONS_SCHEMA);

        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_optionsSchema가_유효하면_저장한다() {
        Map<String, Object> validOptions = Map.of(
                "type", "object",
                "properties", Map.of("fontSize", Map.of("enum", List.of("small", "large"))));
        TemplateCreateRequest request = new TemplateCreateRequest(
                "t", null, "클래식", Map.of("type", "object"), validOptions, null);
        when(templateRepository.save(any(Template.class))).thenAnswer(invocation -> {
            Template saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "templateUid", UUID.randomUUID());
            return saved;
        });

        assertThatCode(() -> service.createTemplate(request)).doesNotThrowAnyException();

        verify(templateRepository).save(any(Template.class));
    }
```

> 참고 1: `categoryService.validateExists(...)`는 `@Mock`이라 기본적으로 아무것도 하지 않으므로 통과한다.
> 참고 2: 단위 테스트에서는 `@PrePersist`가 실행되지 않아 `templateUid`가 null이다. `createTemplate`의 정상 경로 끝 `TemplateResponse.from(template)`가 `templateUid.toString()`을 부르므로, `save` 스텁에서 `ReflectionTestUtils`로 `templateUid`를 채워 NPE를 피한다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.service.TemplateServiceImplTest'`
Expected: 컴파일 실패 (`ErrorCode.INVALID_TEMPLATE_OPTIONS_SCHEMA` 없음)

- [ ] **Step 3-a: ErrorCode 추가**

`ErrorCode.java`에서 마지막 `INVALID_SECTION_VALUES(...)` 항목을 다음 2줄로 교체(옵션 코드 추가 후 세미콜론 유지):

```java
    INVALID_SECTION_VALUES(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "청첩장 입력값이 템플릿 스키마와 맞지 않습니다."),
    INVALID_TEMPLATE_OPTIONS_SCHEMA(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "옵션 스키마가 유효한 JSON Schema가 아닙니다.");
```

- [ ] **Step 3-b: createTemplate 수정**

`TemplateServiceImpl.createTemplate`를 교체:

```java
    @Override
    @Transactional
    public TemplateResponse createTemplate(TemplateCreateRequest templateCreateRequest) {
        categoryService.validateExists(templateCreateRequest.category());

        if (templateCreateRequest.optionsSchema() != null) {
            List<String> errors = schemaValidator.validateSchema(templateCreateRequest.optionsSchema());
            if (!errors.isEmpty()) {
                throw new TemplateException(ErrorCode.INVALID_TEMPLATE_OPTIONS_SCHEMA);
            }
        }

        Template template = templateCreateRequest.toTemplate();

        templateRepository.save(template);
        return TemplateResponse.from(template);
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.service.TemplateServiceImplTest'`
Expected: 4개 PASS (Task 2의 2개 + 신규 2개)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/core/exception/ErrorCode.java src/main/java/smally/server/domain/template/service/TemplateServiceImpl.java src/test/java/smally/server/domain/template/service/TemplateServiceImplTest.java
git commit -m "feat : 템플릿 등록 시 options_schema 메타검증

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: VariantResponse에 optionsSchema 노출

프론트 선택지 엔드포인트(`GET /api/template/v1/variant/{uid}`)의 `VariantResponse`에 `optionsSchema`를 실어 내린다. `section_schema` 비노출 원칙은 그대로 유지한다.

**Files:**
- Modify: `src/main/java/smally/server/domain/template/dto/VariantResponse.java`
- Test: `src/test/java/smally/server/domain/template/dto/VariantResponseTest.java`

**Interfaces:**
- Consumes: `Template.getOptionsSchema()` (Task 3)
- Produces: `VariantResponse(Map<String,Object> variant, Map<String,Object> optionsSchema)`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/template/dto/VariantResponseTest.java`:

```java
package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.template.entity.Template;

class VariantResponseTest {

    @Test
    void from_variant와_optionsSchema를_함께_노출한다() {
        Map<String, Object> variants = Map.of("color", List.of("white", "beige"));
        Map<String, Object> options = Map.of(
                "design", Map.of("fontSize", Map.of("enum", List.of("small", "large"))));
        Template template = Template.builder()
                .name("t").category("c")
                .sectionSchema(Map.of("type", "object"))
                .variants(variants)
                .optionsSchema(options)
                .build();

        VariantResponse response = VariantResponse.from(template);

        assertThat(response.variant()).isEqualTo(variants);
        assertThat(response.optionsSchema()).isEqualTo(options);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.VariantResponseTest'`
Expected: 컴파일 실패 (`optionsSchema()` 접근자 없음)

- [ ] **Step 3: VariantResponse 수정**

`VariantResponse.java`를 아래로 교체:

```java
package smally.server.domain.template.dto;

import smally.server.domain.template.entity.Template;

import java.util.Map;

public record VariantResponse(
        Map<String, Object> variant,
        Map<String, Object> optionsSchema
) {
    public static VariantResponse from(Template template) {
        return new VariantResponse(template.getVariants(), template.getOptionsSchema());
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.VariantResponseTest'`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/domain/template/dto/VariantResponse.java src/test/java/smally/server/domain/template/dto/VariantResponseTest.java
git commit -m "feat : VariantResponse에 options_schema 노출

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 완료 기준

- `SchemaValidator` 공용 컴포넌트로 메타검증·데이터검증 통합, 새 검증기 0개(ADR-006).
- 템플릿 등록 시 `options_schema` 메타검증, 위반 시 400(`INVALID_TEMPLATE_OPTIONS_SCHEMA`).
- `options_schema` 저장(jsonb) + `VariantResponse`로 프론트 노출, `section_schema` 비노출 유지.
- 전 태스크 단위/슬라이스 테스트 통과.

## 후속 (이 계획 밖)

- 청첩장 `Invitation.selected_options` 필드 + `SchemaValidator.validateData(template.optionsSchema, selectedOptions)` 연동.
- `x-editable:false` 옵션 변조 거절 후처리.
- section_schema 등록 메타검증(2026-07-14 설계의 미구현분) — 동일 `SchemaValidator.validateSchema`로 마감 가능.
