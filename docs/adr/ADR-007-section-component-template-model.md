# ADR-007: 템플릿을 섹션(컴포넌트) 타입 조합 레시피로 재정의하고 스키마를 컴포넌트 레벨로 내린다

- 상태: 승인됨
- 작성일: 2026-07-15
- 작성자: jasmin
- 관련 ADR: ADR-002(부분 개정), ADR-006(대체함)

## 배경 (Context)

ADR-002는 템플릿마다 `section_schema`(JSON Schema)를 붙여 청첩장 데이터를 검증하기로, ADR-006은 그 옆에 `options_schema`를 붙여 선택지를 관리하기로 정했다. 두 결정 모두 스키마를 **템플릿 단위**에 두었다.

이 "통짜 템플릿" 모델은 다음이 걸린다.

- **중복**: 인사말·갤러리·계좌번호 같은 섹션은 어느 템플릿에나 반복 등장하는데, 그 데이터 계약을 템플릿마다 다시 쓴다.
- **조합 불가**: 템플릿이 고정 덩어리라 "이 섹션들을 이 순서로" 같은 조합을 데이터로 표현하지 못한다. 새 디자인 = 새 통짜 스키마.
- **고객 UX와 관리 유연성의 상충**: 모든 걸 세팅 가능하게 하면 고객이 복잡하고, 고정하면 관리자가 못 바꾼다.

시장 실측도 다른 방향을 가리켰다. 경쟁 서비스 snappost를 브라우저로 분석한 결과, 청첩장이 `<section data-section-type="hero|gallery|…" data-section-id="…">`의 스택이고, 서로 다른 템플릿이 **같은 섹션 타입**을 쓰되 **집합·순서·변형(variant)·테마**만 다르게 조합한다. 같은 타입 인스턴스 반복(gallery ×2)도 허용한다. 즉 "템플릿"은 섹션 타입 위에 얹은 **프리셋 레시피**다.

따라서 검증 단위와 조합 단위를 **템플릿 → 컴포넌트(섹션 타입)**로 내리는 재정의가 필요하다.

## 결정 (Decision)

1. **컴포넌트 종류(`ComponentType`)와 컴포넌트(`Component`)를 분리해 1급화한다.** `ComponentType`은 종류 카테고리(메인이미지·인사말·… 12종)로 **확장성을 위해 DB 테이블**로 관리한다(기존 `Category` 패턴). `Component`는 실제 UI 조각으로 자신의 **종류(→`ComponentType` FK)**, **프론트 연결 겸 외부 식별자 `componentUId`**(고유·불변), **데이터 계약 `data_schema`**(JSON Schema), **컴포넌트 옵션 `option_schema`**(JSON Schema)를 가진다. 컴포넌트 **코드**(프론트 React)는 여전히 배포가 필요하지만, 이 메타 행은 데이터로서 서버가 검증·관리한다.

   `componentUId`는 프론트의 어느 React 컴포넌트에 연결되는지를 가리키는 키(예: `"GalleryGrid"`)이자, 레시피·API가 컴포넌트를 지목하는 외부 식별자다. 하나의 필드가 두 역할을 겸한다.

2. **템플릿을 레시피로 재정의한다.** 템플릿 = `sections`(순서 있는 `[{ componentUId, 고정옵션값(제목 위치·크기 등), editable }]`) + `theme` + 메타. **같은 컴포넌트를 여러 템플릿이 공유**하되 고정옵션값으로 차별화한다. **배포 없이** 데이터로 등록한다. 템플릿 등록 검증은 "스키마 문법"이 아니라 **레시피 유효성**(참조 컴포넌트 존재)이 된다.

3. **스키마 위치를 컴포넌트 레벨로 내린다(ADR-002 부분 개정).** 청첩장 데이터 검증은 저장 시점에, **각 섹션 인스턴스가 참조하는 `Component`의 `data_schema`로** 수행한다. ADR-002의 핵심(JSON Schema + jsonb + 서버가 저장 시점에 검증)은 유지되고, 스키마가 붙는 **위치만** 템플릿 → 컴포넌트로 바뀐다. 공용 `SchemaValidator`(엔진)는 그대로 재사용한다.

4. **청첩장 데이터는 단일 jsonb를 유지한다.** ADR-002대로 정규화하지 않고, 섹션 인스턴스 id로 키잉한 하나의 `section_values` jsonb + `selected_options` jsonb로 저장한다.

5. **ADR-006을 대체하고 옵션을 두 스코프로 나눈다.** template-level `options_schema`는 폐기한다. **컴포넌트 옵션**(표시모드·타입·폰트사이즈·텍스트 위치 등)은 `Component.option_schema`에서 관리하고, **전역 옵션**(폰트·애니메이션·색상 프리셋 등)은 **마스터 테이블 `OptionDefinition`**에 정의해 템플릿 `theme`가 값을 골라 템플릿별로 관리한다.

데이터 모델(잠정):
- `component_types`: `name`(고유키, 12종)
- `components`: `component_type`(FK → component_types), `name`, `componentUId`(고유·불변, 프론트 연결 키 겸 외부 식별자), `data_schema`(jsonb), `option_schema`(jsonb)
- `option_definitions`: `key`(고유), `label`, `control_type`, `allowed_values`(jsonb), `default_value`(jsonb)
- `templates`: `template_uid`, `name`, `category`, `thumbnail`, **`sections`(jsonb, `[{componentUId, 고정옵션값, editable}]` 순서 리스트)**, **`theme`(jsonb, `{optionKey → 값}`)**
- `invitations`: `invitation_uid`, `user_id`, `template_uid`, `slug`, `status`, **`section_values`(jsonb, sectionId별)**, **`selected_options`(jsonb)**

## 고려한 대안 (Alternatives Considered)

### 대안 1: 종류(ComponentType)/컴포넌트(Component) 분리 1급화 — 채택
- 설명: 위 결정. 종류는 카테고리 테이블(ComponentType), 스키마·옵션·프론트연결은 컴포넌트(Component)에, 템플릿은 조합 레시피로.
- 장점: 스키마 중복 제거(컴포넌트당 1개), 무배포 템플릿 조합, 고객엔 프리셋·관리엔 유연성, 종류 확장은 데이터로. 시장 실측(snappost)과 일치.
- 단점: `Component` 엔티티 신설 + 섹션 렌더 시스템을 한 번 만드는 초기 개발 비용. 컴포넌트 코드 추가는 여전히 배포 필요.
- 채택 이유: 초기 비용은 일회성이고, 중복 제거·조합 유연성·검증 트러스트 경계를 동시에 얻는다.

### 대안 2: template-level 스키마 유지 + 컴포넌트 레벨 추가 공존 (ADR-006 유지)
- 설명: 방금 구현한 template-level `options_schema`를 두고 컴포넌트 모델을 위에 얹음.
- 장점: 기존 커밋을 안 건드림.
- 단점: 같은 관심사(검증·옵션)가 템플릿·컴포넌트 두 레벨에 공존해 혼란·기술부채. 무엇이 진실의 원천인지 모호.
- 채택하지 않은 이유: 두 축 공존은 유지보수 비용이 크고, 재정의의 목적(단순화)과 정면 충돌.

### 대안 3: 서버 검증 없이 불투명 blob 저장
- 설명: 서버는 `{sectionType, 순서, 데이터}`를 그대로 저장, 검증은 프론트 책임.
- 장점: 서버 최소·개발 빠름.
- 단점: 등록/저장 API가 공개(permitAll)이고 하객 열람도 공개라, 깨진 데이터·직접 API 호출을 못 막는다. ARCHITECTURE §2의 "서버가 입력을 검증한다" 트러스트 경계를 포기.
- 채택하지 않은 이유: 깨진 청첩장이 하객에게 노출되는 것을 막는다는 제품 원칙과 충돌.

## 결정 이유 (Rationale)

- **중복 제거·재사용**: 스키마가 컴포넌트당 1개로 줄어 템플릿마다 다시 쓰던 중복이 사라진다.
- **운영 유연성**: 템플릿(레시피) 등록이 순수 데이터 작업 → 배포 불필요. ADR-002 정신을 더 강하게 실현한다(컴포넌트 코드 추가만 배포).
- **책임 일관성**: 검증을 여전히 서버가, 저장 시점에, JSON Schema로 수행(ADR-002 유지). 스키마 위치만 이동.
- **시장 검증**: snappost가 동일 구조를 실서비스로 운영 — 실현 가능성 확인.
- **엔진 재사용**: `SchemaValidator`를 그대로 쓰므로 검증 표면이 하나로 유지된다.

## 영향 (Consequences)

### 긍정적 영향
- 새 템플릿(레시피)을 배포 없이 데이터로 등록.
- 스키마 중복 제거, 컴포넌트 단위 재사용.
- 고객에겐 단순한 프리셋, 관리자에겐 조합 유연성.

### 부정적 영향 / 트레이드오프
- `Component` 엔티티 신설 + 섹션 렌더 시스템(프론트) 초기 구축 비용.
- 새 컴포넌트 **코드**는 여전히 배포 필요(무배포는 조합·테마·정적 SVG 블록까지).
- ADR-006 기반으로 이미 커밋된 template-level `options_schema`(커밋 `b7c3d2e`)를 컴포넌트 레벨로 이행해야 한다(아래).
- 컴포넌트/템플릿 버전 관리 부재는 ADR-002와 동일하게 잠재 리스크로 남김(후속).

### 이행 (커밋 b7c3d2e → 새 모델)
- `SchemaValidator`(+테스트): **유지** — 새 모델의 핵심 엔진.
- `Template.options_schema` 필드: **폐기·재배치** → 컴포넌트 `option_schema` + 템플릿 `theme`.
- `VariantResponse.optionsSchema` 노출: 컴포넌트/템플릿 조회 응답으로 재설계.
- `createTemplate` 스키마 메타검증: 컴포넌트 등록 메타검증으로 이동, 템플릿 등록은 레시피 유효성 검증으로 대체.
- `INVALID_TEMPLATE_OPTIONS_SCHEMA`: 컴포넌트 스키마 메타검증 에러코드로 대체/확장.
- 되돌릴 미커밋 파일은 없으며(b7c3d2e는 커밋됨), 위 이행은 신규 후속 커밋으로 진행.

**계획 A(Component 파운데이션, 미커밋 작업 트리) 재설계**: 이 세션에서 만든 `Component`는 종류와 컴포넌트를 한 엔티티에 뭉쳐 놨으므로 재설계한다(미커밋이라 마이그레이션 불요).
- `Component.type`(String) → **`ComponentType` 테이블 신설** + `Component.componentType` FK로 분리.
- `Component.variants`(string[]) → **제거**(디자인 차별은 별도 `Component` 행 + 템플릿 고정옵션값으로).
- `Component`에 **`componentUId` 추가**(프론트 연결 키 겸 외부 식별자, 고유·불변). `data_schema`·`option_schema`는 유지.
- **`OptionDefinition`(전역 옵션 마스터) 신설** — 전역 옵션(폰트·애니메이션·색상 프리셋)을 관리자 데이터로 관리, 템플릿 `theme`가 참조.

### 후속 조치 필요 사항
- **본 ADR 승인 시 ADR-006 상태를 "대체됨 (ADR-007로 대체)"로 변경.**
- 편집기/미리보기 구현(전용 렌더러 라우트 + iframe, snappost 패턴 참고) — 별도 프론트 레포.
- 실제 저장 endpoint·payload 형태 확인 후 `section_values`/`selected_options` 형태 확정(현재 에디터 로그인 미확인).
- `theme`·컴포넌트 옵션 토큰 표준화, 사용자 순서/on·off 오버라이드 정책.
- 컴포넌트/템플릿 버전 관리 정책(필요 시 별도 ADR).

## 참고 자료 (References)

- [섹션/컴포넌트 기반 템플릿 모델 설계](../superpowers/specs/2026-07-15-section-component-model-design.md) — 본 ADR의 상세 설계.
- [ADR-002](./ADR-002-template-schema-jsonschema-jsonb.md) — 부분 개정: JSON Schema+jsonb 유지, 스키마 위치만 컴포넌트로 이동.
- [ADR-006](./ADR-006-template-options-schema.md) — 본 ADR이 대체함.
- [docs/ARCHITECTURE.md](../ARCHITECTURE.md) — 데이터/렌더러 분리, 서버 검증 책임.
- snappost.co 브라우저 실측(2026-07-15): `data-section-type` 섹션 스택, preview-frame iframe, SVG 장식 애셋.
