#!/usr/bin/env bash
#
# CONTRIBUTING.md 의 커밋 메시지 규칙을 검사한다.
#
#   사용법: check-commit-messages.sh <base-ref> <head-ref>
#
# 로컬 훅은 --no-verify 로 우회되므로 실제 강제는 여기서 한다.
set -uo pipefail

export LC_ALL=C.UTF-8 2>/dev/null || true

BASE="${1:?base ref가 필요합니다}"
HEAD="${2:?head ref가 필요합니다}"

TYPES="feat|fix|refactor|docs|test|chore"
MAX_LENGTH=50

# 명령형이 아닌 흔한 어미. CONTRIBUTING: "~수정, ~추가 / ~했음, ~합니다 아님"
NON_IMPERATIVE_SUFFIX='(했음|했습니다|합니다|하였음|되었음|됨|함)$'

failed=0

check() {
  local subject="$1"

  if [[ ! "$subject" =~ ^($TYPES): ]]; then
    echo "  ✗ 타입이 없거나 허용되지 않습니다. 허용: feat, fix, refactor, docs, test, chore"
    echo "    형식은 '<타입>: <제목>' 입니다."
    return 1
  fi

  local length=${#subject}
  if (( length > MAX_LENGTH )); then
    echo "  ✗ 제목이 ${length}자입니다. ${MAX_LENGTH}자 이내로 줄여주세요."
    return 1
  fi

  if [[ "$subject" =~ \.$ ]]; then
    echo "  ✗ 제목 끝에 마침표를 붙이지 않습니다."
    return 1
  fi

  if [[ "$subject" =~ $NON_IMPERATIVE_SUFFIX ]]; then
    echo "  ✗ 제목은 명령형으로 씁니다. '~추가', '~수정' 형태로 바꿔주세요."
    return 1
  fi

  return 0
}

echo "검사 범위: ${BASE}..${HEAD}"
echo

# 머지 커밋은 제목을 우리가 정하지 않으므로 건너뛴다.
mapfile -t subjects < <(git log --no-merges --format=%s "${BASE}..${HEAD}")

if (( ${#subjects[@]} == 0 )); then
  echo "검사할 커밋이 없습니다."
  exit 0
fi

for subject in "${subjects[@]}"; do
  if check "$subject"; then
    echo "  ✓ ${subject}"
  else
    echo "    → ${subject}"
    echo
    failed=1
  fi
done

echo
if (( failed )); then
  echo "커밋 메시지 규칙 위반이 있습니다. 규칙은 .github/CONTRIBUTING.md 를 보세요."
  echo "고치려면: git rebase -i ${BASE}"
  exit 1
fi

echo "커밋 ${#subjects[@]}개 모두 통과했습니다."
