# 템플릿 옵션(options_schema) SDD 진행 기록

- 실행 방식: 서브에이전트, **커밋 금지** (사용자가 최종 검토 후 직접 커밋)
- BASE 커밋: 754e896 (HEAD 고정, 태스크마다 이동하지 않음)
- 리뷰 diff 범위: 각 태스크가 건드리는 파일 경로로 한정 (working tree vs HEAD)
- 계획: docs/superpowers/plans/2026-07-15-template-options.md

## 태스크
- [x] Task 1: SchemaValidator 공용 컴포넌트 (working tree, review clean; Minor: validateData 스키마 미캐싱, static 필드 스타일, com.networknt Error 섀도잉)
- [x] Task 2: isValidTemplate 리팩터링 (working tree, review clean; 특이사항 없음)
- [x] Task 3: Template·TemplateCreateRequest optionsSchema (working tree, review clean; Minor: 테스트가 variants 매핑/ null 케이스 미검증)
- [x] Task 4: createTemplate 메타검증 + ErrorCode (working tree, review clean; 관찰: TemplateResponse.from은 미영속 엔티티에 NPE 가능—기존 코드, 범위 밖)
- [x] Task 5: VariantResponse 노출 (working tree, review clean; Minor: null optionsSchema 케이스 미검증)
