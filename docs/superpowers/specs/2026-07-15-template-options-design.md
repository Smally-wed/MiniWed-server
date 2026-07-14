# 템플릿 옵션 관리 설계 (options_schema + selected_options)

- 작성일: 2026-07-15
- 작성자: jasmin
- 관련 문서: [ADR-002](../../adr/ADR-002-template-schema-jsonschema-jsonb.md), [템플릿 기능 설계](./2026-07-14-template-features-design.md)

## 1. 목적과 범위

지금까지 템플릿은 **콘텐츠 데이터의 형태·필수·길이**를 `section_schema`(JSON Schema)로 검증했다.
이번 설계는 그 옆에 **"사용자가 고르는 선택지"**를 관리하는 축을 추가한다.

폰트 크기, 섹션 순서/on·off, 참석여부 표시모드, 갤러리 타입, 색상 프리셋처럼
사용자가 **정해진 보기 중 하나를 고르는** 값들을 저장·검증한다.

### 핵심 구분: 옵션은 `section_schema`와 다른 축이다

| 축 | 검증 대상 | 예시 | 위치 |
|---|---|---|---|
| **콘텐츠 데이터** | 사용자가 입력한 *값의 형태·필수·길이* | 인사말 text(≤500), 사진 URL 배열 | `section_schema` |
| **옵션(선택지)** | 사용자가 *허용된 보기 중 무엇을 골랐나* | 폰트크기=크게, 참석여부=버튼, 섹션순서 | `options_schema` (신규) |

옵션은 "자유 입력값이 유효한가"가 아니라 **"허용된 보기 중 하나인가"**를 다룬다.

### 옵션의 3층 (누가 정하나)

1. **앱 전역 고정** — 폰트 제공(글로벌), 음악 라이브러리. 템플릿마다 다르지 않음 → 서버가 템플릿별로 저장하지 않는다. (범위 밖)
2. **템플릿이 정하는 고정값** — 위치 표시방식·포토드롭·계좌번호·방명록 등 "템플릿별 고정". 사용자가 못 바꿈 → `options_schema`에 `x-editable:false`로 표현.
3. **사용자가 고르는 선택** — 폰트크기, 섹션 순서/on·off, 참석여부 표시모드, 갤러리 타입, 색상 프리셋 등. → 저장·검증 대상.

### 범위 밖 (이번 설계 제외)

- **애니메이션/필터/음악/스크롤효과/폰트 종류의 실제 렌더링 해석** — 서버는 enum 값(`"blur"`, `"large"`)만 통과·저장하고 의미는 해석하지 않는다. 실제 렌더는 프론트 몫.
- **오프닝 애니메이션** — 작성자 원안에서도 MVP 제외.
- **프리셋 프리미엄 과금 로직** — 표시 플래그(`x-premium`)만 두고 과금은 별도 과제.
- **청첩장 `selected_options` 검증의 실제 연동** — 이번엔 공용 검증기 재사용 지점만 정의하고, invitation 도메인 연동은 후속(§7).
- **옵션 스키마 버전 관리** — ADR-002 4항과 동일하게 현 단계 미도입.

## 2. 대칭 구조

기존 검증 대칭에 옵션 축을 한 칸씩 늘린다.

```
템플릿 쪽:   section_schema (데이터 규칙)   ↔   options_schema  (선택지 정의)
청첩장 쪽:   section_values (입력 데이터)   ↔   selected_options (사용자가 고른 값)
```

"고른 값이 허용 보기 안에 있나"는 JSON Schema의 `enum`으로 표현되므로,
**이미 만든 `SchemaValidator`(검증 엔진)를 그대로 재사용**한다. 새 검증기는 만들지 않는다.

## 3. 데이터 모델

### Template (기존 + 신규 필드)

| 필드 | 형태 | 설명 | 프론트 노출 |
|---|---|---|---|
| `section_schema` | jsonb (JSON Schema) | 콘텐츠 데이터 검증 규칙 (기존) | ✕ (서버 검증 전용) |
| `options_schema` | jsonb (JSON Schema) | **선택지 정의 (신규)** | ○ (에디터가 선택 UI 렌더) |

- `options_schema`는 `section_schema`와 달리 **조회 응답에 포함**한다. 에디터가 "폰트크기: 작게/보통/크게" 같은 선택 UI와 섹션 on·off·순서 목록을 서버 응답으로부터 그려야 하기 때문.
- `section_schema` 비노출 원칙은 그대로 유지한다.

### Invitation (신규 필드 — 실제 연동은 후속)

| 필드 | 형태 | 설명 |
|---|---|---|
| `section_values` | jsonb | 사용자 입력 콘텐츠 (ADR-002 예정) |
| `selected_options` | jsonb | **사용자가 고른 옵션 값 (신규)** |

## 4. `options_schema` 모양 + `x-` 키워드 규약

### 사용자가 저장할 `selected_options` 예시

```json
{
  "design":  { "fontSize": "large", "scrollAnimation": "blur", "colorPreset": 2 },
  "sections": {
    "order":    ["main","greeting","gallery","attendance","date","location","account","guestbook"],
    "disabled": ["countdown","video"],
    "config":   { "attendance": { "displayMode": "button" }, "gallery": { "type": "grid" } }
  }
}
```

- `design`: 전역 옵션(폰트크기, 스크롤 애니메이션, 색상 프리셋 등).
- `sections.order`: 섹션 노출 순서 (순서 변경).
- `sections.disabled`: 끈 섹션 목록 (opt-out; 토글 가능한 섹션은 기본 on, 여기 넣으면 off).
- `sections.config`: 섹션별 세부 옵션 (참석여부 표시모드, 갤러리 타입 등).

### 이를 검증·렌더하는 `options_schema`(템플릿) 발췌

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "design": { "type": "object", "properties": {
      "fontSize":    { "type":"string", "enum":["small","normal","large"], "default":"normal",
                       "title":"폰트 크기", "x-scope":"global", "x-control":"select", "x-editable":true },
      "colorPreset": { "type":"integer", "enum":[0,1,2,3], "default":0,
                       "x-control":"preset", "x-premium":true }
    }},
    "sections": { "type":"object", "properties": {
      "order":    { "type":"array",
                    "items":{"enum":["main","greeting","gallery","attendance","date","location","account","guestbook"]},
                    "x-control":"order" },
      "disabled": { "type":"array", "items":{"enum":["countdown","video","gallery"]} },
      "config":   { "type":"object", "properties": {
        "attendance": { "type":"object", "properties": {
          "displayMode": { "enum":["inline","button","popup","sticky"], "default":"inline", "x-control":"select" }
        }}
      }}
    }}
  }
}
```

### `x-` 키워드 규약 (최소 어휘)

표준 JSON Schema 검증기는 모르는 키워드를 무시한다. 따라서 `x-` 키워드는 **검증에 무해**하고,
**프론트 렌더 + 서버의 얇은 후처리 체크에만** 의미가 있다.

| 키워드 | 표준 여부 | 의미 |
|---|---|---|
| `title` | 표준 | 표시명 |
| `default` | 표준 | 기본값 (고정 옵션은 이 값이 곧 렌더값) |
| `x-editable` | 커스텀 | `false` = 템플릿 고정, 사용자 변경 불가 (기본 `true`) |
| `x-scope` | 커스텀 | `global` / `section` — 에디터 그룹핑 힌트 |
| `x-control` | 커스텀 | `select` / `toggle` / `order` / `preset` — UI 종류 |
| `x-premium` | 커스텀 | 프리셋 등 프리미엄 표시 플래그 |

### 요구 케이스 매핑

| 원안 요구 | 표현 방식 |
|---|---|
| 폰트 크기(작게/보통/크게), 글로벌 | `design.fontSize` enum + `x-scope:"global"` |
| 스크롤 애니메이션(슬라이드/블러) | `design.scrollAnimation` enum |
| 색상 프리셋 3~4개 (프리미엄) | `design.colorPreset` enum + `x-premium:true` |
| 섹션 순서 변경 | `sections.order` (array + `x-control:"order"`) |
| 섹션 끄고 켜기 | `sections.disabled` (토글 가능 섹션만 items enum에 포함) |
| 참석여부 표시모드(인라인/버튼/팝업/스티키) | `sections.config.attendance.displayMode` enum |
| 갤러리 타입, 날짜 스타일, 카운트다운 스타일 | `sections.config.<section>.<key>` enum |
| 위치 표시방식·포토드롭·계좌번호·방명록 = **템플릿별 고정** | 해당 옵션에 `x-editable:false` |
| 끌 수 없는(고정) 섹션 | `sections.disabled`의 items enum에서 그 섹션 키를 제외 → 애초에 못 끔 |

## 5. 검증 흐름 (엔진 재사용)

```
템플릿 등록:
  SchemaValidator.validateSchema(optionsSchema)
    - section_schema와 동일한 메타검증 (draft 2020-12 문법 검증)
    - 실패 → 400, INVALID_TEMPLATE_OPTIONS_SCHEMA + 위반 이유

청첩장 저장(후속 연동):
  SchemaValidator.validate(selectedOptions, template.optionsSchema)
    - 기존 데이터검증 엔진 그대로 (enum 위반, 타입 위반 검출)
  + 얇은 후처리:
    - selected_options에 x-editable:false 인 키가 있으면 거절 (고정 옵션 변조 방지)
```

- **새 검증기는 0개.** 기존 `SchemaValidator`가 메타검증·데이터검증 둘 다 처리한다.
- `x-editable` 강제만 작은 후처리 한 겹으로 둔다. JSON Schema로는 "이 키는 보내면 안 됨"을 자연스럽게 표현하기 어려워, 이 부분만 코드로 처리한다.

## 6. 영향받는 구성 요소

```
domain/template
├── entity/Template.java              (수정) options_schema 필드 추가 (jsonb)
├── dto/TemplateCreateRequest.java    (수정) optionsSchema 수신
├── dto/TemplateResponse.java         (수정) optionsSchema 노출 (section_schema는 계속 비노출)
└── service/TemplateServiceImpl.java  (수정) 등록 시 optionsSchema 메타검증 호출

공용
└── SchemaValidator                   (재사용) 추가 구현 없음

domain/invitation (후속)
├── entity/Invitation                 selected_options 필드 (jsonb)
└── (저장 서비스)                      selected_options 검증 + x-editable 후처리

core/exception
└── ErrorCode                         (수정) INVALID_TEMPLATE_OPTIONS_SCHEMA 등 추가
```

## 7. 후속 과제

- 청첩장 `selected_options` 검증의 invitation 도메인 연동 (`section_values`와 함께).
- 색상 프리셋 프리미엄 과금 로직.
- 옵션 스키마 버전 관리 정책 (ADR-002 4항과 연동, 필요 시 별도 ADR).
- `x-control` 어휘 확장 (에디터 UI 종류가 늘어날 때).

## 8. ADR 연계

엔티티 필드 추가 + 새 검증 표면(옵션 축)은 **구조 변경**이다.
ADR-002(섹션 스키마 JSON Schema 결정)를 잇는 짧은 ADR로 "옵션도 JSON Schema로,
검증 엔진 재사용, options_schema는 프론트에 노출" 결정을 기록한다.
