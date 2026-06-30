#!/usr/bin/env bash
# PreToolUse 훅: Write/Edit/Bash 도구 호출 내용에 한국 전화번호, 주소, 주민등록번호 패턴이
# 평문으로 포함되어 있으면 차단한다. (마스킹 없이 그대로 로그/코드/커밋에 남는 것을 방지)
# exit 0  -> 허용
# exit 2  -> 차단, stderr 메시지를 모델에게 전달

set -uo pipefail

INPUT=$(cat)

# 검사 대상 텍스트 추출 (file_text, new_str, command 등 흔한 키들)
CONTENT=$(echo "$INPUT" | grep -oE '"(file_text|new_str|content|command)"[[:space:]]*:[[:space:]]*"[^"]*"')

if [[ -z "$CONTENT" ]]; then
  exit 0
fi

violations=""

# 한국 휴대폰 번호 패턴 (010-1234-5678, 01012345678 등)
if echo "$CONTENT" | grep -qE '01[016789][-. ]?[0-9]{3,4}[-. ]?[0-9]{4}'; then
  violations="${violations}- 전화번호로 추정되는 패턴 발견\n"
fi

# 주민등록번호 패턴 (123456-1234567)
if echo "$CONTENT" | grep -qE '[0-9]{6}[-]?[1-4][0-9]{6}'; then
  violations="${violations}- 주민등록번호로 추정되는 패턴 발견\n"
fi

# 한국 주소 패턴 (시/도 + 구/군 + 동/로/길 형태가 함께 등장)
if echo "$CONTENT" | grep -qE '(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)[가-힣]*(시|도)[가-힣 ]*(구|군)[가-힣 0-9]*(로|길|동)[ 0-9]*'; then
  violations="${violations}- 주소로 추정되는 패턴 발견\n"
fi

if [[ -n "$violations" ]]; then
  echo "개인정보 노출 가능성으로 작업을 차단했습니다:" >&2
  echo -e "$violations" >&2
  echo "마스킹 처리(예: 010-****-5678) 후 다시 시도하거나, 정말 필요한 경우 사용자에게 직접 확인을 받으세요." >&2
  exit 2
fi

exit 0