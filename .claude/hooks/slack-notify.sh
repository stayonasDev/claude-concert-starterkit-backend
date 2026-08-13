#!/usr/bin/env bash
# Claude Code Stop hook: 작업(응답) 종료 시 Slack Incoming Webhook으로 알림 전송.
# SLACK_WEBHOOK_URL은 .env(git-ignore 대상)에 등록한다. 값이 없으면 조용히 스킵한다.

set -a
[ -f .env ] && . .env
set +a

if [ -z "$SLACK_WEBHOOK_URL" ]; then
  exit 0
fi

PROJECT=$(basename "$PWD")
TIME=$(date "+%Y-%m-%d %H:%M:%S")

# 한글이 포함된 payload를 curl 커맨드라인 인자로 넘기면 Windows 네이티브 curl.exe가
# 시스템 ANSI 코드페이지 기준으로 인자를 재해석해 깨진다. 파일에 UTF-8로 써서
# --data-binary @file로 넘기면 바이트 그대로 전송되어 인코딩 문제가 없다.
TMPFILE=$(mktemp)
trap 'rm -f "$TMPFILE"' EXIT

cat > "$TMPFILE" <<EOF
{"text":":bell: *Claude Code 알림*\n프로젝트: ${PROJECT}\n상태: 완료\n시간: ${TIME}"}
EOF

curl -s -X POST -H 'Content-type: application/json; charset=utf-8' --data-binary "@${TMPFILE}" "$SLACK_WEBHOOK_URL" >/dev/null 2>&1 || true
