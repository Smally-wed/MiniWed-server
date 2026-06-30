#!/usr/bin/env bash
# PreToolUse 훅: Bash 도구로 실행하려는 명령이 되돌리기 어려운 고위험 작업
# (배포, DB 스키마 변경, 강제 삭제, 외부 강제 전송 등) 패턴에 해당하면 차단하고
# 사용자 확인을 먼저 받도록 안내한다.
# exit 0 -> 허용
# exit 2 -> 차단

set -uo pipefail

INPUT=$(cat)

COMMAND=$(echo "$INPUT" | grep -oE '"command"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed -E 's/.*"command"[[:space:]]*:[[:space:]]*"(.*)"/\1/')

if [[ -z "$COMMAND" ]]; then
  exit 0
fi

declare -a PATTERNS=(
  'rm[[:space:]]+-rf'
  'DROP[[:space:]]+TABLE'
  'DROP[[:space:]]+DATABASE'
  'TRUNCATE[[:space:]]+TABLE'
  'DELETE[[:space:]]+FROM[[:space:]]+.*'
  'git[[:space:]]+push[[:space:]]+.*--force'
  'git[[:space:]]+push[[:space:]]+.*-f([[:space:]]|$)'
  'kubectl[[:space:]]+apply'
  'kubectl[[:space:]]+delete'
  'docker[[:space:]]+push'
  'terraform[[:space:]]+apply'
  'terraform[[:space:]]+destroy'
  'scp[[:space:]]+.*@'
  './gradlew[[:space:]]+.*deploy'
  './gradlew[[:space:]]+.*publish'
)

for p in "${PATTERNS[@]}"; do
  if echo "$COMMAND" | grep -qiE "$p"; then
    echo "고위험 명령으로 판단되어 실행을 차단했습니다: ${COMMAND}" >&2
    echo "배포, DB 변경, 강제 삭제, 외부 전송에 해당하는 명령입니다." >&2
    echo "사용자에게 무엇을 왜 실행하려는지 먼저 설명하고 명시적 승인을 받은 뒤 진행하세요." >&2
    exit 2
  fi
done

exit 0