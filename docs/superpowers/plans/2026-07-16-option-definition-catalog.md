# OptionDefinition 마스터 옵션 카탈로그 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 전역/테마 옵션을 관리자가 코드 없이 데이터로 정의·관리하는 마스터 카탈로그 `OptionDefinition` 엔티티와 그 등록/조회를 구현한다. (ADR-007 하위 계획 B, 설계 결정 "B: 마스터 옵션 카탈로그 테이블")

**Architecture:** `OptionDefinition`(key·label·controlType·scope·allowedValues·defaultValue)을 1급 엔티티로 신설. 정의(카탈로그)는 관계형 행으로, 값은 여전히 jsonb(후속 계획 C의 `Template.theme`)에 저장 — ADR-002의 "값 저장은 jsonb" 원칙과 공존한다(카탈로그는 정의/레퍼런스 데이터이지 청첩장 값이 아님). 등록 시 `defaultValue`가 `allowedValues`에 드는지 검증한다. 이 카탈로그는 후속 계획 C에서 `Template.theme` 값 검증과 에디터 전역옵션 렌더의 기준이 된다. 코드 스타일·도메인 배치는 기존 `Component`/`Category` 도메인을 그대로 따른다.

**Tech Stack:** Spring Boot 4.1.0, Java 25, Spring Data JPA, PostgreSQL jsonb(Hibernate `@JdbcTypeCode(SqlTypes.JSON)`), Jackson 3(`tools.jackson`).

## Global Constraints

- **선행 의존**: 계획 A(Component)의 `ErrorCode` 변경이 작업 트리에 존재해야 한다(마지막 코드가 `INVALID_COMPONENT_SCHEMA`). 그 뒤에 새 코드를 append 한다.
- **패키지 배치**: 모두 `smally.server.domain.template.*` 하위. entity/dto/repository/service/controller 서브패키지. 예외는 `core.exception.exceptions`.
- **jsonb 매핑**: `allowedValues`(`List<Object>`)·`defaultValue`(`Object`)에 `@JdbcTypeCode(SqlTypes.JSON)`. `Object` 스칼라도 jsonb로 직렬화된다(Jackson). 엔티티는 `BaseEntity` 상속(기존 `Component`/`Template`과 동일).
- **검증**: JSON Schema 아님. 등록 시 `defaultValue ∈ allowedValues` 멤버십만 확인(요청 객체 비교, `SchemaValidator` 불필요). `allowedValues`가 비어있지 않을 때만.
- **노출**: 카탈로그 전 필드가 에디터용 메타데이터라 응답에 전부 노출(서버 비밀 없음).
- **응답 포맷**: `ApiResponse.of(HttpStatus, body)`. 등록은 기존 컨트롤러 관례(`ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED, ...))`)를 그대로 따른다. (전송 200/바디 201 관례는 앱 전반 이슈로 이 계획 범위 밖.)
- **예외**: `OptionDefinitionException(ErrorCode)` 신설(기존 `ComponentException` 패턴). `BusinessException`은 `@Getter`로 `getErrorCode()` 노출.
- **테스트 스타일**: JUnit 5 + Mockito(`@ExtendWith(MockitoExtension.class)`) + AssertJ. 메서드명 한글_언더스코어.
- **빌드 훅**: 모든 Edit/Write가 전체 `./gradlew build`(Postgres/Redis/jwt.secret 필요)를 돌린다. 인프라 부재로 실패해도 파일 반영됨. 실질 검증은 각 태스크 스코프 테스트(`--tests`).
- **커밋**: 태스크마다 커밋. 메시지 끝에 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- **범위 밖**: Template 레시피 전환·theme 값 검증(계획 C), OptionDefinition 수정/삭제(후속).

---

### Task 1: OptionDefinition 엔티티 + OptionDefinitionCreateRequest DTO

**Files:**
- Create: `src/main/java/smally/server/domain/template/entity/OptionDefinition.java`
- Create: `src/main/java/smally/server/domain/template/dto/OptionDefinitionCreateRequest.java`
- Test: `src/test/java/smally/server/domain/template/dto/OptionDefinitionCreateRequestTest.java`

**Interfaces:**
- Produces:
  - `OptionDefinition` — getters: `getKey()`, `getLabel()`, `getControlType()`, `getScope()`, `getAllowedValues()`, `getDefaultValue()`; 빌더 동명 메서드
  - `OptionDefinitionCreateRequest(String key, String label, String controlType, String scope, List<Object> allowedValues, Object defaultValue)` + `OptionDefinition toEntity()`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/template/dto/OptionDefinitionCreateRequestTest.java`:

```java
package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import smally.server.domain.template.entity.OptionDefinition;

class OptionDefinitionCreateRequestTest {

    @Test
    void toEntity_모든_필드를_매핑한다() {
        List<Object> allowed = List.of("small", "normal", "large");
        OptionDefinitionCreateRequest request = new OptionDefinitionCreateRequest(
                "fontSize", "폰트 크기", "select", "global", allowed, "normal");

        OptionDefinition entity = request.toEntity();

        assertThat(entity.getKey()).isEqualTo("fontSize");
        assertThat(entity.getLabel()).isEqualTo("폰트 크기");
        assertThat(entity.getControlType()).isEqualTo("select");
        assertThat(entity.getScope()).isEqualTo("global");
        assertThat(entity.getAllowedValues()).isEqualTo(allowed);
        assertThat(entity.getDefaultValue()).isEqualTo("normal");
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.OptionDefinitionCreateRequestTest'`
Expected: 컴파일 실패

- [ ] **Step 3: OptionDefinition 엔티티 구현**

`src/main/java/smally/server/domain/template/entity/OptionDefinition.java`:

```java
package smally.server.domain.template.entity;

import jakarta.persistence.*;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import smally.server.domain.common.entity.BaseEntity;

@Entity
@Table(name = "option_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptionDefinition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_definition_id")
    private Long id;

    @Column(name = "option_key", nullable = false, unique = true, updatable = false)
    private String key;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String controlType;

    @Column(nullable = false)
    private String scope;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_values")
    private List<Object> allowedValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_value")
    private Object defaultValue;

    @Builder
    private OptionDefinition(String key, String label, String controlType, String scope,
                            List<Object> allowedValues, Object defaultValue) {
        this.key = key;
        this.label = label;
        this.controlType = controlType;
        this.scope = scope;
        this.allowedValues = allowedValues;
        this.defaultValue = defaultValue;
    }
}
```

> 참고: 필드명은 `key`지만 컬럼은 `option_key`(SQL 예약어 회피). `unique = true`가 이미 유니크 인덱스를 만들므로 별도 `@Index`는 두지 않는다(계획 A의 redundant-index Minor 회피).

- [ ] **Step 4: OptionDefinitionCreateRequest 구현**

`src/main/java/smally/server/domain/template/dto/OptionDefinitionCreateRequest.java`:

```java
package smally.server.domain.template.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import smally.server.domain.template.entity.OptionDefinition;

public record OptionDefinitionCreateRequest(
        @NotBlank(message = "옵션 키는 필수입니다.")
        String key,
        @NotBlank(message = "라벨은 필수입니다.")
        String label,
        @NotBlank(message = "컨트롤 타입은 필수입니다.")
        String controlType,
        @NotBlank(message = "스코프는 필수입니다.")
        String scope,
        List<Object> allowedValues,
        Object defaultValue
) {
    public OptionDefinition toEntity() {
        return OptionDefinition.builder()
                .key(key)
                .label(label)
                .controlType(controlType)
                .scope(scope)
                .allowedValues(allowedValues)
                .defaultValue(defaultValue)
                .build();
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.OptionDefinitionCreateRequestTest'`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/smally/server/domain/template/entity/OptionDefinition.java src/main/java/smally/server/domain/template/dto/OptionDefinitionCreateRequest.java src/test/java/smally/server/domain/template/dto/OptionDefinitionCreateRequestTest.java
git commit -m "feat : OptionDefinition(옵션 카탈로그) 엔티티·등록 DTO 추가

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Repository + ErrorCode/Exception + OptionDefinitionService (등록·기본값검증·중복거절·조회)

**Files:**
- Create: `src/main/java/smally/server/domain/template/repository/OptionDefinitionRepository.java`
- Modify: `src/main/java/smally/server/core/exception/ErrorCode.java`
- Create: `src/main/java/smally/server/core/exception/exceptions/OptionDefinitionException.java`
- Create: `src/main/java/smally/server/domain/template/service/OptionDefinitionService.java`
- Create: `src/main/java/smally/server/domain/template/service/OptionDefinitionServiceImpl.java`
- Test: `src/test/java/smally/server/domain/template/service/OptionDefinitionServiceImplTest.java`

**Interfaces:**
- Consumes: `OptionDefinitionCreateRequest`/`OptionDefinition` (Task 1)
- Produces:
  - `OptionDefinitionRepository`: `boolean existsByKey(String)`, `Optional<OptionDefinition> findByKey(String)`
  - `ErrorCode.DUPLICATE_OPTION_DEFINITION`, `ErrorCode.OPTION_DEFINITION_NOT_FOUND`, `ErrorCode.INVALID_OPTION_DEFAULT`
  - `OptionDefinitionException extends BusinessException`
  - `OptionDefinitionService`: `void createOptionDefinition(OptionDefinitionCreateRequest)`, `OptionDefinition getOptionDefinition(String key)`, `List<OptionDefinition> getAllOptionDefinitions()`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/template/service/OptionDefinitionServiceImplTest.java`:

```java
package smally.server.domain.template.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.OptionDefinitionException;
import smally.server.domain.template.dto.OptionDefinitionCreateRequest;
import smally.server.domain.template.entity.OptionDefinition;
import smally.server.domain.template.repository.OptionDefinitionRepository;

@ExtendWith(MockitoExtension.class)
class OptionDefinitionServiceImplTest {

    @Mock OptionDefinitionRepository repository;
    OptionDefinitionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OptionDefinitionServiceImpl(repository);
    }

    private OptionDefinitionCreateRequest request(List<Object> allowed, Object def) {
        return new OptionDefinitionCreateRequest("fontSize", "폰트 크기", "select", "global", allowed, def);
    }

    @Test
    void createOptionDefinition_기본값이_allowedValues에_없으면_거절하고_저장하지_않는다() {
        when(repository.existsByKey("fontSize")).thenReturn(false);
        OptionDefinitionCreateRequest req = request(List.of("small", "large"), "huge");

        assertThatThrownBy(() -> service.createOptionDefinition(req))
                .isInstanceOf(OptionDefinitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_OPTION_DEFAULT);

        verify(repository, never()).save(any());
    }

    @Test
    void createOptionDefinition_키가_중복이면_거절한다() {
        when(repository.existsByKey("fontSize")).thenReturn(true);
        OptionDefinitionCreateRequest req = request(List.of("small", "large"), "small");

        assertThatThrownBy(() -> service.createOptionDefinition(req))
                .isInstanceOf(OptionDefinitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_OPTION_DEFINITION);

        verify(repository, never()).save(any());
    }

    @Test
    void createOptionDefinition_기본값이_allowedValues에_있으면_저장한다() {
        when(repository.existsByKey("fontSize")).thenReturn(false);
        OptionDefinitionCreateRequest req = request(List.of("small", "large"), "large");

        assertThatCode(() -> service.createOptionDefinition(req)).doesNotThrowAnyException();

        verify(repository).save(any(OptionDefinition.class));
    }

    @Test
    void createOptionDefinition_allowedValues가_없으면_기본값검증을_건너뛴다() {
        when(repository.existsByKey("fontSize")).thenReturn(false);
        OptionDefinitionCreateRequest req = request(null, "anything");

        assertThatCode(() -> service.createOptionDefinition(req)).doesNotThrowAnyException();

        verify(repository).save(any(OptionDefinition.class));
    }

    @Test
    void getOptionDefinition_없는_키면_OPTION_DEFINITION_NOT_FOUND() {
        when(repository.findByKey("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOptionDefinition("nope"))
                .isInstanceOf(OptionDefinitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OPTION_DEFINITION_NOT_FOUND);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.service.OptionDefinitionServiceImplTest'`
Expected: 컴파일 실패

- [ ] **Step 3-a: OptionDefinitionRepository 구현**

`src/main/java/smally/server/domain/template/repository/OptionDefinitionRepository.java`:

```java
package smally.server.domain.template.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.template.entity.OptionDefinition;

public interface OptionDefinitionRepository extends JpaRepository<OptionDefinition, Long> {
    boolean existsByKey(String key);
    Optional<OptionDefinition> findByKey(String key);
}
```

- [ ] **Step 3-b: ErrorCode 추가**

`ErrorCode.java`에서 마지막 항목 `INVALID_COMPONENT_SCHEMA(...)`(계획 A에서 추가됨)를 다음으로 교체(세미콜론은 마지막에 유지):

```java
    INVALID_COMPONENT_SCHEMA(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "컴포넌트 스키마가 유효한 JSON Schema가 아닙니다."),
    DUPLICATE_OPTION_DEFINITION(HttpStatus.CONFLICT, "CONFLICT", "이미 존재하는 옵션 키입니다."),
    OPTION_DEFINITION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "옵션 정의를 찾을 수 없습니다."),
    INVALID_OPTION_DEFAULT(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "기본값이 허용값 목록에 없습니다.");
```

- [ ] **Step 3-c: OptionDefinitionException 구현**

`src/main/java/smally/server/core/exception/exceptions/OptionDefinitionException.java`:

```java
package smally.server.core.exception.exceptions;

import smally.server.core.exception.BusinessException;
import smally.server.core.exception.ErrorCode;

public class OptionDefinitionException extends BusinessException {
    public OptionDefinitionException(ErrorCode errorCode) {
        super(errorCode);
    }
}
```

- [ ] **Step 3-d: OptionDefinitionService 인터페이스**

`src/main/java/smally/server/domain/template/service/OptionDefinitionService.java`:

```java
package smally.server.domain.template.service;

import java.util.List;
import smally.server.domain.template.dto.OptionDefinitionCreateRequest;
import smally.server.domain.template.entity.OptionDefinition;

public interface OptionDefinitionService {
    void createOptionDefinition(OptionDefinitionCreateRequest request);
    OptionDefinition getOptionDefinition(String key);
    List<OptionDefinition> getAllOptionDefinitions();
}
```

- [ ] **Step 3-e: OptionDefinitionServiceImpl 구현**

`src/main/java/smally/server/domain/template/service/OptionDefinitionServiceImpl.java`:

```java
package smally.server.domain.template.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.OptionDefinitionException;
import smally.server.domain.template.dto.OptionDefinitionCreateRequest;
import smally.server.domain.template.entity.OptionDefinition;
import smally.server.domain.template.repository.OptionDefinitionRepository;

@Service
@RequiredArgsConstructor
public class OptionDefinitionServiceImpl implements OptionDefinitionService {

    private final OptionDefinitionRepository optionDefinitionRepository;

    @Override
    @Transactional
    public void createOptionDefinition(OptionDefinitionCreateRequest request) {
        if (optionDefinitionRepository.existsByKey(request.key())) {
            throw new OptionDefinitionException(ErrorCode.DUPLICATE_OPTION_DEFINITION);
        }
        if (request.allowedValues() != null && !request.allowedValues().isEmpty()
                && !request.allowedValues().contains(request.defaultValue())) {
            throw new OptionDefinitionException(ErrorCode.INVALID_OPTION_DEFAULT);
        }
        optionDefinitionRepository.save(request.toEntity());
    }

    @Override
    @Transactional(readOnly = true)
    public OptionDefinition getOptionDefinition(String key) {
        return optionDefinitionRepository.findByKey(key)
                .orElseThrow(() -> new OptionDefinitionException(ErrorCode.OPTION_DEFINITION_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OptionDefinition> getAllOptionDefinitions() {
        return optionDefinitionRepository.findAll();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.service.OptionDefinitionServiceImplTest'`
Expected: 5개 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/domain/template/repository/OptionDefinitionRepository.java src/main/java/smally/server/core/exception/ErrorCode.java src/main/java/smally/server/core/exception/exceptions/OptionDefinitionException.java src/main/java/smally/server/domain/template/service/OptionDefinitionService.java src/main/java/smally/server/domain/template/service/OptionDefinitionServiceImpl.java src/test/java/smally/server/domain/template/service/OptionDefinitionServiceImplTest.java
git commit -m "feat : 옵션 정의 등록(기본값검증·중복거절)·조회 서비스

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: OptionDefinitionResponse DTO + OptionDefinitionController

**Files:**
- Create: `src/main/java/smally/server/domain/template/dto/OptionDefinitionResponse.java`
- Create: `src/main/java/smally/server/domain/template/controller/OptionDefinitionController.java`
- Test: `src/test/java/smally/server/domain/template/dto/OptionDefinitionResponseTest.java`

**Interfaces:**
- Consumes: `OptionDefinition` (Task 1), `OptionDefinitionService` (Task 2)
- Produces: `OptionDefinitionResponse(String key, String label, String controlType, String scope, List<Object> allowedValues, Object defaultValue)` + `from(OptionDefinition)`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/smally/server/domain/template/dto/OptionDefinitionResponseTest.java`:

```java
package smally.server.domain.template.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import smally.server.domain.template.entity.OptionDefinition;

class OptionDefinitionResponseTest {

    @Test
    void from_모든_카탈로그_필드를_노출한다() {
        List<Object> allowed = List.of("small", "normal", "large");
        OptionDefinition entity = OptionDefinition.builder()
                .key("fontSize").label("폰트 크기").controlType("select").scope("global")
                .allowedValues(allowed).defaultValue("normal")
                .build();

        OptionDefinitionResponse response = OptionDefinitionResponse.from(entity);

        assertThat(response.key()).isEqualTo("fontSize");
        assertThat(response.label()).isEqualTo("폰트 크기");
        assertThat(response.controlType()).isEqualTo("select");
        assertThat(response.scope()).isEqualTo("global");
        assertThat(response.allowedValues()).isEqualTo(allowed);
        assertThat(response.defaultValue()).isEqualTo("normal");
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.OptionDefinitionResponseTest'`
Expected: 컴파일 실패

- [ ] **Step 3-a: OptionDefinitionResponse 구현**

`src/main/java/smally/server/domain/template/dto/OptionDefinitionResponse.java`:

```java
package smally.server.domain.template.dto;

import java.util.List;
import smally.server.domain.template.entity.OptionDefinition;

public record OptionDefinitionResponse(
        String key,
        String label,
        String controlType,
        String scope,
        List<Object> allowedValues,
        Object defaultValue
) {
    public static OptionDefinitionResponse from(OptionDefinition entity) {
        return new OptionDefinitionResponse(
                entity.getKey(),
                entity.getLabel(),
                entity.getControlType(),
                entity.getScope(),
                entity.getAllowedValues(),
                entity.getDefaultValue()
        );
    }
}
```

- [ ] **Step 3-b: OptionDefinitionController 구현**

`src/main/java/smally/server/domain/template/controller/OptionDefinitionController.java`:

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
import smally.server.domain.template.dto.OptionDefinitionCreateRequest;
import smally.server.domain.template.dto.OptionDefinitionResponse;
import smally.server.domain.template.service.OptionDefinitionService;

@RestController
@RequestMapping("/api/option-definition")
@RequiredArgsConstructor
public class OptionDefinitionController {

    private final OptionDefinitionService optionDefinitionService;

    @PostMapping("/v1")
    public ResponseEntity<ApiResponse<Void>> createOptionDefinition(
            @RequestBody @Valid OptionDefinitionCreateRequest request
    ) {
        optionDefinitionService.createOptionDefinition(request);
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED, null));
    }

    @GetMapping("/v1")
    public ResponseEntity<ApiResponse<List<OptionDefinitionResponse>>> getAllOptionDefinitions() {
        List<OptionDefinitionResponse> body = optionDefinitionService.getAllOptionDefinitions().stream()
                .map(OptionDefinitionResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.OK, body));
    }

    @GetMapping("/v1/{key}")
    public ResponseEntity<ApiResponse<OptionDefinitionResponse>> getOptionDefinition(
            @PathVariable String key
    ) {
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.OK,
                OptionDefinitionResponse.from(optionDefinitionService.getOptionDefinition(key))));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'smally.server.domain.template.dto.OptionDefinitionResponseTest'`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/domain/template/dto/OptionDefinitionResponse.java src/main/java/smally/server/domain/template/controller/OptionDefinitionController.java src/test/java/smally/server/domain/template/dto/OptionDefinitionResponseTest.java
git commit -m "feat : 옵션 정의 조회 응답·컨트롤러

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 완료 기준

- `OptionDefinition`(key·label·controlType·scope·allowedValues·defaultValue) 마스터 카탈로그 엔티티 신설.
- 등록 시 키 중복 거절 + `defaultValue ∈ allowedValues` 검증.
- 전 필드 조회 노출, REST 등록/조회 엔드포인트.
- 전 태스크 스코프 테스트 통과(Task1: 1, Task2: 5, Task3: 1 = 7).

## 다음 계획 (ADR-007 하위)

- **계획 C**: Template 레시피 전환 — `sections`(jsonb 순서 리스트)+`theme` 추가, `options_schema` 폐기, 등록 시 레시피 유효성(참조 Component 존재·variant 유효) + **theme 값 검증**(각 theme 키가 OptionDefinition 카탈로그에 존재하고 값이 `allowedValues`에 듦). 이때 ADR-007에 "theme는 OptionDefinition 카탈로그로 검증" 보강 노트 추가.
- **계획 D**: Invitation 저장 시 각 섹션 인스턴스를 Component `data_schema`로 검증(`SchemaValidator.validateData`) + `selected_options` 검증.
