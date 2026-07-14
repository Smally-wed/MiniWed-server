# ADR-006: 템플릿 옵션은 두 번째 JSON Schema(options_schema)로 관리하고 프론트에 노출한다

- 상태: 승인됨
- 작성일: 2026-07-15
- 작성자: jasmin
- 관련 ADR: ADR-002

## 배경 (Context)

ADR-002에서 템플릿의 **콘텐츠 데이터**(인사말 text, 갤러리 photos 등)를 `section_schema`(JSON Schema)로 검증하기로 정했다. 그 `section_schema`는 서버 검증 전용이며 프론트에 내려주지 않는다(프론트의 손수 만든 React 컴포넌트가 필드 구조를 이미 안다).

이제 서비스에 **사용자가 고르는 선택지**를 추가해야 한다.

- 디자인: 폰트 크기(작게/보통/크게), 색상 프리셋, 스크롤 애니메이션 등
- 섹션 구성: 순서 변경, 끄고 켜기, 섹션별 세부 옵션(참석여부 표시모드, 갤러리 타입 등)
- 일부는 "템플릿별 고정"(위치 표시방식·포토드롭·계좌번호·방명록)이라 사용자가 못 바꾼다.

이 옵션들은 콘텐츠 데이터와 성질이 다르다. "자유 입력값이 유효한가"가 아니라 **"허용된 보기 중 하나를 골랐나"**를 다룬다. 또한 콘텐츠 데이터와 달리, 사용자에게 **선택 UI로 제시**해야 하므로 프론트가 선택지 목록을 알아야 한다.

따라서 (1) 옵션을 무엇으로 정의·검증할지, (2) 옵션 정의를 프론트에 노출할지를 결정한다.

## 결정 (Decision)

1. **옵션은 `section_schema`와 별개의 두 번째 JSON Schema `options_schema`로 정의한다.** 템플릿 메타데이터에 `options_schema`(jsonb)를 함께 저장한다. 옵션 값의 제약은 JSON Schema의 `enum`/`type`으로, 기본값은 `default`, 표시명은 `title`로 표현한다.

2. **선택값 검증은 ADR-002가 도입한 `SchemaValidator`(검증 엔진)를 그대로 재사용한다.** 템플릿 등록 시 `options_schema` 자체를 메타검증하고, 청첩장 저장 시 사용자의 `selected_options`를 `options_schema`로 데이터검증한다. 새 검증기를 만들지 않는다.

3. **`options_schema`는 프론트에 노출한다.** `section_schema`와 달리, 에디터가 선택 UI(폰트크기 셀렉트, 섹션 on·off·순서 목록)를 서버 응답으로부터 그려야 하므로 조회 응답에 포함한다. `section_schema`의 비노출 원칙은 유지한다.

4. **렌더링·정책 메타데이터는 `x-` 커스텀 키워드로 얹는다.** 표준 JSON Schema 검증기는 모르는 키워드를 무시하므로 검증에 무해하다. 최소 어휘: `x-editable`(false=템플릿 고정), `x-scope`(global/section), `x-control`(select/toggle/order/preset), `x-premium`.

5. **"템플릿 고정 옵션" 강제만 얇은 후처리로 둔다.** JSON Schema로는 "이 키는 보내면 안 됨"을 자연스럽게 표현하기 어려워, `selected_options`에 `x-editable:false`인 키가 오면 거절하는 체크만 코드로 처리한다.

데이터 모델(잠정):
- `templates`: 기존 컬럼 + **`options_schema` (jsonb, JSON Schema 문서, 프론트 노출)**
- `invitations`: 기존 컬럼 + **`selected_options` (jsonb, 사용자가 고른 값)** — 실제 연동은 후속

## 고려한 대안 (Alternatives Considered)

### 대안 1: 옵션을 `section_schema`에 흡수 (단일 스키마)
- 설명: 별도 스키마 없이 옵션 enum을 기존 `section_schema` 안에 함께 넣는다.
- 장점: 필드·검증 표면이 하나로 유지된다.
- 단점: 콘텐츠 데이터 규칙과 선택지가 뒤섞인다. "템플릿 고정 vs 사용자 편집" 구분을 표현하기 어렵다. 무엇보다 옵션은 프론트에 노출해야 하는데 `section_schema`는 비노출 원칙이라 충돌한다(스키마를 반으로 잘라 일부만 노출하는 예외가 생긴다).
- 채택하지 않은 이유: 성질이 다른 두 축을 한 스키마에 묶으면 노출 정책과 편집 정책이 엉킨다.

### 대안 2: 전용 매니페스트 (목적특화 커스텀 포맷)
- 설명: 옵션을 JSON Schema가 아니라 목적특화 구조(옵션 정의 리스트: key·kind·allowedValues·default·editable·scope)로 저장한다.
- 장점: "이건 사용자가 고르는 메뉴다"라는 의도에 가장 솔직하고, 프론트 렌더가 깔끔하다.
- 단점: 선택값 검증을 위해 별도 경로(매니페스트→enum 스키마 파생, 또는 전용 검증기)가 생긴다. ADR-002의 "JSON Schema 한 우산 + 라이브러리 위임" 일관성에서 벗어난다.
- 채택하지 않은 이유: 검증 표면이 둘로 늘고, 이미 JSON Schema에 투자한 자산을 재사용하지 못한다.

### 대안 3: 섹션별 옵션을 코드로 고정 (섹션별 DTO + Bean Validation)
- 설명: 참석여부·갤러리 등 섹션 옵션을 자바 record/enum으로 정의하고 `@Valid`로 검증.
- 장점: 타입 안전, IDE 지원.
- 단점: 새 옵션·새 보기값 추가 시 자바 코드 수정과 재배포가 필요하다. "관리자가 DB로 템플릿을 등록"하는 방향과 충돌한다(ADR-002 대안 2와 동일한 문제).
- 채택하지 않은 이유: 옵션 카탈로그가 코드에 강결합되어 운영 유연성이 떨어진다.

## 결정 이유 (Rationale)

- **일관성·재사용**: ADR-002가 이미 JSON Schema + 라이브러리 위임으로 갔다. 옵션도 같은 엔진·같은 사고방식으로 붙여 검증 표면을 하나로 유지한다. 새 검증기 0개.
- **운영 유연성**: 옵션 카탈로그(참석여부 4모드, 갤러리 타입 등)가 코드가 아니라 템플릿 등록 데이터(`options_schema`)로 들어오므로, 새 옵션 추가에 재배포가 필요 없다. ADR-002 정신을 유지한다.
- **책임 분리**: 콘텐츠 데이터(`section_schema`, 비노출)와 선택지(`options_schema`, 노출)를 분리해 노출·편집 정책을 각 축에서 독립적으로 다룬다.
- **비용 인지**: JSON Schema는 원래 검증 언어라 렌더 의도를 표현하려면 `x-` 키워드가 얹혀 다소 장황하다. 이 비용은 검증 엔진 재사용과 일관성을 위해 감수한다.

## 영향 (Consequences)

### 긍정적 영향
- 옵션 검증에 기존 `SchemaValidator`를 그대로 재사용한다(추가 구현 없음).
- 새 옵션·새 보기값 추가가 코드 배포 없이 데이터 작업으로 가능하다.
- 콘텐츠 데이터와 선택지의 노출 정책이 각각 독립적으로 관리된다.

### 부정적 영향 / 트레이드오프
- **JSON Schema에 렌더 메타가 얹혀 장황해진다.** `x-` 커스텀 키워드 규약을 프론트와 공유·유지해야 한다.
- **"템플릿 고정 옵션" 강제가 JSON Schema만으로 안 된다.** `x-editable:false` 후처리 체크를 코드로 한 겹 둔다.
- **`options_schema`가 프론트에 노출**되므로, 스키마에 민감정보를 넣지 않도록 주의해야 한다(선택지 정의 전용).
- ADR-002와 동일하게 **옵션 스키마 버전 관리 부재** — 운영 중 옵션 스키마를 바꾸면 기존 청첩장의 `selected_options`와 어긋날 수 있다. → 후속 과제.

### 후속 조치 필요 사항
- `Template.options_schema` 필드 및 `TemplateResponse` 노출 반영.
- 청첩장 `selected_options` 검증의 invitation 도메인 연동(`section_values`와 함께).
- `x-editable:false` 후처리 체크 구현.
- `ErrorCode`에 옵션 스키마 메타검증 실패 코드(예: `INVALID_TEMPLATE_OPTIONS_SCHEMA`) 추가.
- 옵션 스키마 버전 관리 정책(필요 시 별도 ADR).

## 참고 자료 (References)

- [ADR-002](./ADR-002-template-schema-jsonschema-jsonb.md) — 섹션 스키마 JSON Schema + jsonb 결정. 본 ADR은 그 검증 엔진을 재사용한다.
- [템플릿 옵션 관리 설계](../superpowers/specs/2026-07-15-template-options-design.md) — 본 ADR의 상세 설계.
- [docs/ARCHITECTURE.md](../ARCHITECTURE.md) — 데이터/렌더러 분리, 서버 검증 책임.
