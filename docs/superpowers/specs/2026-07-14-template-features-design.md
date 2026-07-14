# 템플릿 기능 설계 (등록 + 메타검증 + 조회)

- 작성일: 2026-07-14
- 작성자: jasmin
- 관련 문서: [ADR-002](../../adr/ADR-002-template-schema-jsonschema-jsonb.md), [ADR-001](../../adr/ADR-001-image-storage-s3-presigned.md)

## 1. 목적과 범위

템플릿 도메인은 **"관리자가 만든 청첩장 틀 + 그 틀의 검증 규칙(JSON Schema)"의 저장소**다.
이번 설계 범위는 다음 세 가지로 한정한다.

- **등록**: 관리자가 템플릿을 등록한다. 등록 시 `section_schema`가 그 자체로 유효한 JSON Schema인지 **메타검증**한다.
- **조회**: 사용자가 템플릿 목록/단건을 조회한다.
- 검증 엔진 분리: JSON Schema 검증기를 공용 컴포넌트로 두어, 청첩장 데이터 검증(invitation 도메인)에서도 재사용한다.

### 범위 밖 (이번 설계 제외)

- 템플릿 **수정/삭제** — 스키마 버전 관리가 없어(ADR-002 4항) 발행된 청첩장과 불일치 위험이 있으므로 후속 과제.
- `Category` 엔티티와 `Template.category`(String)의 **FK 연결** — 현상태(String) 유지. 정합성은 앱이 책임. 필요 시 별도 과제.
- 청첩장 `section_values` 데이터 검증의 **실제 구현** — 이번엔 공용 검증기 인터페이스만 정의하고, invitation 도메인에서 호출하는 연동은 후속.

## 2. 검증의 세 갈래 (책임 분리)

검증을 하나로 뭉치지 않고 트리거 주체·시점으로 나눈다.

| 구분 | 무엇을 검증 | 시점 | 트리거 주체 |
|---|---|---|---|
| 스키마 메타검증 | `section_schema` 자체가 유효한 JSON Schema인가 | 템플릿 등록 시 | **template** |
| 데이터 검증 | `section_values`가 템플릿 스키마를 지키는가 | 청첩장 저장 시 | **invitation** (범위 밖) |
| 검증 엔진 | 위 둘이 공통으로 쓰는 JSON Schema 검증기 | - | **공용 컴포넌트** |

핵심: `SchemaValidator`(엔진)는 하나만 두고, 메타검증은 template이, 데이터검증은 invitation이 각각 호출한다.

## 3. 구성 요소

```
domain/template
├── entity/Template.java            (기존) section_schema, variants, category(String) 등
├── entity/Category.java            (기존) title (String, FK 없음 — 현상태 유지)
├── dto/TemplateCreateRequest.java  (채움) 등록 요청
├── dto/TemplateResponse.java       (채움) 단건 응답 — section_schema 포함
├── dto/TemplateSummaryResponse.java(신규) 목록 응답 — section_schema 제외
├── service/TemplateService.java    (기존 인터페이스) create/get/getList
└── service/TemplateServiceImpl.java(신규) 구현 — 등록 시 메타검증 호출

공용
└── SchemaValidator                 (신규) JSON Schema 메타검증 + 데이터검증 진입점
```

### 왜 응답 DTO를 둘로 나누나

- **단건**(`TemplateResponse`): 편집 화면이 입력 폼을 그려야 하므로 `section_schema`를 **포함**한다.
- **목록**(`TemplateSummaryResponse`): 카드 그리드용이라 `section_schema`가 불필요하고 무겁다. 썸네일·이름·카테고리만 내린다.
- 하나로 합치면 목록 조회마다 큰 스키마를 매 항목 실어 나른다. 그래서 분리한다.

## 4. 등록 + 메타검증

### 요청 (`TemplateCreateRequest`)

```json
{
  "name": "클래식 화이트",
  "thumbnail": "https://cdn.smally/thumb/classic.png",
  "category": "클래식",
  "sectionSchema": {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object",
    "required": ["greeting", "gallery"],
    "properties": {
      "greeting": {
        "type": "object",
        "required": ["text"],
        "properties": { "text": { "type": "string", "maxLength": 500 } }
      },
      "gallery": {
        "type": "object",
        "required": ["photos"],
        "properties": {
          "photos": {
            "type": "array", "minItems": 1, "maxItems": 10,
            "items": { "type": "string", "format": "uri" }
          }
        }
      }
    }
  },
  "variants": { "color": ["white", "beige"], "font": ["serif", "sans"] }
}
```

### 처리 흐름

```
createTemplate(request):
  1. SchemaValidator.validateSchema(request.sectionSchema)
       - 라이브러리 메타스키마(draft 2020-12)로 sectionSchema 문법 검증
       - 검증 로직을 직접 구현하지 않고 라이브러리에 위임 (ADR-002)
  2. 실패 → 400, ErrorCode(예: INVALID_TEMPLATE_SCHEMA) + 위반 이유
  3. 성공 → templateUid(UUID) 발급, Template 저장
  4. TemplateResponse 반환
```

### 응답 (`TemplateResponse`, 등록 직후 = 단건 조회와 동일 형태)

```json
{
  "templateUid": "a1b2c3d4-....",
  "name": "클래식 화이트",
  "thumbnail": "https://cdn.smally/thumb/classic.png",
  "category": "클래식",
  "sectionSchema": { "...요청과 동일...": "..." },
  "variants": { "color": ["white", "beige"], "font": ["serif", "sans"] }
}
```

## 5. 조회 응답

### 단건 — `GET /templates/{templateUid}`

`section_schema` **포함** (편집 폼 렌더용). 형태는 4절의 `TemplateResponse`와 동일.

### 목록 — `GET /templates?category=클래식&page=0&size=20`

`section_schema` **제외**. `category`는 선택적 필터. 페이징 적용.

```json
{
  "content": [
    { "templateUid": "a1b2...", "name": "클래식 화이트", "thumbnail": "https://cdn.smally/thumb/classic.png", "category": "클래식" },
    { "templateUid": "e5f6...", "name": "클래식 베이지", "thumbnail": "https://cdn.smally/thumb/beige.png", "category": "클래식" }
  ],
  "page": 0, "size": 20, "totalElements": 2
}
```

> 참고: 현재 `TemplateService.getTemplateList`는 `java.awt.print.Pageable`을 import하고 있다(오타성 오류). 구현 시 `org.springframework.data.domain.Pageable`로 교정 필요.

## 6. 에러 처리

| 상황 | 응답 | 비고 |
|---|---|---|
| 등록 스키마가 유효하지 않은 JSON Schema | 400 + 위반 이유 | 메타검증 실패 |
| 존재하지 않는 `templateUid` 단건 조회 | 404 | |
| 정상 등록/조회 | 200 / 201 | |

`ErrorCode`에 템플릿용 코드(예: `TEMPLATE_NOT_FOUND`, `INVALID_TEMPLATE_SCHEMA`) 추가 필요.

## 7. 후속 과제 (ADR-002 연계)

- JSON Schema 검증 라이브러리 확정 및 draft 버전 (예: networknt/json-schema-validator, draft 2020-12)
- 청첩장 `section_values` 데이터 검증의 invitation 도메인 연동
- 템플릿 수정/삭제 + 스키마 버전 관리 정책
- `Category` ↔ `Template.category` 정합성 강화(FK) 여부
```

