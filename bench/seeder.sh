#!/bin/sh
# docker compose up -d 만으로 1000만 건이 채워지도록 하는 시더 컨테이너.
#
# 백엔드의 DummyDataLoader(@Profile("prod"))가 post 50만 건을 다 넣을 때까지 기다렸다가
# seed.sql로 created_at을 펼치고 목표 건수까지 증식한 뒤 스스로 종료한다.
#
# 몇 번을 다시 올려도 안전하다. bench_fill_posts가 현재 건수를 읽고 부족한 만큼만
# 채우므로, 이미 목표치면 아무것도 하지 않고 끝난다.
set -eu

TARGET="${TARGET_ROWS:-10000000}"
SEED_ROWS=500000          # DummyDataLoader가 넣는 양
WAIT_LIMIT=360            # 10초 × 360 = 60분까지만 기다린다

export MYSQL_PWD=root     # -p 로 넘기면 경고가 계속 찍혀서 환경변수로
MYSQL="mysql -h mysql -uroot --default-character-set=utf8mb4"

count() {
    c=$($MYSQL -N -B deboard -e "SELECT COUNT(*) FROM post" 2>/dev/null | tail -1) || c=""
    case "$c" in ''|*[!0-9]*) echo -1 ;; *) echo "$c" ;; esac
}

echo "[seeder] 목표 ${TARGET} 건. DummyDataLoader 대기 중..."

i=0
while :; do
    c=$(count)

    if [ "$c" -ge "$TARGET" ]; then
        echo "[seeder] 이미 ${c} 건 — 할 일 없음. 종료."
        exit 0
    fi

    if [ "$c" -ge "$SEED_ROWS" ]; then
        echo "[seeder] DummyDataLoader 완료 (${c} 건). 증식 시작."
        break
    fi

    if [ "$c" -lt 0 ]; then
        echo "[seeder] post 테이블 대기 중... (백엔드가 스키마 생성 중)"
    else
        echo "[seeder] post ${c} / ${SEED_ROWS}"
    fi

    i=$((i + 1))
    if [ "$i" -ge "$WAIT_LIMIT" ]; then
        echo "[seeder] 60분간 50만 건에 도달하지 못했습니다. docker compose logs backend 를 확인하세요."
        exit 1
    fi
    sleep 10
done

echo "[seeder] created_at 펼치기 + ${TARGET} 건까지 증식 (INSERT ... SELECT 로 두 배씩)"
$MYSQL deboard < /seed.sql
$MYSQL deboard -e "CALL bench_fill_posts(${TARGET});"

echo "[seeder] 통계 갱신"
$MYSQL deboard -e "ANALYZE TABLE post;"

echo "[seeder] 결과 ─────────────────────────────"
$MYSQL deboard -e "SELECT COUNT(*) AS post_rows, COUNT(DISTINCT created_at) AS distinct_created_at FROM post;"
$MYSQL deboard -e "SELECT COUNT(*) AS user_rows FROM users;"
$MYSQL deboard -e "SELECT MIN(created_at) AS oldest, MAX(created_at) AS newest FROM post;"

final=$(count)
if [ "$final" -lt "$TARGET" ]; then
    echo "[seeder] 실패: ${final} / ${TARGET}. 디스크 여유를 확인하세요."
    exit 1
fi
echo "[seeder] 완료 — post ${final} 건. 이제 k6를 돌리면 됩니다."
