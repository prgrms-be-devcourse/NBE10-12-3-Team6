#!/usr/bin/env bash
# 이메일 인증 발송 API 부하 테스트 (기본 20 concurrent)
# 사용법:
#   ./scripts/load-test-email.sh              # 20개, 기본 URL
#   ./scripts/load-test-email.sh 50           # 50개
#   ./scripts/load-test-email.sh 20 http://staging.example.com   # 개수 + URL

set -euo pipefail

COUNT="${1:-20}"
BASE_URL="${2:-http://localhost:8080}"
ENDPOINT="$BASE_URL/api/v1/auth/check_email"

# 실제 수신 확인용 이메일 — 이 주소들에는 진짜 인증 코드가 도착합니다.
# 앞자리 요청(req001, req002, req003)에 배정됩니다.
REAL_EMAILS=(
  "bristol9128@naver.com"
  "yoonsun9128@gmail.com"
  "azalea9128@naver.com"
)
REAL_COUNT="${#REAL_EMAILS[@]}"

# 매 실행마다 고유 prefix — 재실행 시에도 쿨다운/existsByEmail 회피
RUN_ID="$(date +%s)"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "[start] $COUNT concurrent requests to $ENDPOINT (run_id=$RUN_ID)"
echo "        - real: ${REAL_EMAILS[*]}"
echo "        - fake: load-test-${RUN_ID}-N@example.com (나머지)"
echo

send_one() {
  local i=$1
  local email
  local kind
  # 앞자리 REAL_COUNT개는 실제 이메일, 나머지는 fake
  if [ "$i" -le "$REAL_COUNT" ]; then
    email="${REAL_EMAILS[$((i-1))]}"
    kind="real"
  else
    email="load-test-${RUN_ID}-${i}@example.com"
    kind="fake"
  fi

  local body_file="$TMP_DIR/body_${i}"
  local meta_file="$TMP_DIR/meta_${i}"

  curl -sS \
    -o "$body_file" \
    -w "%{http_code} %{time_total}" \
    -X POST "$ENDPOINT" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\"}" \
    > "$meta_file" 2>"$TMP_DIR/err_${i}" || true

  # meta 파일 예: "200 0.048123"
  local http_code time_total
  read -r http_code time_total < "$meta_file"
  printf "req%03d [%s] status=%s time=%ss email=%s\n" \
    "$i" "$kind" "${http_code:-ERR}" "${time_total:-N/A}" "$email"
}

START_TS=$(date +%s)

for ((i=1; i<=COUNT; i++)); do
  send_one "$i" &
done

wait

END_TS=$(date +%s)
ELAPSED_S=$((END_TS - START_TS))

echo
echo "[done] all $COUNT requests finished in ~${ELAPSED_S}s wall-clock"
echo

# 상태 코드 집계
echo "[status summary]"
for meta in "$TMP_DIR"/meta_*; do
  awk '{print $1}' "$meta"
done | sort | uniq -c
echo

# 200이 아닌 응답이 있으면 body 예시 최대 3개 출력
NON_200_INDICES=()
for meta in "$TMP_DIR"/meta_*; do
  code=$(awk '{print $1}' "$meta")
  if [ "$code" != "200" ]; then
    idx=$(basename "$meta" | sed 's/meta_//')
    NON_200_INDICES+=("$idx")
  fi
done

if [ "${#NON_200_INDICES[@]}" -gt 0 ]; then
  echo "[non-200 example bodies]"
  for idx in "${NON_200_INDICES[@]:0:3}"; do
    echo "--- req${idx} ---"
    cat "$TMP_DIR/body_${idx}" 2>/dev/null || true
    echo
  done
fi

echo "[tip] 서버 로그에서 [verify-timing] total=Xms 가 $COUNT번 찍혀야 하며,"
echo "      이후 백그라운드에서 [mail-timing] send=Xms 가 mail-N 스레드에서 순차적으로 나옵니다."
