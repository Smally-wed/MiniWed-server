# 섹션/컴포넌트 기반 템플릿 모델 설계 (ADR-002·006 개정)

- 작성일: 2026-07-15
- 작성자: jasmin
- 관련 문서: [ADR-002](../../adr/ADR-002-template-schema-jsonschema-jsonb.md), [ADR-006](../../adr/ADR-006-template-options-schema.md), [템플릿 옵션 설계](./2026-07-15-template-options-design.md)
- 상태: 초안 (검토 대기)

## 1. 목적과 배경

지금까지 템플릿은 **"한 덩어리"**였다 — 템플릿 하나에 `section_schema`(데이터 규칙) + `options_schema`(선택지)가 통째로 붙었다(ADR-002, ADR-006). 이 모델은 두 가지가 걸린다.

- 템플릿마다 스키마를 새로 쓰고 관리해야 한다(중복).
- 새 템플릿을 만들 때 조합의 자유도가 없다(고정 통짜).

**전환**: 청첩장을 구성하는 섹션 단위를 **컴포넌트**로 분리해 DB에 저장하고, 템플릿은 **"어떤 컴포넌트들이 어떤 순서로 나열되는가"**만 기록하는 레시피로 재정의한다. 기존엔 템플릿 자체가 프론트 코드였고 DB의 "템플릿"은 사실 데이터 검증용이었을 뿐이라, 새 템플릿마다 개발자가 옵션·코드를 다 세팅해야 했다. 컴포넌트를 분리해두면 **조합만으로** 다양한 템플릿을 무배포로 찍어낼 수 있다.

컴포넌트의 **종류(메인이미지·인사말·참석여부·갤러리·날짜시간·영상삽입·카운트다운·위치·포토드롭·계좌번호·안내사항·방명록)**는 확장성을 위해 별도 **카테고리 테이블(`ComponentType`)**로 관리하고, 각 컴포넌트는 그 종류를 참조한다.

### 제품 취지 (왜 이렇게)

- **사용자에겐 자유도를 일부러 낮춘다.** 과한 자유는 선택 장애를 부르고 사용감을 흐린다. 조합의 자유는 **관리자**가 쥐고 다양한 템플릿을 미리 만들어 제공한다.
- 완전 커스텀을 원하는 고객을 위해 **완전 자유 템플릿(유료)**을 별도로 제공한다.
- **차별성은 컴포넌트 단위에서 나온다.** 같은 컴포넌트를 여러 템플릿이 공유하되, 템플릿 등록 시 제목 위치·크기 등 컴포넌트 옵션을 다르게 줘 차별화한다. 메인 페이지처럼 템플릿마다 특정 컴포넌트를 지정(고정)한다.

### 시장 실측 (snappost.co)

경쟁 서비스 snappost를 브라우저로 직접 분석한 결과, 정확히 이 구조를 쓴다.

- 청첩장 렌더 DOM이 `<section data-section-type="..." data-section-id="...">`의 스택. 타입 예: `hero, greeting, gallery, datetime, countdown, timeline, location, infoGuide, photoDrop, account, rsvp, guestbook, coupleProfile, wishlist, contact, outro, videoEmbed, guestList`.
- 서로 다른 템플릿(pure-simple vs classic-arch)이 **같은 `hero` 타입**을 쓰되 섹션 **집합·순서·변형·테마**가 다름. 즉 "메인 페이지가 다른 것"은 같은 컴포넌트 타입의 **variant + theme** 차이.
- `gallery`가 한 템플릿에서 2번 등장 → **같은 타입의 인스턴스 반복** 허용.
- 편집기 미리보기는 전용 렌더러 라우트(`/ko/preview-frame?key=...`)를 **iframe**에 띄우는 방식(스타일 격리 + WYSIWYG). 저장 모델과 무관한 미리보기 기법.
- SVG 장식(`/deco/text/*.svg` + 마스크 애니메이션)을 애셋으로 스왑 → 무배포 장식.

## 2. 핵심 결정

1. **컴포넌트 종류(`ComponentType`)와 컴포넌트(`Component`)를 분리해 1급화한다.**
   - `ComponentType`: 종류 카테고리(메인이미지·인사말·… 12종). **확장성을 위해 DB 테이블**로 관리(기존 `Category`와 같은 패턴).
   - `Component`: 실제 UI 조각. 자신의 **종류(→`ComponentType`)**, **프론트 연결(`frontendBinding`: 어느 React 컴포넌트에 매핑되는지)**, **데이터 계약(`data_schema`)**, **컴포넌트 옵션(`option_schema`: 표시모드·타입·폰트사이즈·텍스트 위치 등)**을 가진다.
   - 새 컴포넌트 **코드**(프론트 React)는 배포가 필요하지만, 이 메타 행은 DB에 저장되어 서버가 검증·관리한다.

2. **템플릿 = 레시피.** 템플릿은 `[{ componentId, 순서, 고정옵션값(제목 위치·크기 등), 편집가능여부 }, …]` + `theme`(전역 옵션 선택값) + 메타(name/category/thumbnail). **같은 컴포넌트를 여러 템플릿이 공유**하되 고정옵션값으로 차별화한다. **배포 없이** 데이터로 등록한다.

3. **옵션은 두 스코프로 나눈다.**
   - **컴포넌트 옵션**(표시모드·타입·폰트사이즈·텍스트 위치 등): `Component.option_schema`에서 **컴포넌트 자체가 관리**.
   - **전역 옵션**(폰트·애니메이션·색상 프리셋 등): **마스터 옵션 테이블 `OptionDefinition`**에 정의하고, 템플릿의 `theme`가 그 값을 골라 **템플릿별로 관리**.

4. **청첩장 데이터는 단일 jsonb.** ADR-002 원칙 유지 — 정규화 테이블로 쪼개지 않는다. 섹션 인스턴스 id로 키잉한 하나의 jsonb.

5. **검증은 컴포넌트 `data_schema`로, 저장 시점에.** 청첩장 저장 시 각 섹션 인스턴스마다 참조 `Component`의 `data_schema`로 값을 검증한다. **기존 `SchemaValidator`(공용 엔진)를 그대로 재사용**한다. 컴포넌트 등록 시 `data_schema`·`option_schema` 자체를 메타검증한다. 전역 옵션(theme)은 `OptionDefinition` 카탈로그로 검증한다.

6. **ADR 전략**: 신규 **ADR-007**이 **ADR-006을 대체(superseded)**하고 **ADR-002를 부분 개정**한다. ADR-002의 핵심(JSON Schema + jsonb + 서버 검증)은 **유지**되고, 바뀌는 것은 스키마의 **위치**뿐이다(템플릿당 → 컴포넌트 타입당).

## 3. 데이터 모델

```
ComponentType (종류 카테고리, 확장용 DB 테이블)
  id, name(고유: "hero"·"gallery"… 12종)      // 종류 관리. 새 종류 = 행 추가. (기존 Category 패턴)

Component (실제 UI 조각, 마스터)
  id, name
  componentType (→ ComponentType)            // 어떤 종류인지
  frontend_binding (string)                  // 프론트 어느 React 컴포넌트에 연결되는지 (키/경로)
  data_schema   (jsonb, JSON Schema)         // 이 컴포넌트가 받는 고객 데이터 계약 (검증용)
  option_schema (jsonb, JSON Schema)         // 컴포넌트 자체 옵션(표시모드·타입·폰트사이즈·텍스트 위치…)

OptionDefinition (전역 옵션 마스터, 템플릿별 관리)
  key, label, controlType, allowed_values(jsonb), default_value(jsonb)   // 폰트·애니메이션·색상 프리셋…

Template (레시피, 무배포 등록)
  templateUid, name, category, thumbnail
  sections (jsonb)   // [{ componentId, 고정옵션값(제목 위치·크기…), editable(사용자 편집 허용 키) }, … 순서]
  theme    (jsonb)   // { OptionDefinition.key → 선택값 }

Invitation (고객 작성)
  invitationUid, userId, templateUid, slug, status
  section_values   (jsonb)   // { "<sectionId>": { …고객 입력값… }, … }
  selected_options (jsonb)   // 편집 가능한 옵션에서 고른 값 + (허용 시)순서/on·off 오버라이드
```

- **ComponentType vs Component**: 종류(카테고리)는 `ComponentType`, 실제 조각은 `Component`. 같은 종류에 여러 `Component`가 있을 수 있고(디자인이 다른 hero 여러 개), 템플릿은 그중 특정 `Component`를 골라 고정한다.
- **차별성**: 같은 `Component`를 여러 템플릿이 공유하되, 템플릿 등록 시 `sections[].고정옵션값`(제목 위치·크기 등)으로 차별화.
- **theme**: 전역 옵션(폰트·애니메이션·색상 프리셋)은 `OptionDefinition` 카탈로그에 정의하고 템플릿 `theme`가 값을 고른다.
- **section instance**: 같은 컴포넌트를 순서 리스트에서 여러 번 쓸 수 있고, 각 인스턴스는 고유 sectionId를 가진다.

## 4. 검증 흐름 (엔진 재사용)

```
컴포넌트 등록:
  SchemaValidator.validateSchema(component.dataSchema)     // 메타검증
  SchemaValidator.validateSchema(component.optionSchema)   // 메타검증

템플릿 등록:
  각 sections[].componentId 가 존재하는 Component인지 검증 (레시피 유효성)
  theme 의 각 key 가 OptionDefinition 카탈로그에 있고 값이 allowed_values에 드는지 검증

청첩장 저장:
  for each 섹션 인스턴스 in 템플릿.sections:
    schema = Component(sections[i].componentId).data_schema
    SchemaValidator.validateData(schema, section_values[sectionId])   // 데이터검증
  옵션: 각 컴포넌트 option_schema로 selected_options 검증 (+ x-editable 후처리)
```

- 새 검증기 0개. `SchemaValidator`가 메타·데이터검증 둘 다 처리(현행과 동일).
- 스키마가 컴포넌트당 1개 → 템플릿마다 다시 쓰던 중복이 사라진다.

## 5. 편집기 / 미리보기 (참고, 후속 구현)

- 미리보기: 전용 렌더러 라우트를 **iframe**에 띄우고 초안 데이터를 전달(snappost는 keyed 채널, 거의 확실히 postMessage). 스타일 격리 + WYSIWYG. **서버 저장 모델과 무관**.
- 고객 UX: 프리셋(레시피)이라 대부분 이미지·텍스트만 입력. 편집 가능 옵션만 노출(설정 과부하 방지). 별도 "자유 템플릿"(모든 옵션 개방) 1종 제공 검토.
- 무배포 장식: "이미지/SVG 블록" 컴포넌트로 정적 시각 요소는 배포 없이 추가(snappost `/deco/text/*.svg`).

> **확인 예정(에디터 로그인 필요)**: 실제 저장 엔드포인트·payload 형태. 현재 Keychain 권한 이슈로 미확인. 확보되면 §3 `section_values`/`selected_options` 형태를 대조·보정한다.

## 6. 기존 커밋(b7c3d2e)에서의 이행

`b7c3d2e`(템플릿 optionSchema 컬럼 추가)에 대해:

| 요소 | 새 모델에서 |
|---|---|
| `SchemaValidator` (+테스트) | **유지** — 새 모델의 핵심 엔진 그대로 |
| `Template.options_schema` 필드 | **컴포넌트 레벨로 재배치** — 컴포넌트 `option_schema` + 템플릿 `theme`로 분해 |
| `VariantResponse.optionsSchema` 노출 | 컴포넌트/템플릿 조회 응답으로 재설계 |
| `createTemplate` 메타검증 | 컴포넌트 등록 메타검증으로 이동, 템플릿 등록은 "레시피 유효성"(참조 컴포넌트 존재)으로 대체 |
| `INVALID_TEMPLATE_OPTIONS_SCHEMA` | 컴포넌트 스키마 메타검증 에러코드로 대체/확장 |

되돌릴 미커밋 파일은 없다(b7c3d2e는 커밋됨). 위 재배치는 **후속 커밋(신규 구현)**으로 진행한다.

### 계획 A(Component 파운데이션, 미커밋)에서의 재설계

이 세션에서 만든 `Component`(작업 트리, 미커밋)는 **종류와 컴포넌트를 한 엔티티에 뭉쳐** 놨으므로 이 모델에 맞게 재설계한다(아직 미커밋이라 마이그레이션 없이 리셰이프 가능):

| 계획 A의 `Component` | 새 모델에서 |
|---|---|
| `type` (String) | **`ComponentType` 테이블 신설** + `Component.componentType` FK 참조로 분리 |
| `data_schema`, `option_schema` | `Component`에 유지 |
| `variants` (string[]) | **제거** — 디자인 차별은 별도 `Component` 행 + 템플릿 등록 시 고정옵션값으로 |
| (없음) | **`frontend_binding` 추가** |

## 7. 범위 밖 (후속)

- 편집기 프론트 구현(별도 Next.js 레포).
- `theme`·컴포넌트 옵션 토큰 표준화.
- 여러 타입이 공유하는 컴포넌트 옵션(showTitle 등)을 `OptionDefinition`으로 DRY 재사용할지 여부.
- 청첩장 `selected_options`의 순서/on·off 오버라이드 정책.
- 컴포넌트/템플릿 버전 관리(ADR-002 4항과 동일하게 현 단계 미도입).

## 8. ADR 계획

- **ADR-007 (신규)**: "섹션/컴포넌트 기반 템플릿 모델". 상태 제안됨으로 작성 → 검토/승인 후 승인됨.
  - ADR-006을 **대체(superseded)** 처리.
  - ADR-002를 **부분 개정**(스키마 위치: 템플릿→컴포넌트, 나머지 결정 유지) 명시.
- 대안 비교(최소): ① 컴포넌트 1급화(채택) ② template-level 유지+공존(B, 기각) ③ 서버 검증 없이 불투명 blob(기각, 트러스트 경계 포기).
