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

### 핵심 전제: `section_schema`는 서버 검증 전용 (프론트에 내려주지 않음)

실제 청첩장 디자인은 프론트엔드의 **사람이 손으로 만든 React 컴포넌트**이며(ARCHITECTURE 15·17줄), 그 컴포넌트가 **입력 폼과 필드 구조를 이미 자체적으로 알고 있다.** 따라서 프론트는 폼을 그리기 위해 `section_schema`를 받을 필요가 없다.

`section_schema`의 유일한 소비자는 **서버 자신**이다. 서버는 이를 DB에 검증용으로 보관하고, 청첩장 저장 시점에 로드해 `section_values`를 검증한다. 스키마는 서버 밖으로 나가지 않는다.

- 조회 응답(목록·단건)에서 `section_schema`를 **제외**한다.
- 조회 시 프론트가 필요로 하는 것은 `templateUid`(컴포넌트 매칭 키)와 `variants`(사용자가 고르는 색·폰트 선택지)다.

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
├── entity/Template.java            (기존) section_schema(서버 검증용), variants, category(String) 등
├── entity/Category.java            (기존) title (String, FK 없음 — 현상태 유지)
├── dto/TemplateCreateRequest.java  (채움) 등록 요청 — section_schema 포함(등록 시에만 들어옴)
├── dto/TemplateResponse.java       (채움) 조회 응답 — section_schema 제외
├── service/TemplateService.java    (기존 인터페이스) create/get/getList
└── service/TemplateServiceImpl.java(신규) 구현 — 등록 시 메타검증 호출

공용
└── SchemaValidator                 (신규) JSON Schema 메타검증 + 데이터검증 진입점
```

### 응답 DTO는 하나로 충분하다

`section_schema`를 어차피 조회에서 안 내려주므로, 목록과 단건이 실어 나르는 필드가 사실상 같다. 따라서 **`TemplateResponse` 하나**로 목록·단건을 모두 처리한다(목록은 그 List). 응답에 담는 것은 `templateUid`, `name`, `thumbnail`, `category`, `variants`다.

> 앞선 초안에서 "단건엔 스키마 포함 / 목록엔 제외"로 DTO를 둘로 나눴으나, 스키마가 서버 검증 전용으로 정리되면서 그 구분이 불필요해졌다.

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

### 응답 (`TemplateResponse`, 등록 직후 = 조회와 동일 형태)

등록 요청엔 `section_schema`가 들어오지만, **응답에는 담지 않는다**(서버 검증 전용).

```json
{
  "templateUid": "a1b2c3d4-....",
  "name": "클래식 화이트",
  "thumbnail": "https://cdn.smally/thumb/classic.png",
  "category": "클래식",
  "variants": { "color": ["white", "beige"], "font": ["serif", "sans"] }
}
```

## 5. 조회 응답

목록·단건 모두 `section_schema`를 **제외**한다(서버 검증 전용). 응답 DTO는 `TemplateResponse` 하나를 공유한다.

### 단건 — `GET /templates/{templateUid}`

형태는 4절의 `TemplateResponse`와 동일. 없으면 404.

### 목록 — `GET /templates?category=클래식&page=0&size=20`

`category`는 선택적 필터. 페이징 적용.

```json
{
  "content": [
    { "templateUid": "a1b2...", "name": "클래식 화이트", "thumbnail": "https://cdn.smally/thumb/classic.png", "category": "클래식", "variants": { "color": ["white", "beige"] } },
    { "templateUid": "e5f6...", "name": "클래식 베이지", "thumbnail": "https://cdn.smally/thumb/beige.png", "category": "클래식", "variants": { "color": ["beige"] } }
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

