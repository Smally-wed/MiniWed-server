# Component(섹션 타입) 파운데이션 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **⚠️ 재설계됨 (2026-07-16)**: 이 계획은 `Component` 하나에 **종류(type)와 컴포넌트를 뭉쳐** 놨는데, 이후 모델이 **종류(`ComponentType` 카테고리 테이블) / 컴포넌트(`Component`: →종류·`frontend_binding`·`data_schema`·`option_schema`)** 분리로 바뀌었다([스펙](../specs/2026-07-15-section-component-model-design.md) §2·3·6, [ADR-007](../../adr/ADR-007-section-component-template-model.md) 참조). 이 계획으로 만든 코드(작업 트리, 미커밋)는 그 분리에 맞게 재작성 대상이다: `type`→`ComponentType` FK, `variants` 제거, `frontend_binding` 추가. **아래 태스크는 옛 모델 기준이므로 그대로 실행하지 말 것.** 대체 계획을 새로 작성한다.

**Goal:** 청첩장 섹션의 데이터 계약·옵션·변형을 담는 `Component`(섹션 타입) 엔티티와 그 등록/조회/메타검증을 구현한다. (ADR-007 하위 계획 1/3)

**Architecture:** `Component`를 1급 엔티티로 신설(type·data_schema·option_schema·variants, jsonb). 등록 시 `data_schema`/`option_schema`를 기존 공용 `SchemaValidator.validateSchema`로 메타검증한다. `data_schema`는 서버 검증 전용(비노출), `option_schema`·`variants`는 편집기용으로 조회 응답에 노출. 도메인 배치·코드 스타일은 기존 `Category`/`Template` 도메인을 그대로 따른다.

**Tech Stack:** Spring Boot 4.1.0, Java 25, Spring Data JPA, PostgreSQL jsonb(Hibernate `@JdbcTypeCode(SqlTypes.JSON)`), networknt json-schema-validator 3.0.6(기존 `SchemaValidator` 경유), Jackson 3(`tools.jackson`).

## Global Constraints

- **패키지 배치**: 모두 `smally.server.domain.template.*` 하위(Component는 템플릿 도메인의 구성단위). entity/dto/repository/service/controller 서브패키지.
- **jsonb 매핑**: `Map<String,Object>`/`List<String>` 필드에 `@JdbcTypeCode(SqlTypes.JSON)`. 기존 `Template`과 동일.
- **검증 재사용**: 새 검증기 만들지 않는다. 기존 `smally.server.core.validation.SchemaValidator`의 `List<String> validateSchema(Map<String,Object>)`(빈 목록=유효)를 호출한다.
- **노출 정책**: `data_schema`는 응답에 넣지 않는다(서버 검증 전용). `option_schema`·`variants`만 노출.
- **응답 포맷**: `ApiResponse.of(HttpStatus, body)` 사용(기존 컨트롤러와 동일). 등록은 `HttpStatus.CREATED`.
- **예외**: `ComponentException(ErrorCode)` 신설(기존 `CategoryException` 패턴). `BusinessException`은 `@Getter`로 `getErrorCode()` 노출.
- **테스트 스타일**: JUnit 5 + Mockito(`@ExtendWith(MockitoExtension.class)`, `@Mock`) + AssertJ. 메서드명 한글_언더스코어. 서비스 테스트는 `SchemaValidator`를 실제 인스턴스(`new SchemaValidator(new ObjectMapper())`)로 주입해 실제 메타검증을 태운다.
- **빌드 훅**: 모든 Edit/Write가 전체 `./gradlew build`(Postgres/Redis/jwt.secret 필요)를 돌린다. 인프라 부재로 실패해도 파일은 반영된다. 실질 검증은 각 태스크의 스코프 테스트(`--tests`)로 한다.
- **커밋**: 태스크마다 커밋. 메시지 끝에 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- **범위 밖**: Template 레시피 전환·Invitation 검증(계획 B·C). Component **수정/삭제**(후속). `data_schema`/`option_schema`의 `x-` 키워드 의미 해석(프론트 몫).

---

### Task 1: Component 엔티티 + ComponentCreateRequest DTO

`Component` 엔티티와 등록 요청 DTO를 만든다.

**Files:**
- Create: `src/main/java/smally/server/domain/template/entity/Component.java`
- Create: `src/main/java/smally/server/domain/template/dto/ComponentCreateRequest.java`
- Test: `src/test/java/smally/server/domain/template/dto/ComponentCreateRequestTest.java`

**Interfaces:**
- Produces:
  - `Component` (JPA 엔티티) — getter: `getType()`, `getDataSchema()`, `getOptionSchema()`, `getVariants()`; 빌더 `.type/.dataSchema/.optionSchema/.variants`
  - `ComponentCreateRequest(String type, Map<String,Object> dataSchema, Map<String,Object> optionSchema, List<String> variants)` + `Component toComponent()`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/template/dto/ComponentCreateRequestTest.java`:

```java
package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.template.entity.Component;

class ComponentCreateRequestTest {

    @Test
    void toComponent_모든_필드를_매핑한다() {
        Map<String, Object> dataSchema = Map.of("type", "object",
                "properties", Map.of("photos", Map.of("type", "array")));
        Map<String, Object> optionSchema = Map.of("type", "object",
                "properties", Map.of("showTitle", Map.of("type", "boolean")));
        List<String> variants = List.of("grid", "slide");

        ComponentCreateRequest request =
                new ComponentCreateRequest("gallery", dataSchema, optionSchema, variants);

        Component component = request.toComponent();

        assertThat(component.getType()).isEqualTo("gallery");
        assertThat(component.getDataSchema()).isEqualTo(dataSchema);
        assertThat(component.getOptionSchema()).isEqualTo(optionSchema);
        assertThat(component.getVariants()).isEqualTo(variants);
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.ComponentCreateRequestTest'`
Expected: 컴파일 실패 (`Component`/`ComponentCreateRequest` 없음)

- [ ] **Step 3: Component 엔티티 구현**

`src/main/java/smally/server/domain/template/entity/Component.java`:

```java
package smally.server.domain.template.entity;

import jakarta.persistence.*;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import smally.server.domain.common.entity.BaseEntity;

@Entity
@Table(name = "components", indexes = @Index(name = "idx_component_type", columnList = "type"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Component extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "component_id")
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_schema", nullable = false)
    private Map<String, Object> dataSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "option_schema")
    private Map<String, Object> optionSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private List<String> variants;

    @Builder
    private Component(String type, Map<String, Object> dataSchema,
                      Map<String, Object> optionSchema, List<String> variants) {
        this.type = type;
        this.dataSchema = dataSchema;
        this.optionSchema = optionSchema;
        this.variants = variants;
    }
}
```

- [ ] **Step 4: ComponentCreateRequest 구현**

`src/main/java/smally/server/domain/template/dto/ComponentCreateRequest.java`:

```java
package smally.server.domain.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import smally.server.domain.template.entity.Component;

public record ComponentCreateRequest(
        @NotBlank(message = "컴포넌트 타입은 필수입니다.")
        String type,
        @NotNull(message = "데이터 스키마는 필수입니다.")
        Map<String, Object> dataSchema,
        Map<String, Object> optionSchema,
        List<String> variants
) {
    public Component toComponent() {
        return Component.builder()
                .type(type)
                .dataSchema(dataSchema)
                .optionSchema(optionSchema)
                .variants(variants)
                .build();
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.ComponentCreateRequestTest'`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/smally/server/domain/template/entity/Component.java src/main/java/smally/server/domain/template/dto/ComponentCreateRequest.java src/test/java/smally/server/domain/template/dto/ComponentCreateRequestTest.java
git commit -m "feat : Component(섹션 타입) 엔티티·등록 DTO 추가

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Repository + ErrorCode/Exception + ComponentService (등록 메타검증·중복거절·조회)

`ComponentRepository`, 에러코드·예외, 그리고 등록(메타검증+중복거절)·조회 서비스를 만든다.

**Files:**
- Create: `src/main/java/smally/server/domain/template/repository/ComponentRepository.java`
- Modify: `src/main/java/smally/server/core/exception/ErrorCode.java`
- Create: `src/main/java/smally/server/core/exception/exceptions/ComponentException.java`
- Create: `src/main/java/smally/server/domain/template/service/ComponentService.java`
- Create: `src/main/java/smally/server/domain/template/service/ComponentServiceImpl.java`
- Test: `src/test/java/smally/server/domain/template/service/ComponentServiceImplTest.java`

**Interfaces:**
- Consumes: `SchemaValidator.validateSchema(Map)` (기존), `ComponentCreateRequest`/`Component` (Task 1)
- Produces:
  - `ComponentRepository`: `boolean existsByType(String)`, `Optional<Component> findByType(String)`
  - `ErrorCode.DUPLICATE_COMPONENT`, `ErrorCode.COMPONENT_NOT_FOUND`, `ErrorCode.INVALID_COMPONENT_SCHEMA`
  - `ComponentException extends BusinessException`
  - `ComponentService`: `void createComponent(ComponentCreateRequest)`, `Component getComponent(String type)`, `List<Component> getAllComponents()`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/template/service/ComponentServiceImplTest.java`:

```java
package smally.server.domain.template.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentException;
import smally.server.core.validation.SchemaValidator;
import smally.server.domain.template.dto.ComponentCreateRequest;
import smally.server.domain.template.entity.Component;
import smally.server.domain.template.repository.ComponentRepository;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ComponentServiceImplTest {

    @Mock ComponentRepository componentRepository;
    SchemaValidator schemaValidator = new SchemaValidator(new ObjectMapper());
    ComponentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ComponentServiceImpl(componentRepository, schemaValidator);
    }

    private ComponentCreateRequest request(Map<String, Object> dataSchema) {
        return new ComponentCreateRequest("gallery", dataSchema, null, List.of("grid"));
    }

    @Test
    void createComponent_dataSchema가_유효하지_않으면_거절하고_저장하지_않는다() {
        when(componentRepository.existsByType("gallery")).thenReturn(false);
        ComponentCreateRequest req = request(Map.of("type", 123)); // type은 문자열이어야 함

        assertThatThrownBy(() -> service.createComponent(req))
                .isInstanceOf(ComponentException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_COMPONENT_SCHEMA);

        verify(componentRepository, never()).save(any());
    }

    @Test
    void createComponent_타입이_중복이면_거절한다() {
        when(componentRepository.existsByType("gallery")).thenReturn(true);
        ComponentCreateRequest req = request(Map.of("type", "object"));

        assertThatThrownBy(() -> service.createComponent(req))
                .isInstanceOf(ComponentException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_COMPONENT);

        verify(componentRepository, never()).save(any());
    }

    @Test
    void createComponent_유효하면_저장한다() {
        when(componentRepository.existsByType("gallery")).thenReturn(false);
        ComponentCreateRequest req = request(Map.of("type", "object"));

        assertThatCode(() -> service.createComponent(req)).doesNotThrowAnyException();

        verify(componentRepository).save(any(Component.class));
    }

    @Test
    void getComponent_없는_타입이면_COMPONENT_NOT_FOUND() {
        when(componentRepository.findByType("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getComponent("nope"))
                .isInstanceOf(ComponentException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMPONENT_NOT_FOUND);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.service.ComponentServiceImplTest'`
Expected: 컴파일 실패 (repository/errorcode/exception/service 없음)

- [ ] **Step 3-a: ComponentRepository 구현**

`src/main/java/smally/server/domain/template/repository/ComponentRepository.java`:

```java
package smally.server.domain.template.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.template.entity.Component;

public interface ComponentRepository extends JpaRepository<Component, Long> {
    boolean existsByType(String type);
    Optional<Component> findByType(String type);
}
```

- [ ] **Step 3-b: ErrorCode 추가**

`ErrorCode.java`에서 마지막 항목 `INVALID_TEMPLATE_OPTIONS_SCHEMA(...)`를 다음으로 교체(뒤 3줄, 세미콜론은 마지막에 유지):

```java
    INVALID_TEMPLATE_OPTIONS_SCHEMA(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "옵션 스키마가 유효한 JSON Schema가 아닙니다."),
    DUPLICATE_COMPONENT(HttpStatus.CONFLICT, "CONFLICT", "이미 존재하는 컴포넌트 타입입니다."),
    COMPONENT_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "컴포넌트를 찾을 수 없습니다."),
    INVALID_COMPONENT_SCHEMA(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "컴포넌트 스키마가 유효한 JSON Schema가 아닙니다.");
```

- [ ] **Step 3-c: ComponentException 구현**

`src/main/java/smally/server/core/exception/exceptions/ComponentException.java`:

```java
package smally.server.core.exception.exceptions;

import smally.server.core.exception.BusinessException;
import smally.server.core.exception.ErrorCode;

public class ComponentException extends BusinessException {
    public ComponentException(ErrorCode errorCode) {
        super(errorCode);
    }
}
```

- [ ] **Step 3-d: ComponentService 인터페이스**

`src/main/java/smally/server/domain/template/service/ComponentService.java`:

```java
package smally.server.domain.template.service;

import java.util.List;
import smally.server.domain.template.dto.ComponentCreateRequest;
import smally.server.domain.template.entity.Component;

public interface ComponentService {
    void createComponent(ComponentCreateRequest componentCreateRequest);
    Component getComponent(String type);
    List<Component> getAllComponents();
}
```

- [ ] **Step 3-e: ComponentServiceImpl 구현**

`src/main/java/smally/server/domain/template/service/ComponentServiceImpl.java`:

```java
package smally.server.domain.template.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentException;
import smally.server.core.validation.SchemaValidator;
import smally.server.domain.template.dto.ComponentCreateRequest;
import smally.server.domain.template.entity.Component;
import smally.server.domain.template.repository.ComponentRepository;

@Service
@RequiredArgsConstructor
public class ComponentServiceImpl implements ComponentService {

    private final ComponentRepository componentRepository;
    private final SchemaValidator schemaValidator;

    @Override
    @Transactional
    public void createComponent(ComponentCreateRequest componentCreateRequest) {
        if (componentRepository.existsByType(componentCreateRequest.type())) {
            throw new ComponentException(ErrorCode.DUPLICATE_COMPONENT);
        }

        validateSchemaOrThrow(componentCreateRequest.dataSchema());
        if (componentCreateRequest.optionSchema() != null) {
            validateSchemaOrThrow(componentCreateRequest.optionSchema());
        }

        componentRepository.save(componentCreateRequest.toComponent());
    }

    @Override
    @Transactional(readOnly = true)
    public Component getComponent(String type) {
        return componentRepository.findByType(type)
                .orElseThrow(() -> new ComponentException(ErrorCode.COMPONENT_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Component> getAllComponents() {
        return componentRepository.findAll();
    }

    private void validateSchemaOrThrow(java.util.Map<String, Object> schema) {
        if (!schemaValidator.validateSchema(schema).isEmpty()) {
            throw new ComponentException(ErrorCode.INVALID_COMPONENT_SCHEMA);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.service.ComponentServiceImplTest'`
Expected: 4개 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/domain/template/repository/ComponentRepository.java src/main/java/smally/server/core/exception/ErrorCode.java src/main/java/smally/server/core/exception/exceptions/ComponentException.java src/main/java/smally/server/domain/template/service/ComponentService.java src/main/java/smally/server/domain/template/service/ComponentServiceImpl.java src/test/java/smally/server/domain/template/service/ComponentServiceImplTest.java
git commit -m "feat : 컴포넌트 등록(메타검증·중복거절)·조회 서비스

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: ComponentResponse DTO + ComponentController

편집기용 조회 응답(`option_schema`·`variants` 노출, `data_schema` 비노출)과 REST 엔드포인트를 만든다.

**Files:**
- Create: `src/main/java/smally/server/domain/template/dto/ComponentResponse.java`
- Create: `src/main/java/smally/server/domain/template/controller/ComponentController.java`
- Test: `src/test/java/smally/server/domain/template/dto/ComponentResponseTest.java`

**Interfaces:**
- Consumes: `Component` (Task 1), `ComponentService` (Task 2)
- Produces: `ComponentResponse(String type, List<String> variants, Map<String,Object> optionSchema)` + `from(Component)`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/template/dto/ComponentResponseTest.java`:

```java
package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.template.entity.Component;

class ComponentResponseTest {

    @Test
    void from_type_variants_optionSchema를_노출하고_dataSchema는_담지_않는다() {
        Map<String, Object> optionSchema = Map.of("type", "object");
        Component component = Component.builder()
                .type("gallery")
                .dataSchema(Map.of("type", "object")) // 서버 검증 전용
                .optionSchema(optionSchema)
                .variants(List.of("grid", "slide"))
                .build();

        ComponentResponse response = ComponentResponse.from(component);

        assertThat(response.type()).isEqualTo("gallery");
        assertThat(response.variants()).isEqualTo(List.of("grid", "slide"));
        assertThat(response.optionSchema()).isEqualTo(optionSchema);
        // dataSchema 접근자는 존재하지 않아야 한다(레코드 컴포넌트 2+1개만): 컴파일 계약으로 보장
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.ComponentResponseTest'`
Expected: 컴파일 실패 (`ComponentResponse` 없음)

- [ ] **Step 3-a: ComponentResponse 구현**

`src/main/java/smally/server/domain/template/dto/ComponentResponse.java`:

```java
package smally.server.domain.template.dto;

import java.util.List;
import java.util.Map;
import smally.server.domain.template.entity.Component;

public record ComponentResponse(
        String type,
        List<String> variants,
        Map<String, Object> optionSchema
) {
    public static ComponentResponse from(Component component) {
        return new ComponentResponse(
                component.getType(),
                component.getVariants(),
                component.getOptionSchema()
        );
    }
}
```

- [ ] **Step 3-b: ComponentController 구현**

`src/main/java/smally/server/domain/template/controller/ComponentController.java`:

```java
package smally.server.domain.template.controller;

import jakarta.validation.Valid;
import java.util.List;
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
import smally.server.domain.template.dto.ComponentCreateRequest;
import smally.server.domain.template.dto.ComponentResponse;
import smally.server.domain.template.service.ComponentService;

@RestController
@RequestMapping("/api/component")
@RequiredArgsConstructor
public class ComponentController {

    private final ComponentService componentService;

    @PostMapping("/v1")
    public ResponseEntity<ApiResponse<Void>> createComponent(
            @RequestBody @Valid ComponentCreateRequest componentCreateRequest
    ) {
        componentService.createComponent(componentCreateRequest);
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED, null));
    }

    @GetMapping("/v1")
    public ResponseEntity<ApiResponse<List<ComponentResponse>>> getAllComponents() {
        List<ComponentResponse> body = componentService.getAllComponents().stream()
                .map(ComponentResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.OK, body));
    }

    @GetMapping("/v1/{type}")
    public ResponseEntity<ApiResponse<ComponentResponse>> getComponent(
            @PathVariable String type
    ) {
        return ResponseEntity.ok(
                ApiResponse.of(HttpStatus.OK, ComponentResponse.from(componentService.getComponent(type)))
        );
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.ComponentResponseTest'`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/domain/template/dto/ComponentResponse.java src/main/java/smally/server/domain/template/controller/ComponentController.java src/test/java/smally/server/domain/template/dto/ComponentResponseTest.java
git commit -m "feat : 컴포넌트 조회 응답·컨트롤러 (data_schema 비노출)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 완료 기준

- `Component`(type·data_schema·option_schema·variants, jsonb) 엔티티 신설.
- 등록 시 `data_schema`·`option_schema` 메타검증(기존 `SchemaValidator` 재사용), 타입 중복 거절.
- 조회 응답은 `option_schema`·`variants`만 노출, `data_schema` 비노출.
- 전 태스크 스코프 테스트 통과(Task1: 1, Task2: 4, Task3: 1 = 6).

## 다음 계획 (ADR-007 하위)

- **계획 B**: Template 레시피 전환 — `sections`(jsonb 순서 리스트)+`theme` 추가, `options_schema` 폐기, 등록 시 레시피 유효성 검증(참조 컴포넌트 존재·variant 유효), 응답 재설계.
- **계획 C**: Invitation 저장 시 각 섹션 인스턴스를 컴포넌트 `data_schema`로 검증(`SchemaValidator.validateData` 재사용) + `selected_options` 검증.
