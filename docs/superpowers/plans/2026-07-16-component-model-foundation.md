# Component 모델 파운데이션 (A′) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 컴포넌트 종류 카테고리 `ComponentType`과, 종류를 참조하며 프론트 연결·데이터계약·컴포넌트옵션을 가지는 `Component`를 구현한다. (ADR-007 하위 계획 A′ — 종전 계획 A의 "종류/컴포넌트 뭉침"을 분리로 재설계)

**Architecture:** `ComponentType`(name 카테고리, 확장용 DB 테이블)은 기존 `Category` 도메인을 그대로 미러링한다. `Component`는 `componentType`(String, 등록 시 존재 검증), `frontendBinding`, `data_schema`(jsonb), `option_schema`(jsonb)를 가진다. 컴포넌트 등록 시 `componentType` 존재 검증 + 스키마 메타검증(기존 공용 `SchemaValidator` 재사용). `data_schema`는 서버 검증 전용(비노출), `component_type`·`frontend_binding`·`option_schema`는 편집기용 노출.

**Tech Stack:** Spring Boot 4.1.0, Java 25, Spring Data JPA, PostgreSQL jsonb(Hibernate `@JdbcTypeCode(SqlTypes.JSON)`), networknt json-schema-validator 3.0.6(기존 `SchemaValidator` 경유), Jackson 3(`tools.jackson`).

## Global Constraints

- **패키지 배치**: 모두 `smally.server.domain.template.*` 하위. entity/dto/repository/service/controller. 예외는 `core.exception.exceptions`.
- **jsonb 매핑**: `Map<String,Object>`(`dataSchema`/`optionSchema`)에 `@JdbcTypeCode(SqlTypes.JSON)`. `Component`는 `BaseEntity` 상속(기존 `Template`과 동일). `ComponentType`은 `Category`처럼 `BaseEntity` 미상속(최소).
- **종류 참조**: `Component.componentType`은 FK가 아니라 **String**(종류 이름). 등록 시 `ComponentTypeService.validateExists`로 존재를 확인한다(기존 `Template.category` ↔ `CategoryService.validateExists` 패턴과 동일).
- **검증 재사용**: 새 검증기 만들지 않는다. 기존 `smally.server.core.validation.SchemaValidator.validateSchema(Map)`(빈 목록=유효)를 호출.
- **노출 정책**: `data_schema`는 응답에 넣지 않는다(서버 검증 전용). `Component` 응답은 `id`·`name`·`componentType`·`frontendBinding`·`optionSchema` 노출.
- **응답 포맷**: `ApiResponse.of(HttpStatus, body)`. 등록은 기존 컨트롤러 관례(`ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED, ...))`)를 따른다.
- **예외**: `ComponentTypeException`/`ComponentException`(기존 `CategoryException` 패턴). `BusinessException`은 `@Getter`로 `getErrorCode()` 노출.
- **테스트 스타일**: JUnit 5 + Mockito(`@ExtendWith(MockitoExtension.class)`) + AssertJ. 메서드명 한글_언더스코어. 서비스 테스트는 `SchemaValidator`를 실제 인스턴스(`new SchemaValidator(new ObjectMapper())`)로 주입.
- **빌드 훅**: 모든 Edit/Write가 전체 `./gradlew build`(Postgres/Redis/jwt.secret 필요)를 돌린다. 인프라 부재로 실패해도 파일 반영됨. 실질 검증은 각 태스크 스코프 테스트(`--tests`).
- **커밋 금지(이 세션)**: 태스크의 커밋 스텝은 건너뛴다. 변경은 작업 트리에만 남기고 사용자가 최종 검토 후 직접 커밋.
- **범위 밖**: Template 레시피 전환(계획 C), Invitation 검증(계획 D), OptionDefinition 전역옵션 마스터(계획 B), 수정/삭제.

---

### Task 1: ComponentType 카테고리 도메인 (Category 미러)

종류 카테고리 `ComponentType`을 기존 `Category` 도메인과 동일 패턴으로 만든다.

**Files:**
- Create: `src/main/java/smally/server/domain/template/entity/ComponentType.java`
- Create: `src/main/java/smally/server/domain/template/dto/ComponentTypeCreateRequest.java`
- Create: `src/main/java/smally/server/domain/template/repository/ComponentTypeRepository.java`
- Modify: `src/main/java/smally/server/core/exception/ErrorCode.java`
- Create: `src/main/java/smally/server/core/exception/exceptions/ComponentTypeException.java`
- Create: `src/main/java/smally/server/domain/template/service/ComponentTypeService.java`
- Create: `src/main/java/smally/server/domain/template/service/ComponentTypeServiceImpl.java`
- Create: `src/main/java/smally/server/domain/template/controller/ComponentTypeController.java`
- Test: `src/test/java/smally/server/domain/template/service/ComponentTypeServiceImplTest.java`

**Interfaces:**
- Produces:
  - `ComponentType` — `getName()`; 빌더 `.name`
  - `ComponentTypeCreateRequest(String name)` + `ComponentType to()`
  - `ComponentTypeRepository`: `boolean existsByName(String)`
  - `ErrorCode.DUPLICATE_COMPONENT_TYPE`, `ErrorCode.COMPONENT_TYPE_NOT_FOUND`
  - `ComponentTypeException extends BusinessException`
  - `ComponentTypeService`: `void createComponentType(ComponentTypeCreateRequest)`, `List<String> getAllComponentTypes()`, `void validateExists(String name)`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/template/service/ComponentTypeServiceImplTest.java`:

```java
package smally.server.domain.template.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentTypeException;
import smally.server.domain.template.dto.ComponentTypeCreateRequest;
import smally.server.domain.template.entity.ComponentType;
import smally.server.domain.template.repository.ComponentTypeRepository;

@ExtendWith(MockitoExtension.class)
class ComponentTypeServiceImplTest {

    @Mock ComponentTypeRepository repository;
    ComponentTypeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ComponentTypeServiceImpl(repository);
    }

    @Test
    void createComponentType_중복이면_거절하고_저장하지_않는다() {
        when(repository.existsByName("hero")).thenReturn(true);

        assertThatThrownBy(() -> service.createComponentType(new ComponentTypeCreateRequest("hero")))
                .isInstanceOf(ComponentTypeException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_COMPONENT_TYPE);

        verify(repository, never()).save(any());
    }

    @Test
    void createComponentType_새_이름이면_저장한다() {
        when(repository.existsByName("hero")).thenReturn(false);

        assertThatCode(() -> service.createComponentType(new ComponentTypeCreateRequest("hero")))
                .doesNotThrowAnyException();

        verify(repository).save(any(ComponentType.class));
    }

    @Test
    void validateExists_없는_종류면_COMPONENT_TYPE_NOT_FOUND() {
        when(repository.existsByName("nope")).thenReturn(false);

        assertThatThrownBy(() -> service.validateExists("nope"))
                .isInstanceOf(ComponentTypeException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMPONENT_TYPE_NOT_FOUND);
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.service.ComponentTypeServiceImplTest'`
Expected: 컴파일 실패

- [ ] **Step 3-a: ComponentType 엔티티**

`src/main/java/smally/server/domain/template/entity/ComponentType.java`:

```java
package smally.server.domain.template.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "component_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComponentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "component_type_id")
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String name;

    @Builder
    private ComponentType(String name) {
        this.name = name;
    }
}
```

- [ ] **Step 3-b: ComponentTypeCreateRequest**

`src/main/java/smally/server/domain/template/dto/ComponentTypeCreateRequest.java`:

```java
package smally.server.domain.template.dto;

import jakarta.validation.constraints.NotBlank;
import smally.server.domain.template.entity.ComponentType;

public record ComponentTypeCreateRequest(
        @NotBlank(message = "컴포넌트 종류 이름은 필수입니다.")
        String name
) {
    public ComponentType to() {
        return ComponentType.builder().name(name).build();
    }
}
```

- [ ] **Step 3-c: ComponentTypeRepository**

`src/main/java/smally/server/domain/template/repository/ComponentTypeRepository.java`:

```java
package smally.server.domain.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.template.entity.ComponentType;

public interface ComponentTypeRepository extends JpaRepository<ComponentType, Long> {
    boolean existsByName(String name);
}
```

- [ ] **Step 3-d: ErrorCode 추가**

`ErrorCode.java`에서 마지막 항목 `INVALID_TEMPLATE_OPTIONS_SCHEMA(...)`를 다음으로 교체(세미콜론은 마지막에 유지):

```java
    INVALID_TEMPLATE_OPTIONS_SCHEMA(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "옵션 스키마가 유효한 JSON Schema가 아닙니다."),
    DUPLICATE_COMPONENT_TYPE(HttpStatus.CONFLICT, "CONFLICT", "이미 존재하는 컴포넌트 종류입니다."),
    COMPONENT_TYPE_NOT_FOUND(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "존재하지 않는 컴포넌트 종류입니다.");
```

- [ ] **Step 3-e: ComponentTypeException**

`src/main/java/smally/server/core/exception/exceptions/ComponentTypeException.java`:

```java
package smally.server.core.exception.exceptions;

import smally.server.core.exception.BusinessException;
import smally.server.core.exception.ErrorCode;

public class ComponentTypeException extends BusinessException {
    public ComponentTypeException(ErrorCode errorCode) {
        super(errorCode);
    }
}
```

- [ ] **Step 3-f: ComponentTypeService 인터페이스**

`src/main/java/smally/server/domain/template/service/ComponentTypeService.java`:

```java
package smally.server.domain.template.service;

import java.util.List;
import smally.server.domain.template.dto.ComponentTypeCreateRequest;

public interface ComponentTypeService {
    void createComponentType(ComponentTypeCreateRequest request);
    List<String> getAllComponentTypes();
    void validateExists(String name);
}
```

- [ ] **Step 3-g: ComponentTypeServiceImpl**

`src/main/java/smally/server/domain/template/service/ComponentTypeServiceImpl.java`:

```java
package smally.server.domain.template.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentTypeException;
import smally.server.domain.template.dto.ComponentTypeCreateRequest;
import smally.server.domain.template.entity.ComponentType;
import smally.server.domain.template.repository.ComponentTypeRepository;

@Service
@RequiredArgsConstructor
public class ComponentTypeServiceImpl implements ComponentTypeService {

    private final ComponentTypeRepository componentTypeRepository;

    @Override
    @Transactional
    public void createComponentType(ComponentTypeCreateRequest request) {
        if (componentTypeRepository.existsByName(request.name())) {
            throw new ComponentTypeException(ErrorCode.DUPLICATE_COMPONENT_TYPE);
        }
        componentTypeRepository.save(request.to());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAllComponentTypes() {
        return componentTypeRepository.findAll().stream()
                .map(ComponentType::getName)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void validateExists(String name) {
        if (!componentTypeRepository.existsByName(name)) {
            throw new ComponentTypeException(ErrorCode.COMPONENT_TYPE_NOT_FOUND);
        }
    }
}
```

- [ ] **Step 3-h: ComponentTypeController**

`src/main/java/smally/server/domain/template/controller/ComponentTypeController.java`:

```java
package smally.server.domain.template.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smally.server.core.dto.ApiResponse;
import smally.server.domain.template.dto.ComponentTypeCreateRequest;
import smally.server.domain.template.service.ComponentTypeService;

@RestController
@RequestMapping("/api/component-type")
@RequiredArgsConstructor
public class ComponentTypeController {

    private final ComponentTypeService componentTypeService;

    @PostMapping("/v1")
    public ResponseEntity<ApiResponse<Void>> createComponentType(
            @RequestBody @Valid ComponentTypeCreateRequest request
    ) {
        componentTypeService.createComponentType(request);
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED, null));
    }

    @GetMapping("/v1")
    public ResponseEntity<ApiResponse<List<String>>> getAllComponentTypes() {
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.OK, componentTypeService.getAllComponentTypes()));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.service.ComponentTypeServiceImplTest'`
Expected: 3개 PASS

- [ ] **Step 5: 커밋 — 건너뜀** (이 세션 커밋 금지, 작업 트리에만 남긴다)

---

### Task 2: Component 엔티티 + ComponentCreateRequest DTO

**Files:**
- Create: `src/main/java/smally/server/domain/template/entity/Component.java`
- Create: `src/main/java/smally/server/domain/template/dto/ComponentCreateRequest.java`
- Test: `src/test/java/smally/server/domain/template/dto/ComponentCreateRequestTest.java`

**Interfaces:**
- Produces:
  - `Component` — getters: `getId()`, `getName()`, `getComponentType()`, `getFrontendBinding()`, `getDataSchema()`, `getOptionSchema()`; 빌더 동명
  - `ComponentCreateRequest(String name, String componentType, String frontendBinding, Map<String,Object> dataSchema, Map<String,Object> optionSchema)` + `Component toComponent()`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/template/dto/ComponentCreateRequestTest.java`:

```java
package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.template.entity.Component;

class ComponentCreateRequestTest {

    @Test
    void toComponent_모든_필드를_매핑한다() {
        Map<String, Object> dataSchema = Map.of("type", "object",
                "properties", Map.of("photos", Map.of("type", "array")));
        Map<String, Object> optionSchema = Map.of("type", "object");

        ComponentCreateRequest request = new ComponentCreateRequest(
                "클래식 갤러리", "gallery", "GalleryGrid", dataSchema, optionSchema);

        Component component = request.toComponent();

        assertThat(component.getName()).isEqualTo("클래식 갤러리");
        assertThat(component.getComponentType()).isEqualTo("gallery");
        assertThat(component.getFrontendBinding()).isEqualTo("GalleryGrid");
        assertThat(component.getDataSchema()).isEqualTo(dataSchema);
        assertThat(component.getOptionSchema()).isEqualTo(optionSchema);
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.ComponentCreateRequestTest'`
Expected: 컴파일 실패

- [ ] **Step 3-a: Component 엔티티**

`src/main/java/smally/server/domain/template/entity/Component.java`:

```java
package smally.server.domain.template.entity;

import jakarta.persistence.*;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import smally.server.domain.common.entity.BaseEntity;

@Entity
@Table(name = "components")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Component extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "component_id")
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "component_type", nullable = false)
    private String componentType;

    @Column(name = "frontend_binding", nullable = false)
    private String frontendBinding;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_schema", nullable = false)
    private Map<String, Object> dataSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "option_schema")
    private Map<String, Object> optionSchema;

    @Builder
    private Component(String name, String componentType, String frontendBinding,
                      Map<String, Object> dataSchema, Map<String, Object> optionSchema) {
        this.name = name;
        this.componentType = componentType;
        this.frontendBinding = frontendBinding;
        this.dataSchema = dataSchema;
        this.optionSchema = optionSchema;
    }
}
```

- [ ] **Step 3-b: ComponentCreateRequest**

`src/main/java/smally/server/domain/template/dto/ComponentCreateRequest.java`:

```java
package smally.server.domain.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import smally.server.domain.template.entity.Component;

public record ComponentCreateRequest(
        @NotBlank(message = "컴포넌트 이름은 필수입니다.")
        String name,
        @NotBlank(message = "컴포넌트 종류는 필수입니다.")
        String componentType,
        @NotBlank(message = "프론트 연결(frontendBinding)은 필수입니다.")
        String frontendBinding,
        @NotNull(message = "데이터 스키마는 필수입니다.")
        Map<String, Object> dataSchema,
        Map<String, Object> optionSchema
) {
    public Component toComponent() {
        return Component.builder()
                .name(name)
                .componentType(componentType)
                .frontendBinding(frontendBinding)
                .dataSchema(dataSchema)
                .optionSchema(optionSchema)
                .build();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.ComponentCreateRequestTest'`
Expected: PASS

- [ ] **Step 5: 커밋 — 건너뜀**

---

### Task 3: Repository + ErrorCode/Exception + ComponentService (종류검증·메타검증·조회)

**Files:**
- Create: `src/main/java/smally/server/domain/template/repository/ComponentRepository.java`
- Modify: `src/main/java/smally/server/core/exception/ErrorCode.java`
- Create: `src/main/java/smally/server/core/exception/exceptions/ComponentException.java`
- Create: `src/main/java/smally/server/domain/template/service/ComponentService.java`
- Create: `src/main/java/smally/server/domain/template/service/ComponentServiceImpl.java`
- Test: `src/test/java/smally/server/domain/template/service/ComponentServiceImplTest.java`

**Interfaces:**
- Consumes: `ComponentTypeService.validateExists(String)` (Task 1), `SchemaValidator.validateSchema(Map)` (기존), `ComponentCreateRequest`/`Component` (Task 2)
- Produces:
  - `ComponentRepository extends JpaRepository<Component, Long>` (커스텀 메서드 없음)
  - `ErrorCode.COMPONENT_NOT_FOUND`, `ErrorCode.INVALID_COMPONENT_SCHEMA`
  - `ComponentException extends BusinessException`
  - `ComponentService`: `void createComponent(ComponentCreateRequest)`, `Component getComponent(Long id)`, `List<Component> getAllComponents()`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/template/service/ComponentServiceImplTest.java`:

```java
package smally.server.domain.template.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentException;
import smally.server.core.exception.exceptions.ComponentTypeException;
import smally.server.core.validation.SchemaValidator;
import smally.server.domain.template.dto.ComponentCreateRequest;
import smally.server.domain.template.entity.Component;
import smally.server.domain.template.repository.ComponentRepository;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ComponentServiceImplTest {

    @Mock ComponentRepository componentRepository;
    @Mock ComponentTypeService componentTypeService;
    SchemaValidator schemaValidator = new SchemaValidator(new ObjectMapper());
    ComponentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ComponentServiceImpl(componentRepository, componentTypeService, schemaValidator);
    }

    private ComponentCreateRequest request(Map<String, Object> dataSchema) {
        return new ComponentCreateRequest("클래식 갤러리", "gallery", "GalleryGrid", dataSchema, null);
    }

    @Test
    void createComponent_종류가_없으면_거절하고_저장하지_않는다() {
        doThrow(new ComponentTypeException(ErrorCode.COMPONENT_TYPE_NOT_FOUND))
                .when(componentTypeService).validateExists("gallery");

        assertThatThrownBy(() -> service.createComponent(request(Map.of("type", "object"))))
                .isInstanceOf(ComponentTypeException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMPONENT_TYPE_NOT_FOUND);

        verify(componentRepository, never()).save(any());
    }

    @Test
    void createComponent_dataSchema가_유효하지_않으면_거절하고_저장하지_않는다() {
        ComponentCreateRequest req = request(Map.of("type", 123)); // type은 문자열이어야 함

        assertThatThrownBy(() -> service.createComponent(req))
                .isInstanceOf(ComponentException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_COMPONENT_SCHEMA);

        verify(componentRepository, never()).save(any());
    }

    @Test
    void createComponent_유효하면_저장한다() {
        ComponentCreateRequest req = request(Map.of("type", "object"));

        assertThatCode(() -> service.createComponent(req)).doesNotThrowAnyException();

        verify(componentRepository).save(any(Component.class));
    }

    @Test
    void getComponent_없는_id면_COMPONENT_NOT_FOUND() {
        when(componentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getComponent(999L))
                .isInstanceOf(ComponentException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMPONENT_NOT_FOUND);
    }
}
```

> 참고: 첫 테스트는 `validateExists`가 예외를 던지도록 stub하므로 그 뒤 스키마 검증·save에 도달하지 않는다. 둘째·셋째 테스트는 `validateExists`가 아무것도 하지 않는 기본 mock 동작(통과)을 이용한다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.service.ComponentServiceImplTest'`
Expected: 컴파일 실패

- [ ] **Step 3-a: ComponentRepository**

`src/main/java/smally/server/domain/template/repository/ComponentRepository.java`:

```java
package smally.server.domain.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.template.entity.Component;

public interface ComponentRepository extends JpaRepository<Component, Long> {
}
```

- [ ] **Step 3-b: ErrorCode 추가**

`ErrorCode.java`에서 마지막 항목 `COMPONENT_TYPE_NOT_FOUND(...)`(Task 1에서 추가됨)를 다음으로 교체(세미콜론은 마지막에 유지):

```java
    COMPONENT_TYPE_NOT_FOUND(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "존재하지 않는 컴포넌트 종류입니다."),
    COMPONENT_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "컴포넌트를 찾을 수 없습니다."),
    INVALID_COMPONENT_SCHEMA(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "컴포넌트 스키마가 유효한 JSON Schema가 아닙니다.");
```

- [ ] **Step 3-c: ComponentException**

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
    void createComponent(ComponentCreateRequest request);
    Component getComponent(Long id);
    List<Component> getAllComponents();
}
```

- [ ] **Step 3-e: ComponentServiceImpl**

`src/main/java/smally/server/domain/template/service/ComponentServiceImpl.java`:

```java
package smally.server.domain.template.service;

import java.util.List;
import java.util.Map;
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
    private final ComponentTypeService componentTypeService;
    private final SchemaValidator schemaValidator;

    @Override
    @Transactional
    public void createComponent(ComponentCreateRequest request) {
        componentTypeService.validateExists(request.componentType());

        validateSchemaOrThrow(request.dataSchema());
        if (request.optionSchema() != null) {
            validateSchemaOrThrow(request.optionSchema());
        }

        componentRepository.save(request.toComponent());
    }

    @Override
    @Transactional(readOnly = true)
    public Component getComponent(Long id) {
        return componentRepository.findById(id)
                .orElseThrow(() -> new ComponentException(ErrorCode.COMPONENT_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Component> getAllComponents() {
        return componentRepository.findAll();
    }

    private void validateSchemaOrThrow(Map<String, Object> schema) {
        if (!schemaValidator.validateSchema(schema).isEmpty()) {
            throw new ComponentException(ErrorCode.INVALID_COMPONENT_SCHEMA);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.service.ComponentServiceImplTest'`
Expected: 4개 PASS

- [ ] **Step 5: 커밋 — 건너뜀**

---

### Task 4: ComponentResponse DTO + ComponentController

편집기용 조회 응답(`data_schema` 비노출)과 REST 엔드포인트.

**Files:**
- Create: `src/main/java/smally/server/domain/template/dto/ComponentResponse.java`
- Create: `src/main/java/smally/server/domain/template/controller/ComponentController.java`
- Test: `src/test/java/smally/server/domain/template/dto/ComponentResponseTest.java`

**Interfaces:**
- Consumes: `Component` (Task 2), `ComponentService` (Task 3)
- Produces: `ComponentResponse(Long id, String name, String componentType, String frontendBinding, Map<String,Object> optionSchema)` + `from(Component)`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/template/dto/ComponentResponseTest.java`:

```java
package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.template.entity.Component;

class ComponentResponseTest {

    @Test
    void from_editor_필드를_노출하고_dataSchema는_담지_않는다() {
        Map<String, Object> optionSchema = Map.of("type", "object");
        Component component = Component.builder()
                .name("클래식 갤러리")
                .componentType("gallery")
                .frontendBinding("GalleryGrid")
                .dataSchema(Map.of("type", "object")) // 서버 검증 전용
                .optionSchema(optionSchema)
                .build();

        ComponentResponse response = ComponentResponse.from(component);

        assertThat(response.name()).isEqualTo("클래식 갤러리");
        assertThat(response.componentType()).isEqualTo("gallery");
        assertThat(response.frontendBinding()).isEqualTo("GalleryGrid");
        assertThat(response.optionSchema()).isEqualTo(optionSchema);
        // dataSchema 접근자는 존재하지 않는다(레코드 컴포넌트 5개): 컴파일 계약으로 보장
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.ComponentResponseTest'`
Expected: 컴파일 실패

- [ ] **Step 3-a: ComponentResponse**

`src/main/java/smally/server/domain/template/dto/ComponentResponse.java`:

```java
package smally.server.domain.template.dto;

import java.util.Map;
import smally.server.domain.template.entity.Component;

public record ComponentResponse(
        Long id,
        String name,
        String componentType,
        String frontendBinding,
        Map<String, Object> optionSchema
) {
    public static ComponentResponse from(Component component) {
        return new ComponentResponse(
                component.getId(),
                component.getName(),
                component.getComponentType(),
                component.getFrontendBinding(),
                component.getOptionSchema()
        );
    }
}
```

- [ ] **Step 3-b: ComponentController**

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
            @RequestBody @Valid ComponentCreateRequest request
    ) {
        componentService.createComponent(request);
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED, null));
    }

    @GetMapping("/v1")
    public ResponseEntity<ApiResponse<List<ComponentResponse>>> getAllComponents() {
        List<ComponentResponse> body = componentService.getAllComponents().stream()
                .map(ComponentResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.OK, body));
    }

    @GetMapping("/v1/{id}")
    public ResponseEntity<ApiResponse<ComponentResponse>> getComponent(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                ApiResponse.of(HttpStatus.OK, ComponentResponse.from(componentService.getComponent(id)))
        );
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.ComponentResponseTest'`
Expected: PASS

- [ ] **Step 5: 커밋 — 건너뜀**

---

## 완료 기준

- `ComponentType`(name 카테고리) 신설 + 등록/목록/존재검증(Category 미러).
- `Component`(name·componentType·frontendBinding·data_schema·option_schema) 신설 + 등록(종류 존재검증 + 스키마 메타검증) + 조회.
- 조회 응답은 `data_schema` 비노출.
- 전 태스크 스코프 테스트 통과(Task1: 3, Task2: 1, Task3: 4, Task4: 1 = 9).

## 다음 계획 (ADR-007 하위)

- **계획 B**: `OptionDefinition`(전역 옵션 마스터). `docs/superpowers/plans/2026-07-16-option-definition-catalog.md` (작성됨).
- **계획 C**: Template 레시피 전환 — `sections`(jsonb: `[{componentId, 고정옵션값, editable}]`)+`theme`, `options_schema` 폐기, 등록 시 레시피 유효성(componentId 존재) + theme 값 검증(OptionDefinition).
- **계획 D**: Invitation 저장 시 각 섹션 인스턴스를 Component `data_schema`로 검증.
