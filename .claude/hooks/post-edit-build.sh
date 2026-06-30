#!/usr/bin/env bash
# PostToolUse 훅: Java/Kotlin 소스 파일이 Edit/Write로 수정된 후 자동으로 빌드를 실행한다.
# stdin으로 훅 입력(JSON)을 받는다. 표준 Claude Code PostToolUse 페이로드 형식을 기대한다.

set -euo pipefail

INPUT=$(cat)

# 수정된 파일 경로 추출 (tool_input.file_path 기준)
FILE_PATH=$(echo "$INPUT" | grep -o '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed -E 's/.*"file_path"[[:space:]]*:[[:space:]]*"([^"]*)".*/\1/')

if [[ -z "$FILE_PATH" ]]; then
  exit 0
fi

# Java/Kotlin/Gradle 관련 파일이 아니면 스킵
if [[ ! "$FILE_PATH" =~ \.(java|kt|kts|gradle)$ ]]; then
  exit 0
fi

PROJECT_ROOT="$(pwd)"

if [[ ! -f "$PROJECT_ROOT/gradlew" ]]; then
  echo "경고: gradlew를 찾을 수 없어 빌드를 건너뜁니다." >&2
  exit 0
fi

echo "변경된 파일(${FILE_PATH}) 감지: ./gradlew build 실행 중..." >&2

if ! "$PROJECT_ROOT/gradlew" build -q; then
  echo "빌드 실패. 위 출력을 확인하고 수정하세요." >&2
  # exit 2 -> Claude Code가 이 메시지를 모델에게 전달해 즉시 대응하게 함
  exit 2
fi

echo "빌드 성공." >&2
exit 0