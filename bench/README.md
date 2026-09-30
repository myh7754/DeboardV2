# 페이지네이션 벤치 환경

**이 폴더만** 다른 PC로 복사하면 끝. 안에 빌드된 `app.jar`가 같이 들어있다.

## 실행

```powershell
docker compose up -d
```

**이거 하나면 1000만 건까지 자동으로 채워진다.** 진행 상황:

```powershell
docker compose logs -f seeder
```

`[seeder] 완료 — post 10000000 건` 이 뜨면 끝. 처음 한 번은 **20~35분** 걸린다.

측정:

```powershell
docker rm -f k6-keyset 2>$null
docker compose run --name k6-keyset -e MODE=keyset -e PAGE=999 -e VUS=500 k6 run /scripts/load-test.js

docker rm -f k6-offset 2>$null
docker compose run --name k6-offset -e MODE=offset -e PAGE=999 -e VUS=500 k6 run /scripts/load-test.js
```

한 번에 약 10분.

**앞의 `docker rm -f` 를 빼먹지 말 것.** `--rm` 을 쓰지 않기 때문에(붙이면 컨테이너가 즉시
삭제돼 Docker Desktop에서 로그를 볼 수 없다) 컨테이너가 그대로 남고, 같은 이름으로 다시
돌리면 이 오류가 난다:

```
Error response from daemon: Conflict. The container name "/k6-keyset" is already in use
```

`run.ps1` 은 이름에 실행 시각을 붙여서 이 문제가 없다.

### 결과 보는 곳

k6 스크립트의 `handleSummary()`가 콘솔 출력과 동시에 파일로도 남긴다.
컨테이너를 지워도 결과는 남는다.

| 파일 | 내용 |
|---|---|
| `k6/out/summary-<MODE>-page<N>-vus<N>.txt` | 요약. **p(95)와 실패율이 여기 있다** |
| `k6/out/summary-<MODE>-page<N>-vus<N>.json` | 전체 지표 |
| `k6/out/report.html` | 그래프 (브라우저로 열기) |
| `docker logs k6-keyset` | 콘솔 로그 다시 보기 |

요약 파일은 이렇게 생겼다:

```
  요청 수          123,456
  처리량           205.4 req/s
  실패율           1.23 %
  응답시간 p(95)   2100.8 ms   ← 포트폴리오 표에 쓸 값
```

### run.ps1 (선택)

시드 완료를 기다렸다가 k6까지 한 번에 돌려주는 편의 스크립트. 안 써도 된다.

```powershell
.\run.ps1                      # up -d + 대기 + keyset 999 측정
.\run.ps1 -Mode offset         # 대조군
.\run.ps1 -Status              # 지금 상태만 확인
```

Linux/macOS면 `./run.sh`, `MODE=offset ./run.sh`.

## 필요한 것

측정 PC에 **Docker Desktop만** 있으면 된다. JDK도 gradle도 소스도 필요 없다.

- Docker Desktop (실행 중일 것)
- 여유 디스크 **15GB 이상** (1000만 건 post 테이블이 약 3~4GB, 인덱스/리두 포함)
- 비밀키 챙길 필요 없다. 더미값이 `docker-compose.yml`에 기본값으로 박혀 있어서 `.env`가 없어도 뜬다.

## 구성

```
app.jar             빌드된 백엔드 (약 87MB). 이게 있어야 돈다
docker-compose.yml
seed.sql            증식 프로시저
seeder.sh           시더 컨테이너가 실행하는 스크립트
run.ps1 / run.sh    편의 스크립트 (선택)
k6/load-test.js
```

띄우는 컨테이너는 **mysql / redis / backend / seeder** 4개. k6는 프로필이라 측정할 때만 붙는다.
grafana, prometheus, loki, promtail, frontend는 뺐다 — 측정에 필요 없고, 같은 CPU와 디스크를
쓰면서 노이즈만 만든다. 특히 promtail은 운영 compose에서 `ClaudeCode` 폴더 전체를 마운트하는데
다른 PC엔 그 경로가 없다.

## 1000만 건이 채워지는 순서

Spring으로 1000만 건을 넣으면 몇 시간 걸린다. 애플리케이션이 매 행마다 파라미터를 바인딩해
네트워크로 넘기기 때문이다. `INSERT ... SELECT`는 데이터가 MySQL 밖으로 안 나가고 서버 안에서만
복사되므로 비교가 안 되게 빠르다. 그래서 두 단계로 나눴다.

1. **backend** — `DummyDataLoader`(`\Profile("prod")`)가 users 3,000 / feed 50 /
   feed_subscription / post **500,000**을 넣는다. 제목 50종 × 본문 7종이라 내용이 다양하다.
2. **seeder** — post가 50만이 될 때까지 기다렸다가 `seed.sql`을 실행한다.
   50만 → 1M → 2M → 4M → 8M → **10M**, 다섯 라운드면 끝난다.

시더는 **몇 번을 다시 올려도 안전하다.** `bench_fill_posts`가 현재 건수를 읽고 부족한 만큼만
채우므로, 이미 1000만이면 아무것도 안 하고 끝나고 중간에 끊겼으면 이어서 간다.

`seed.sql`이 같이 고치는 것:

- **created_at 동률** — `DummyDataLoader`는 `now`를 루프 밖에서 한 번만 만들어 50만 건이
  전부 같은 시각이다. 그대로면 keyset 커서의 `(created_at, id)` 비교가 항상 두 번째 갈래
  (`created_at = ? AND id < ?`)로만 떨어져, 정작 `key_len=17`을 노리고 OR 전개형을 쓴
  그 설계를 측정하지 못한다. id 기준 1초 간격으로 펼친다.
- **`docs/dummy-data.md`의 프로시저는 쓰지 않는다.** 거기 프로시저는
  `information_schema.columns`에서 `LIMIT 50000`으로 뽑는데 그 테이블 행 수가 5만보다
  훨씬 적다. 진행률만 `10000000 / 10000000`으로 찍히고 실제로는 100만 건 남짓만 들어간다.

### 확인

```powershell
docker compose exec -T mysql mysql -uroot -proot deboard -e "SELECT COUNT(*), COUNT(DISTINCT created_at) FROM post;"
```

**두 값이 다 `10000000`이어야 정상이다.** 두 번째가 1이면 `created_at` 펼치기가 안 된 것이고,
그 상태로 측정하면 숫자는 나오지만 의미가 없다.

포트폴리오에 "1,000만 건"이라 쓰려면 이 출력이 근거다.

## 새 머신에서 문제가 되던 것들

| 문제 | 처리 |
|---|---|
| `ddl-auto: none` + `init.sql`은 GRANT만 → 테이블이 없음 | `SPRING_JPA_HIBERNATE_DDL_AUTO=update`. `Post`의 `\Table(indexes=...)` 2개도 같이 생성됨 |
| prod 프로필의 `org.hibernate.SQL=debug` | `LOGGING_LEVEL_*=warn` |
| RSS 스케줄러가 측정 중 끼어듦 | `RSS_SCHEDULE_CRON`을 매년 1/1로 |
| `Dockerfile`의 `-Xmx384m` (t3.small 기준) | compose에서 entrypoint를 덮어써 `HEAP`으로 조절 |
| MySQL 기본 버퍼풀 128M | `MYSQL_BUFFER_POOL` (기본 2G) |
| 측정 PC에 JDK/gradle 없음 | 소스 빌드 대신 프리빌드 `app.jar`를 bind mount |
| `CommandLineRunner`는 웹서버 기동 **뒤에** 돈다 | health가 UP이어도 시드 중일 수 있어, 시더는 행 수를 폴링 |

RSS `resolve()`는 문자열 조작뿐이라 시드에 **네트워크가 필요 없다.** 인터넷 없이도 돈다.

## 조정 손잡이

`.env.example`을 `.env`로 복사해 고치거나, 환경변수로 넘긴다.

| 값 | 기본 | 비고 |
|---|---|---|
| `TARGET_ROWS` | `10000000` | 시더가 채울 목표 건수 |
| `MYSQL_BUFFER_POOL` | `2G` | 측정 PC 램의 50~60%까지 올려도 된다 |
| `HEAP` | `1g` | EC2 t3.small과 맞추려면 `384m` |
| `HIKARI_POOL` | `20` | `application-local.yml`과 같은 값. prod는 10 |
| `REDIS_POOL` | `100` | prod 기본값은 8 |
| `OPEN_IN_VIEW` | `false` | 스프링 기본은 true |

## 커넥션 풀을 왜 prod 기본값(10)으로 두지 않았나

`application-prod.yml`은 EC2 t3.small(2 vCPU / 2GB)에 맞춰 Hikari 풀 10, Redis 풀 8이다.
그 값으로 VUS 500을 때리면 **쿼리가 아니라 풀 크기를 측정하게 된다.**

리틀의 법칙: `처리량 = 커넥션 수 / 커넥션 점유시간`. 쿼리가 1초라 쳐도 `10 / 1s = 10 req/s`가
상한인데, k6는 500 VU × `sleep(1)` 로 약 500 req/s를 요구한다. 수요가 공급의 50배라
대기열이 무한히 쌓이고 30초 `connection-timeout` 에 걸려 절반 이상이 실패한다.
응답시간이 안정화되지 않고 계속 우상향하면 이 상태다(congestion collapse).

**이 상태에서는 offset과 keyset이 같은 숫자로 나온다.** 둘 다 풀 앞에서 똑같이 줄 서기 때문에
쿼리 차이가 묻힌다. 그래서 벤치는 `application-local.yml` 과 같은 **20** 으로 맞췄다.
예전에 실제로 측정했던 값이라 근거가 있다.

**더 키우지 않는 이유:** 커넥션은 많을수록 좋은 게 아니다. 대략 `코어 수 x 2` 를 넘으면
컨텍스트 스위칭과 락 경합 때문에 처리량이 오히려 떨어진다. HikariCP 문서도 작은 풀을 권한다.
100 같은 숫자는 근거가 없다.

### 20으로도 offset은 무너질 수 있다 — 그게 결과다

풀을 20으로 올려도 500 VU를 다 받아내지는 못한다. 다시 리틀의 법칙:

| 쿼리 1건 소요 | 풀 20의 처리량 상한 | k6 수요(500 VU) | 결과 |
|---|---|---|---|
| 10 ms | 2,000 req/s | 500 req/s | 여유 |
| 100 ms | 200 req/s | 500 req/s | 포화 |
| 1 s | 20 req/s | 500 req/s | 붕괴 |

1000만 건에서 `LIMIT 10 OFFSET 9980` 은 초 단위가 나올 수 있으므로 **offset은 500 VU에서
무너지는 게 정상이고, 그 자체가 보여주려는 결과다.** 중요한 건 같은 조건에서 **keyset은
안 무너지는 것**이다. 풀 10일 때의 문제는 둘 다 무너져서 차이가 안 보였다는 점이었다.

깨끗한 쿼리 지연 숫자가 필요하면 포화되지 않는 지점에서 한 번 더 잰다:

```powershell
docker compose run --name k6-offset-low -e MODE=offset -e PAGE=999 -e VUS=30 k6 run /scripts/load-test.js
docker compose run --name k6-keyset-low -e MODE=keyset -e PAGE=999 -e VUS=30 k6 run /scripts/load-test.js
```

VUS 500은 "부하 상황에서의 안정성", VUS 30은 "쿼리 자체의 지연". 표에는 둘을 구분해 적는다.

로그에서 알아보는 법:

```
HikariPool-1 - Connection is not available, request timed out after 30000ms
  (total=10, active=10, idle=0, waiting=48)
```

`waiting` 이 쌓이면 풀 고갈이다. 이때 `request.exception.duplicate` 가 1:1로 따라붙는데
이건 **버그가 아니라 로그 샘플링**이다(`GlobalExceptionHandler.logException`).
같은 예외가 반복되면 스택트레이스 대신 카운트만 찍는 기능이라, 같은 예외가 수천 번 났다는 뜻일 뿐이다.

반대로 "t3.small 스펙에서 500명을 버티는가"를 재고 싶다면 `HIKARI_POOL=10 REDIS_POOL=8`
로 되돌리면 된다. 다만 그건 **용량 측정**이지 페이지네이션 알고리즘 비교가 아니다.

## app.jar 다시 굽기

코드를 고쳤을 때만, 저장소가 있는 개발 PC에서 (JDK 21 필요):

```powershell
.\run.ps1 -Rebuild -SeedOnly
```

수동으로는 저장소 루트에서 `.\gradlew.bat bootJar -x test` 후
`build/libs/DeboardV2-*.jar` 를 `bench/app.jar` 로 복사.

## 측정값을 포트폴리오로 옮길 때

기존 표는 **EC2 t3.small(2 vCPU / 2GB) + RDS** 기준이고 이 환경은 그것과 다르다.
숫자를 갈아끼우면 `측정 환경` 줄도 같이 고쳐야 한다. 서로 다른 머신의 수치를 한 표에 섞지 말 것.

또 기존 EC2 측정은 prod 프로필의 `org.hibernate.SQL=debug`가 켜진 채였을 가능성이 높다.
여기서는 꺼져 있어 절대값이 더 좋게 나온다. **비교는 같은 환경의 offset ↔ keyset끼리만.**

## 정리

```powershell
docker compose down          # 컨테이너만 내림 (1000만 건 유지)
docker compose down -v       # 데이터까지 삭제. 다시 시드해야 함
```
