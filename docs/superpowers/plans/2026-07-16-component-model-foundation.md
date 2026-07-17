# Component 모델 파운데이션 (A′) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **구현 완료 (2026-07-17)**: 이 계획의 프로덕션 코드는 작업 트리에 구현되어 있고 `./gradlew compileJava`가 통과한다. 아래 본문은 **실제 구현된 코드에 맞춰 갱신**했다(최초 작성본과 달라진 점은 [§구현 반영 결과](#구현-반영-결과-2026-07-17) 참조). 테스트는 아직 정리되지 않았다 — [§알려진 문제](#알려진-문제) 참조.

**Goal:** 컴포넌트 종류 카테고리 `ComponentType`과, 종류를 FK로 참조하며 프론트 연결·데이터계약·컴포넌트옵션을 가지는 `Component`를 구현한다. (ADR-007 하위 계획 A′ — 종전 계획 A의 "종류/컴포넌트 뭉침"을 분리로 재설계)

**Architecture:** `ComponentType`(name 카테고리, 확장용 DB 테이블)은 기존 `Category` 도메인을 미러링하되, 존재 확인이 아니라 **엔티티를 반환**한다(`getComponentByName`). `Component`는 `componentType`(`@ManyToOne` FK), `componentUId`(프론트 연결 키 겸 외부 식별자, 고유), `data_schema`(jsonb), `option_schema`(jsonb)를 가진다. 컴포넌트 등록 시 종류 조회 + 스키마 메타검증(기존 공용 `SchemaValidator` 재사용) 후 FK를 주입해 저장한다. `data_schema`는 서버 검증 전용(비노출), `componentType`·`componentUId`·`option_schema`는 편집기용 노출. 조회는 **Redis 캐시 우선**(`RedisCacheService`).

**Tech Stack:** Spring Boot 4.1.0, Java 25, Spring Data JPA, PostgreSQL jsonb(Hibernate `@JdbcTypeCode(SqlTypes.JSON)`), Spring Data Redis(`RedisTemplate<String,Object>`), networknt json-schema-validator 3.0.6(기존 `SchemaValidator` 경유), Jackson 3(`tools.jackson`).

## Global Constraints

- **패키지 배치**: 모두 `smally.server.domain.component.*` 하위. entity/dto/repository/service/controller. 예외는 `core.exception.exceptions`, 캐시는 `core.cache`. (템플릿 도메인 `smally.server.domain.template.*`과 분리된 별도 도메인이다.)
- **jsonb 매핑**: `Map<String,Object>`(`dataSchema`/`optionSchema`)에 `@JdbcTypeCode(SqlTypes.JSON)`. `Component`는 `BaseEntity` 상속(기존 `Template`과 동일). `ComponentType`은 `Category`처럼 `BaseEntity` 미상속(최소).
- **종류 참조**: `Component.componentType`은 **`@ManyToOne(fetch = LAZY)` FK**(`@JoinColumn(name = "component_type")`). 등록 시 `ComponentTypeService.getComponentByName(String)`으로 엔티티를 가져와 `@Setter`로 주입한다. 빌더에는 `componentType`이 없다(요청 DTO가 이름만 받으므로 서비스가 조립).
- **프론트 연결**: `Component.componentUId`(String, 고유·불변). 프론트의 어느 React 컴포넌트에 연결되는지를 가리키는 키(예: `"GalleryGrid"`)이자 API·레시피가 컴포넌트를 지목하는 외부 식별자. 하나의 필드가 두 역할을 겸한다.
- **검증 재사용**: 새 검증기 만들지 않는다. 기존 `smally.server.core.validation.SchemaValidator.validateSchema(Map)`(빈 목록=유효)를 호출.
- **노출 정책**: `data_schema`는 응답에 넣지 않는다(서버 검증 전용). `Component` 응답은 `id`·`name`·`componentType`(이름 String)·`frontendBinding`·`optionSchema` 노출.
- **캐시**: `RedisCacheService`(`core.cache`)를 통해 조회 캐시. 키 `COM:{componentUId}`(상세), `COM:LIST`(목록), `COM_TYPE:`(종류 목록). TTL 30일. 캐시 실패는 로그만 남기고 삼킨다(캐시 장애가 요청을 깨지 않는다). 쓰기 시 관련 키를 갱신·무효화.
- **응답 포맷**: `ApiResponse.of(HttpStatus, body)`. 등록은 기존 컨트롤러 관례(`ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED, ...))`)를 따른다.
- **예외**: `ComponentTypeException`/`ComponentException`(기존 `CategoryException` 패턴). `BusinessException`은 `@Getter`로 `getErrorCode()` 노출.
- **테스트 스타일**: JUnit 5 + Mockito(`@ExtendWith(MockitoExtension.class)`) + AssertJ. 메서드명 한글_언더스코어. 서비스 테스트는 `SchemaValidator`를 실제 인스턴스(`new SchemaValidator(new ObjectMapper())`)로 주입, `RedisCacheService`는 `@Mock`.
- **빌드 훅**: 모든 Edit/Write가 전체 `./gradlew build`(Postgres/Redis/jwt.secret 필요)를 돌린다. 인프라 부재로 실패해도 파일 반영됨. 실질 검증은 각 태스크 스코프 테스트(`--tests`).
- **커밋 금지(이 세션)**: 태스크의 커밋 스텝은 건너뛴다. 변경은 작업 트리에만 남기고 사용자가 최종 검토 후 직접 커밋.
- **범위 밖**: Template 레시피 전환(계획 C), Invitation 검증(계획 D), OptionDefinition 전역옵션 마스터(계획 B), 수정/삭제.

---

### Task 1: ComponentType 카테고리 도메인 (Category 미러)

종류 카테고리 `ComponentType`을 기존 `Category` 도메인과 동일 패턴으로 만든다.

**Files:**
- Create: `src/main/java/smally/server/domain/component/entity/ComponentType.java`
- Create: `src/main/java/smally/server/domain/component/dto/ComponentTypeCreateRequest.java`
- Create: `src/main/java/smally/server/domain/component/repository/ComponentTypeRepository.java`
- Modify: `src/main/java/smally/server/core/exception/ErrorCode.java`
- Create: `src/main/java/smally/server/core/exception/exceptions/ComponentTypeException.java`
- Create: `src/main/java/smally/server/domain/component/service/ComponentTypeService.java`
- Create: `src/main/java/smally/server/domain/component/service/ComponentTypeServiceImpl.java`
- Create: `src/main/java/smally/server/domain/component/controller/ComponentTypeController.java`
- Test: `src/test/java/smally/server/domain/component/service/ComponentTypeServiceImplTest.java`

**Interfaces:**
- Consumes: `RedisCacheService` (`core.cache`, 기존)
- Produces:
  - `ComponentType` — `getId()`, `getName()`; 빌더 `.name`
  - `ComponentTypeCreateRequest(String name)` + `ComponentType to()`
  - `ComponentTypeRepository`: `boolean existsByName(String)`, `Optional<ComponentType> findByName(String)`
  - `ErrorCode.DUPLICATE_COMPONENT_TYPE`, `ErrorCode.COMPONENT_TYPE_NOT_FOUND`
  - `ComponentTypeException extends BusinessException`
  - `ComponentTypeService`: `void createComponentType(ComponentTypeCreateRequest)`, `List<String> getAllComponentTypes()`, `ComponentType getComponentByName(String name)`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/component/service/ComponentTypeServiceImplTest.java`:

```java
package smally.server.domain.component.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentTypeException;
import smally.server.domain.component.dto.ComponentTypeCreateRequest;
import smally.server.domain.component.entity.ComponentType;
import smally.server.domain.component.repository.ComponentTypeRepository;

@ExtendWith(MockitoExtension.class)
class ComponentTypeServiceImplTest {

    @Mock ComponentTypeRepository repository;
    @Mock RedisCacheService redisCacheService;

    ComponentTypeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ComponentTypeServiceImpl(repository, redisCacheService);
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
    void getComponentByName_없는_종류면_COMPONENT_TYPE_NOT_FOUND() {
        when(repository.findByName("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getComponentByName("nope"))
                .isInstanceOf(ComponentTypeException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMPONENT_TYPE_NOT_FOUND);
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.component.service.ComponentTypeServiceImplTest'`
Expected: 컴파일 실패

- [ ] **Step 3-a: ComponentType 엔티티**

`src/main/java/smally/server/domain/component/entity/ComponentType.java`:

```java
package smally.server.domain.component.entity;

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

`src/main/java/smally/server/domain/component/dto/ComponentTypeCreateRequest.java`:

```java
package smally.server.domain.component.dto;

import jakarta.validation.constraints.NotBlank;
import smally.server.domain.component.entity.ComponentType;

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

`src/main/java/smally/server/domain/component/repository/ComponentTypeRepository.java`:

```java
package smally.server.domain.component.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.component.entity.ComponentType;

import java.util.Optional;

public interface ComponentTypeRepository extends JpaRepository<ComponentType, Long> {
    boolean existsByName(String name);
    Optional<ComponentType> findByName(String name);
}
```

- [ ] **Step 3-d: ErrorCode 추가**

`ErrorCode.java`에서 마지막 항목 `INVALID_SECTION_VALUES(...)`를 다음으로 교체(세미콜론은 마지막에 유지):

```java
    INVALID_SECTION_VALUES(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "청첩장 입력값이 템플릿 스키마와 맞지 않습니다."),
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

`src/main/java/smally/server/domain/component/service/ComponentTypeService.java`:

```java
package smally.server.domain.component.service;

import java.util.List;
import smally.server.domain.component.dto.ComponentTypeCreateRequest;
import smally.server.domain.component.entity.ComponentType;

public interface ComponentTypeService {
    void createComponentType(ComponentTypeCreateRequest request);
    List<String> getAllComponentTypes();
    ComponentType getComponentByName(String name);
}
```

- [ ] **Step 3-g: ComponentTypeServiceImpl**

`src/main/java/smally/server/domain/component/service/ComponentTypeServiceImpl.java`:

```java
package smally.server.domain.component.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentTypeException;
import smally.server.domain.component.dto.ComponentTypeCreateRequest;
import smally.server.domain.component.entity.ComponentType;
import smally.server.domain.component.repository.ComponentTypeRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComponentTypeServiceImpl implements ComponentTypeService {

    private final static String COMPONENT_TYPE_LIST_KEY = "COM_TYPE:";
    private final static Duration COMPONENT_TYPE_CACHE_TTL = Duration.ofDays(30);

    private final ComponentTypeRepository componentTypeRepository;
    private final RedisCacheService redisCacheService;

    @Override
    @Transactional
    public void createComponentType(ComponentTypeCreateRequest request) {
        if (componentTypeRepository.existsByName(request.name())) {
            throw new ComponentTypeException(ErrorCode.DUPLICATE_COMPONENT_TYPE);
        }
        componentTypeRepository.save(request.to());
        redisCacheService.deleteCacheData(COMPONENT_TYPE_LIST_KEY);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAllComponentTypes() {
        @SuppressWarnings("unchecked")
        List<String> cached = (List<String>) redisCacheService.getCacheData(COMPONENT_TYPE_LIST_KEY, List.class);

        if (cached != null) {
            return cached;
        }

        log.warn("[Cache-miss] component type List cache miss key : {}", COMPONENT_TYPE_LIST_KEY);

        List<String> componentList = componentTypeRepository.findAll().stream()
                .map(ComponentType::getName)
                .toList();

        redisCacheService.setCacheData(COMPONENT_TYPE_LIST_KEY, new ArrayList<>(componentList), COMPONENT_TYPE_CACHE_TTL);

        return componentList;
    }

    @Override
    @Transactional(readOnly = true)
    public ComponentType getComponentByName(String name) {
        return componentTypeRepository.findByName(name).orElseThrow(
                () -> new ComponentTypeException(ErrorCode.COMPONENT_TYPE_NOT_FOUND)
        );
    }
}
```

> `Type::toList()`가 반환하는 불변 리스트를 그대로 캐시에 넣으면 역직렬화 계약이 흔들릴 수 있어 `new ArrayList<>(...)`로 감싼다.

- [ ] **Step 3-h: ComponentTypeController**

`src/main/java/smally/server/domain/component/controller/ComponentTypeController.java`:

```java
package smally.server.domain.component.controller;

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
import smally.server.domain.component.dto.ComponentTypeCreateRequest;
import smally.server.domain.component.service.ComponentTypeService;

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

Run: `./gradlew test --tests 'smally.server.domain.component.service.ComponentTypeServiceImplTest'`
Expected: 3개 PASS

- [ ] **Step 5: 커밋 — 건너뜀** (이 세션 커밋 금지, 작업 트리에만 남긴다)

---

### Task 2: Component 엔티티 + ComponentCreateRequest DTO

**Files:**
- Create: `src/main/java/smally/server/domain/component/entity/Component.java`
- Create: `src/main/java/smally/server/domain/component/dto/ComponentCreateRequest.java`
- Test: `src/test/java/smally/server/domain/component/dto/ComponentCreateRequestTest.java`

**Interfaces:**
- Produces:
  - `Component` — getters: `getId()`, `getName()`, `getComponentUId()`, `getComponentType()`(→`ComponentType`), `getDataSchema()`, `getOptionSchema()`; `setComponentType(ComponentType)`; 빌더 `.name/.componentUId/.dataSchema/.optionSchema`
  - `ComponentCreateRequest(String name, String componentTypeName, String componentUId, Map<String,Object> dataSchema, Map<String,Object> optionSchema)` + `Component toComponent()`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/component/dto/ComponentCreateRequestTest.java`:

```java
package smally.server.domain.component.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import smally.server.domain.component.entity.Component;

class ComponentCreateRequestTest {

    @Test
    void toComponent_종류를_뺀_필드를_매핑한다() {
        Map<String, Object> dataSchema = Map.of("type", "object",
                "properties", Map.of("photos", Map.of("type", "array")));
        Map<String, Object> optionSchema = Map.of("type", "object");

        ComponentCreateRequest request = new ComponentCreateRequest(
                "클래식 갤러리", "gallery", "GalleryGrid", dataSchema, optionSchema);

        Component component = request.toComponent();

        assertThat(component.getName()).isEqualTo("클래식 갤러리");
        assertThat(component.getComponentUId()).isEqualTo("GalleryGrid");
        assertThat(component.getDataSchema()).isEqualTo(dataSchema);
        assertThat(component.getOptionSchema()).isEqualTo(optionSchema);
        // componentType은 FK 엔티티라 DTO가 채우지 않는다 — 서비스가 조회해 setter로 주입한다.
        assertThat(component.getComponentType()).isNull();
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.component.dto.ComponentCreateRequestTest'`
Expected: 컴파일 실패

- [ ] **Step 3-a: Component 엔티티**

`src/main/java/smally/server/domain/component/entity/Component.java`:

```java
package smally.server.domain.component.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import smally.server.domain.common.entity.BaseEntity;

import java.util.Map;

@Entity
@Table(name = "components")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Component extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "component_id")
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String componentUId;

    @Column(nullable = false)
    private String name;

    @Setter
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "component_type", nullable = false)
    private ComponentType componentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_schema", nullable = false)
    private Map<String, Object> dataSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "option_schema")
    private Map<String, Object> optionSchema;

    @Builder
    private Component(String name, String componentUId,
                      Map<String, Object> dataSchema, Map<String, Object> optionSchema) {
        this.name = name;
        this.componentUId = componentUId;
        this.dataSchema = dataSchema;
        this.optionSchema = optionSchema;
    }

}
```

- [ ] **Step 3-b: ComponentCreateRequest**

`src/main/java/smally/server/domain/component/dto/ComponentCreateRequest.java`:

```java
package smally.server.domain.component.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import smally.server.domain.component.entity.Component;

public record ComponentCreateRequest(
        @NotBlank(message = "컴포넌트 이름은 필수입니다.")
        String name,
        @NotBlank(message = "컴포넌트 종류는 필수입니다.")
        String componentTypeName,
        @NotBlank(message = "프론트 연결(componentUId)은 필수입니다.")
        String componentUId,
        @NotNull(message = "데이터 스키마는 필수입니다.")
        Map<String, Object> dataSchema,
        Map<String, Object> optionSchema
) {
    public Component toComponent() {
        return Component.builder()
                .name(name)
                .componentUId(componentUId)
                .dataSchema(dataSchema)
                .optionSchema(optionSchema)
                .build();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.component.dto.ComponentCreateRequestTest'`
Expected: PASS

- [ ] **Step 5: 커밋 — 건너뜀**

---

### Task 3: Repository + ErrorCode/Exception + ComponentService (종류조회·메타검증·조회·캐시)

**Files:**
- Create: `src/main/java/smally/server/domain/component/repository/ComponentRepository.java`
- Modify: `src/main/java/smally/server/core/exception/ErrorCode.java`
- Create: `src/main/java/smally/server/core/exception/exceptions/ComponentException.java`
- Create: `src/main/java/smally/server/domain/component/service/ComponentService.java`
- Create: `src/main/java/smally/server/domain/component/service/ComponentServiceImpl.java`
- Test: `src/test/java/smally/server/domain/component/service/ComponentServiceImplTest.java`

**Interfaces:**
- Consumes: `ComponentTypeService.getComponentByName(String)` (Task 1), `SchemaValidator.validateSchema(Map)` (기존), `RedisCacheService` (기존), `ComponentCreateRequest`/`Component` (Task 2), `ComponentResponse` (Task 4)
- Produces:
  - `ComponentRepository extends JpaRepository<Component, Long>` + `Optional<Component> findByComponentUId(String)`
  - `ErrorCode.COMPONENT_NOT_FOUND`, `ErrorCode.INVALID_COMPONENT_SCHEMA`
  - `ComponentException extends BusinessException`
  - `ComponentService`: `void createComponent(ComponentCreateRequest)`, `ComponentResponse getComponent(String componentUid)`, `List<ComponentResponse> getAllComponents()`

> **주의**: 작업 트리의 실제 리포지토리 메서드는 `findbyComponentUid`(소문자 `b`)로 되어 있다. Spring Data는 `find...By` 패턴을 요구하므로 이 이름은 컨텍스트 기동 시 깨질 가능성이 높다. [§알려진 문제](#알려진-문제) 참조 — 아래 코드 블록은 올바른 이름(`findByComponentUId`)을 쓴다.

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/component/service/ComponentServiceImplTest.java`:

```java
package smally.server.domain.component.service;

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
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentException;
import smally.server.core.exception.exceptions.ComponentTypeException;
import smally.server.core.validation.SchemaValidator;
import smally.server.domain.component.dto.ComponentCreateRequest;
import smally.server.domain.component.entity.Component;
import smally.server.domain.component.entity.ComponentType;
import smally.server.domain.component.repository.ComponentRepository;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ComponentServiceImplTest {

    @Mock ComponentRepository componentRepository;
    @Mock ComponentTypeService componentTypeService;
    @Mock RedisCacheService redisCacheService;
    SchemaValidator schemaValidator = new SchemaValidator(new ObjectMapper());
    ComponentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ComponentServiceImpl(
                componentRepository, componentTypeService, schemaValidator, redisCacheService);
    }

    private ComponentCreateRequest request(Map<String, Object> dataSchema) {
        return new ComponentCreateRequest("클래식 갤러리", "gallery", "GalleryGrid", dataSchema, null);
    }

    @Test
    void createComponent_종류가_없으면_거절하고_저장하지_않는다() {
        doThrow(new ComponentTypeException(ErrorCode.COMPONENT_TYPE_NOT_FOUND))
                .when(componentTypeService).getComponentByName("gallery");

        assertThatThrownBy(() -> service.createComponent(request(Map.of("type", "object"))))
                .isInstanceOf(ComponentTypeException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMPONENT_TYPE_NOT_FOUND);

        verify(componentRepository, never()).save(any());
    }

    @Test
    void createComponent_dataSchema가_유효하지_않으면_거절하고_저장하지_않는다() {
        when(componentTypeService.getComponentByName("gallery"))
                .thenReturn(ComponentType.builder().name("gallery").build());

        assertThatThrownBy(() -> service.createComponent(request(Map.of("type", 123)))) // type은 문자열이어야 함
                .isInstanceOf(ComponentException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_COMPONENT_SCHEMA);

        verify(componentRepository, never()).save(any());
    }

    @Test
    void createComponent_유효하면_종류를_주입해_저장한다() {
        ComponentType gallery = ComponentType.builder().name("gallery").build();
        when(componentTypeService.getComponentByName("gallery")).thenReturn(gallery);

        assertThatCode(() -> service.createComponent(request(Map.of("type", "object"))))
                .doesNotThrowAnyException();

        verify(componentRepository).save(any(Component.class));
    }

    @Test
    void getComponent_캐시에_없고_DB에도_없으면_COMPONENT_NOT_FOUND() {
        when(componentRepository.findByComponentUId("GalleryGrid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getComponent("GalleryGrid"))
                .isInstanceOf(ComponentException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMPONENT_NOT_FOUND);
    }
}
```

> 참고: 첫 테스트는 `getComponentByName`이 예외를 던지도록 stub하므로 그 뒤 스키마 검증·save에 도달하지 않는다. `redisCacheService.getCacheData`는 mock 기본값 `null`을 반환하므로 캐시 미스 경로를 탄다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.component.service.ComponentServiceImplTest'`
Expected: 컴파일 실패

- [ ] **Step 3-a: ComponentRepository**

`src/main/java/smally/server/domain/component/repository/ComponentRepository.java`:

```java
package smally.server.domain.component.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.component.entity.Component;

import java.util.Optional;

public interface ComponentRepository extends JpaRepository<Component, Long> {
    Optional<Component> findByComponentUId(String componentUid);
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

`src/main/java/smally/server/domain/component/service/ComponentService.java`:

```java
package smally.server.domain.component.service;

import java.util.List;
import smally.server.domain.component.dto.ComponentCreateRequest;
import smally.server.domain.component.dto.ComponentResponse;

public interface ComponentService {
    void createComponent(ComponentCreateRequest request);
    ComponentResponse getComponent(String componentUid);
    List<ComponentResponse> getAllComponents();
}
```

> 조회는 엔티티가 아니라 `ComponentResponse`를 반환한다. 캐시에 담기는 단위가 응답 DTO이기 때문이다(엔티티를 캐시에 넣으면 지연로딩 프록시가 직렬화된다).

- [ ] **Step 3-e: ComponentServiceImpl**

`src/main/java/smally/server/domain/component/service/ComponentServiceImpl.java`:

```java
package smally.server.domain.component.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentException;
import smally.server.core.validation.SchemaValidator;
import smally.server.domain.component.dto.ComponentCreateRequest;
import smally.server.domain.component.dto.ComponentResponse;
import smally.server.domain.component.entity.Component;
import smally.server.domain.component.entity.ComponentType;
import smally.server.domain.component.repository.ComponentRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComponentServiceImpl implements ComponentService {

    private final static String COMPONENT_CACHE_kEY = "COM:";
    private final static String COMPONENT_LIST_CACHE_kEY = "COM:LIST";
    private final static Duration COMPONENT_CACHE_TTL = Duration.ofDays(30);

    private final ComponentRepository componentRepository;
    private final ComponentTypeService componentTypeService;
    private final SchemaValidator schemaValidator;
    private final RedisCacheService redisCacheService;

    @Override
    @Transactional
    public void createComponent(ComponentCreateRequest request) {
        ComponentType componentType = componentTypeService.getComponentByName(request.componentTypeName());

        validateSchemaOrThrow(request.dataSchema());
        if (request.optionSchema() != null) {
            validateSchemaOrThrow(request.optionSchema());
        }

        Component component = request.toComponent();
        component.setComponentType(componentType);

        componentRepository.save(component);

        ComponentResponse cacheData = ComponentResponse.from(component);
        redisCacheService.setCacheData(
                COMPONENT_CACHE_kEY + component.getComponentUId(), cacheData, COMPONENT_CACHE_TTL);
        redisCacheService.deleteCacheData(COMPONENT_LIST_CACHE_kEY);
    }

    @Override
    @Transactional(readOnly = true)
    public ComponentResponse getComponent(String componentUid) {
        String componentKey = COMPONENT_CACHE_kEY + componentUid;
        ComponentResponse cached = redisCacheService.getCacheData(componentKey, ComponentResponse.class);

        if (cached != null) {
            return cached;
        }
        log.warn("[Cache-miss] component detail cache miss key : {}", componentUid);
        Component component = componentRepository.findByComponentUId(componentUid)
                .orElseThrow(() -> new ComponentException(ErrorCode.COMPONENT_NOT_FOUND));

        ComponentResponse response = ComponentResponse.from(component);
        redisCacheService.setCacheData(componentKey, response, COMPONENT_CACHE_TTL);

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComponentResponse> getAllComponents() {
        @SuppressWarnings("unchecked")
        List<ComponentResponse> cached =
                (List<ComponentResponse>) redisCacheService.getCacheData(COMPONENT_LIST_CACHE_kEY, List.class);

        if (cached != null) {
            return cached;
        }

        log.warn("[Cache-miss] component List cache miss key : {}", COMPONENT_LIST_CACHE_kEY);
        List<ComponentResponse> responses = componentRepository.findAll().stream()
                .map(ComponentResponse::from)
                .toList();

        redisCacheService.setCacheData(
                COMPONENT_LIST_CACHE_kEY, new ArrayList<>(responses), COMPONENT_CACHE_TTL);

        return responses;
    }

    private void validateSchemaOrThrow(Map<String, Object> schema) {
        if (!schemaValidator.validateSchema(schema).isEmpty()) {
            throw new ComponentException(ErrorCode.INVALID_COMPONENT_SCHEMA);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.component.service.ComponentServiceImplTest'`
Expected: 4개 PASS

- [ ] **Step 5: 커밋 — 건너뜀**

---

### Task 4: ComponentResponse DTO + ComponentController

편집기용 조회 응답(`data_schema` 비노출)과 REST 엔드포인트.

**Files:**
- Create: `src/main/java/smally/server/domain/component/dto/ComponentResponse.java`
- Create: `src/main/java/smally/server/domain/component/controller/ComponentController.java`
- Test: `src/test/java/smally/server/domain/component/dto/ComponentResponseTest.java`

**Interfaces:**
- Consumes: `Component` (Task 2), `ComponentService` (Task 3)
- Produces: `ComponentResponse(Long id, String name, String componentType, String frontendBinding, Map<String,Object> optionSchema)` + `from(Component)`

> **주의**: 응답의 `frontendBinding` 필드에는 엔티티의 `componentUId` 값이 담긴다. 같은 개념을 엔티티와 API가 서로 다른 이름으로 부르는 상태다. [§알려진 문제](#알려진-문제) 참조.

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/component/dto/ComponentResponseTest.java`:

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
        assertThat(response.frontendBinding()).isEqualTo("GalleryGrid"); // = componentUId
        assertThat(response.optionSchema()).isEqualTo(optionSchema);
        // dataSchema 접근자는 존재하지 않는다(레코드 컴포넌트 5개): 컴파일 계약으로 보장
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.component.dto.ComponentResponseTest'`
Expected: 컴파일 실패

- [ ] **Step 3-a: ComponentResponse**

`src/main/java/smally/server/domain/component/dto/ComponentResponse.java`:

```java
package smally.server.domain.component.dto;

import java.util.Map;
import smally.server.domain.component.entity.Component;

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
                component.getComponentType().getName(),
                component.getComponentUId(),
                component.getOptionSchema()
        );
    }
}
```

- [ ] **Step 3-b: ComponentController**

`src/main/java/smally/server/domain/component/controller/ComponentController.java`:

```java
package smally.server.domain.component.controller;

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
import smally.server.domain.component.dto.ComponentCreateRequest;
import smally.server.domain.component.dto.ComponentResponse;
import smally.server.domain.component.service.ComponentService;

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
        List<ComponentResponse> body = componentService.getAllComponents();
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.OK, body));
    }

    @GetMapping("/v1/{componentUid}")
    public ResponseEntity<ApiResponse<ComponentResponse>> getComponent(
            @PathVariable String componentUid
    ) {
        return ResponseEntity.ok(
                ApiResponse.of(HttpStatus.OK, componentService.getComponent(componentUid))
        );
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.component.dto.ComponentResponseTest'`
Expected: PASS

- [ ] **Step 5: 커밋 — 건너뜀**

---

## 완료 기준

- `ComponentType`(name 카테고리) 신설 + 등록/목록/이름조회(Category 미러).
- `Component`(name·componentUId·componentType FK·data_schema·option_schema) 신설 + 등록(종류 조회 + 스키마 메타검증 + FK 주입) + 조회.
- 조회 응답은 `data_schema` 비노출. 상세·목록은 Redis 캐시 경유.
- 전 태스크 스코프 테스트 통과(Task1: 3, Task2: 1, Task3: 4, Task4: 1 = 9).

## 구현 반영 결과 (2026-07-17)

최초 계획과 실제 구현이 달라진 지점. 문서를 구현에 맞춰 갱신했다.

| 최초 계획 | 실제 구현 | 이유 |
|---|---|---|
| 패키지 `domain.template.*` | **`domain.component.*`** | 컴포넌트를 템플릿과 별개 도메인으로 분리 |
| `Component.componentType`은 String, `validateExists`로 존재만 확인 | **`@ManyToOne` FK**, `getComponentByName`으로 엔티티 조회 후 `@Setter` 주입 | ADR-007 결정 1(종류/컴포넌트 FK 분리)에 맞춤 |
| `frontendBinding` 필드 | **`componentUId`**(고유·불변) | 프론트 연결 키를 외부 식별자로 겸용 |
| `ComponentCreateRequest(name, componentType, frontendBinding, …)` | **`(name, componentTypeName, componentUId, …)`** | 위 두 변경의 연쇄 |
| `getComponent(Long id)` → `Component` | **`getComponent(String componentUid)` → `ComponentResponse`** | 외부 식별자로 조회, 캐시 단위를 DTO로 |
| `getAllComponents()` → `List<Component>` | **`List<ComponentResponse>`** | 동일 |
| 캐시 없음 | **Redis 캐시 도입**(`RedisCacheService`, TTL 30일) | 컴포넌트는 읽기 편중·변경 드묾 |

`RedisCacheService` 자체는 이 계획 범위 밖에서 추가됐다. 개념 설명은 [Spring DI와 Redis 캐시 서비스](../../GUIDE-spring-di-redis-cache.md) 참조.

## 알려진 문제

문서 갱신 시점(2026-07-17) 작업 트리에 남아 있는 문제. **아직 수정되지 않았다.**

1. **`ComponentRepository.findbyComponentUid`** — 소문자 `b`. Spring Data는 `find…By` 패턴을 요구하므로 이 이름은 파생 쿼리로 해석되지 않고 애플리케이션 기동 시 `PropertyReferenceException`이 날 가능성이 높다(Postgres 없이 확인하지 못해 단정하지 않는다). `findByComponentUId`로 고쳐야 한다.
2. **엔티티 `componentUId` ↔ 응답 `frontendBinding` 이름 불일치** — `ComponentResponse.from`이 `getComponentUId()`를 `frontendBinding` 자리에 담는다. 같은 개념을 두 이름으로 부르므로 한쪽으로 통일이 필요하다.
3. **테스트가 옛 패키지·옛 모델에 머물러 있다** — `src/test/java/smally/server/domain/template/` 아래의 `ComponentCreateRequestTest`·`ComponentResponseTest`·`ComponentServiceImplTest`·`ComponentTypeServiceImplTest`가 `domain/component/`로 옮겨지지 않았고, 존재하지 않는 빌더(`.componentType`/`.frontendBinding`)를 호출해 **`./gradlew compileTestJava`가 실패한다**. 위 태스크의 테스트 코드 블록이 올바른 형태다.
4. **`ComponentCreateRequest.toComponent()`가 `componentType`을 채우지 않는다** — 의도된 설계(서비스가 FK 주입)지만, DTO만 보고 엔티티를 만들면 `componentType`이 null인 채 저장 시도된다. 서비스를 우회하는 경로가 생기지 않도록 주의.

## 다음 계획 (ADR-007 하위)

- **계획 B**: `OptionDefinition`(전역 옵션 마스터). `docs/superpowers/plans/2026-07-16-option-definition-catalog.md` (작성됨).
- **계획 C**: Template 레시피 전환 — `sections`(jsonb: `[{componentUId, 고정옵션값, editable}]`)+`theme`, `options_schema` 폐기, 등록 시 레시피 유효성(componentUId 존재) + theme 값 검증(OptionDefinition). `docs/superpowers/plans/2026-07-17-template-recipe.md` (작성됨).
- **계획 D**: Invitation 저장 시 각 섹션 인스턴스를 Component `data_schema`로 검증.
