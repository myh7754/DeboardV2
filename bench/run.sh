#!/usr/bin/env bash
# run.ps1 과 동일. 측정 PC가 Linux/macOS일 때 사용.
#
# 시드는 compose가 알아서 한다 (docker compose up -d 만으로 1000만 건까지 채워짐).
# 이 스크립트는 편의용 — 시더가 끝나길 기다렸다가 k6를 돌린다.
#
#   ./run.sh                  up -d + 시드 완료 대기 + keyset 999페이지 측정
#   MODE=offset ./run.sh      대조군
#   STATUS=1 ./run.sh         지금 상태만 보고 종료
#   SEED_ONLY=1 ./run.sh      시드 완료까지만 기다리고 종료
set -euo pipefail
cd "$(dirname "$0")"

MODE="${MODE:-keyset}"
PAGE="${PAGE:-999}"
VUS="${VUS:-500}"
TARGET_ROWS="${TARGET_ROWS:-10000000}"
SEED_ONLY="${SEED_ONLY:-0}"
STATUS="${STATUS:-0}"

scalar() {
  local v
  v=$(docker compose exec -T mysql mysql -uroot -proot --default-character-set=utf8mb4 -N -B deboard -e "$1" 2>/dev/null | tail -1) || v=""
  case "$v" in ''|*[!0-9]*) echo -1 ;; *) echo "$v" ;; esac
}

post_count() { scalar "SELECT COUNT(*) FROM post"; }

show_status() {
  local c d
  c=$(post_count)
  if [ "$c" -lt 0 ]; then
    echo "    post 테이블 없음 (DB가 안 떴거나 백엔드가 스키마 생성 전)"
    return 0
  fi
  d=$(scalar "SELECT COUNT(DISTINCT created_at) FROM post")
  echo "    post = $c 건, created_at 고유값 = $d"
  if [ "$c" -gt 0 ] && [ "$d" = "1" ]; then
    echo "    !! created_at이 전부 동일 — seed.sql 펼치기가 아직 안 돌았습니다"
  fi
}

# app.jar가 없는 채로 up 하면 Docker가 같은 이름의 디렉터리를 만들어버린다
[ -f app.jar ] || { echo "bench/app.jar 가 없습니다. 저장소 루트에서 ./gradlew bootJar -x test 후 build/libs/*.jar 를 bench/app.jar 로 복사하세요."; exit 1; }

mkdir -p k6/out

echo "==> docker compose up -d (mysql / redis / backend / seeder)"
docker compose up -d

echo "==> 현재 상태"
show_status
count=$(post_count)
[ "$STATUS" = "1" ] && exit 0

# 시더가 목표치를 채울 때까지 대기. 실제 진행 로그는 seeder 컨테이너가 찍는다.
if [ "$count" -lt "$TARGET_ROWS" ]; then
  echo "==> 시더 대기 (${TARGET_ROWS}건까지). 진행: docker compose logs -f seeder"
  stall=0; last=-2
  while :; do
    c=$(post_count)
    if [ "$c" -ge "$TARGET_ROWS" ]; then echo "    post $c — 시드 완료"; break; fi

    # 시더가 죽었으면 무한정 기다리지 않는다
    if [ -z "$(docker compose ps -q seeder 2>/dev/null)" ]; then
      echo "seeder 컨테이너가 없습니다. docker compose logs seeder 를 확인하세요. (현재 $c 건)"
      exit 1
    fi

    echo "    post $c / $TARGET_ROWS"
    if [ "$c" = "$last" ]; then stall=$((stall+1)); else stall=0; fi
    last=$c
    [ "$stall" -ge 120 ] && { echo "20분간 진행 없음. docker compose logs seeder 확인"; exit 1; }
    sleep 10
  done
  show_status

  # 증식 직후엔 버퍼풀이 쓰기 캐시로 더럽혀져 있다. 읽기 측정 전에 한 번 정리.
  echo "==> 재기동 (버퍼풀 초기화)"
  docker compose restart mysql backend
  sleep 30
fi

[ "$SEED_ONLY" = "1" ] && { echo "시드 완료. 종료합니다."; exit 0; }

TAG="$MODE-page$PAGE-vus$VUS"
NAME="k6-$TAG-$(date +%m%d-%H%M)"

echo "==> k6 실행 — MODE=$MODE PAGE=$PAGE VUS=$VUS (약 10분)"
echo "    컨테이너 이름: $NAME"
# --rm 을 쓰지 않는다. 끝난 뒤에도 로그를 볼 수 있어야 한다. 지울 때: docker container prune
docker compose run --name "$NAME" -e MODE="$MODE" -e PAGE="$PAGE" -e VUS="$VUS" k6 run /scripts/load-test.js

echo
echo "결과 ─────────────────────────────"
echo "  k6/out/summary-$TAG.txt     요약 (표에 옮길 값)"
echo "  k6/out/summary-$TAG.json    전체 지표"
echo "  k6/out/report.html          그래프"
echo "  docker logs $NAME           콘솔 로그 다시 보기"
