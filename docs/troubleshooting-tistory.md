> **한 줄 요약** — 개인 블로그 애그리게이터의 `/api/posts` 조회 성능을 끌어올리면서 만난 세 가지 병목과 해결 과정. **인덱스 최적화 → Redis 캐싱 → SWR 패턴 도입**까지, 한 단계를 해결하면 다음 병목이 드러나는 연속적인 여정이었다. **결과: p95 응답시간 13.68s → 46ms (VUS 100), VUS 1500에서도 1.35s.**

## 이 글에서 다루는 내용

1. OR 조건에 잡아먹힌 인덱스 — 복합 인덱스와 UNION ALL 전략
2. DB 부하 자체를 없애기 — 비로그인 첫 페이지 Redis 캐싱
3. TTL을 줄였더니 터진 Cache Stampede — SWR 패턴 도입

---

## 들어가며

처음 k6로 부하 테스트를 돌렸을 때 VUS 100 기준 p95가 **13.68초**가 찍혔다. 목표는 3초였으니 4.6배를 초과한 수치다. 이 글은 그 13.68초를 최종적으로 **46ms까지**(그리고 VUS 1500에서는 SWR로 1.35초까지) 끌어내린 과정을 기록한 것이다.

성능 목표는 처음부터 단순하게 잡았다.

```
[응답시간 p(95)]
  목표값 : 3초 미만
  근거   : Google 연구 기준, 페이지 로드 3초 초과 시 사용자 이탈률 53%

[에러율]
  목표값 : 1% 미만
  근거   : 일반 웹 서비스 SLA 기준

[동시 사용자]
  목표값 : 100명
  근거   : 개인 블로그 애그리게이터 서비스의 예상 피크 트래픽
```

측정 환경은 전부 **EC2 T3.Small (2 vCPU, 2GB RAM)** 단일 인스턴스. MySQL, Spring Boot, k6가 동일 머신에서 실행된다. 데이터셋은 게시글 10만 건, 비로그인 요청 기준이다.

---

## 1. OR 조건에 잡아먹힌 인덱스 — 복합 인덱스와 UNION ALL 전략

> 이 섹션 한 줄 요약 — `OR` 조건 때문에 인덱스 풀스캔이 일어나던 쿼리를, `is_public` 컬럼 + `(is_public, created_at DESC)` 복합 인덱스로 **p95 13.68s → 2.39s**로 단축한 이야기.

### 문제 인식

VUS 100 부하에서 p95가 **13.68초**. EXPLAIN을 돌려보니 `post` 테이블 조회가 `type = ALL`(인덱스 풀스캔)로 동작하고 있었다.

### 원인 분석

#### OR 조건으로 인한 인덱스 무력화

`PostCustomRepositoryImpl`의 가시성 조건은 다음과 같았다.

```java
BooleanExpression isPublic = qPost.feed.feedType.eq(FeedType.PUBLIC)
        .or(qPost.author.isNotNull());
```

실제 실행 SQL:

```sql
WHERE (f.feed_type = 'PUBLIC' OR p.user_id IS NOT NULL)
```

OR 조건은 두 컬럼에 걸쳐 있기 때문에 MySQL 옵티마이저가 하나의 인덱스만 선택할 수 있고, 결국 **인덱스 풀스캔**으로 떨어진다.

게다가 `p.user_id IS NOT NULL`은 전체 데이터의 대다수를 포함하는 **선택도가 낮은 조건**이다. 옵티마이저 입장에서 "어차피 대부분의 행이 해당되니 인덱스보다 풀스캔이 낫다"고 판단하기 좋은 형태였다.

#### ORDER BY + LIMIT에서 손해 보는 구조

페이지당 10개만 필요한데, WHERE 조건을 만족하는 모든 행을 찾은 뒤 정렬(filesort)을 거쳐 10개를 자르는 구조로 동작하고 있었다. 인덱스와 ORDER BY가 연계되지 않아 불필요한 연산이 누적된다.

### 설계 고민 — 왜 `is_public` 컬럼이었나

현재 Post는 생성 방식에 따라 세 가지로 분류된다.

```
[내부 작성글]
  생성 방식  : Post.from()
  공개 범위  : 누구나

[PUBLIC RSS]
  생성 방식  : Post.fromRss() + feed.feedType = PUBLIC
  공개 범위  : 누구나

[PRIVATE RSS]
  생성 방식  : Post.fromRss() + feed.feedType = PRIVATE
  공개 범위  : 해당 피드 구독자만
```

처음에는 `PostType { MEMBER, RSS }` ENUM을 추가하려 했지만, PRIVATE RSS의 "구독자만 볼 수 있음"이라는 동적 조건이 남아 OR 조건 자체를 제거할 수 없었다.

대안으로 택한 것이 **조회 시점의 계산을 저장 시점으로 옮기는 방식**이다. Post 엔티티에 `is_public` boolean 컬럼을 두고, 저장 시점에 이 값을 미리 계산해 둔다.

- `Post.from()` 저장 시 → `isPublic = true`
- `Post.fromRss()` 저장 시 → `isPublic = (feed.feedType == PUBLIC)`

이렇게 하면 내부 작성글과 PUBLIC RSS를 하나의 단일 컬럼 조건으로 통합할 수 있다.

### UNION ALL 전략 (Java 레벨)

OR을 제거하고 각각 인덱스를 타게 분리하는 것이 목표였다. SQL `UNION ALL`이 이상적이지만 **QueryDSL `JPAQueryFactory`가 UNION ALL을 네이티브로 지원하지 않기 때문에**, DB에 쿼리 두 개를 따로 날리고 Java `Stream.concat()`으로 합치는 방식으로 구현했다.

```
비로그인 → 단일 쿼리: WHERE is_public = 1
로그인   → 쿼리1: WHERE is_public = 1
           쿼리2: WHERE is_public = 0 AND feed_id IN (구독 피드 목록)
           → Java Stream.concat() 후 createdAt 내림차순 정렬 + 페이징
```

네트워크 왕복이 한 번 늘지만, 각 쿼리가 인덱스를 타므로 풀스캔을 없앤 이득이 훨씬 크다.

### 핵심 — `(is_public, created_at DESC)` 복합 인덱스

단순히 인덱스를 타는 것에서 그치지 않고, **ORDER BY + LIMIT에서 10개만 읽고 멈추는 구조**를 만드는 것이 진짜 목표였다.

```
복합 인덱스 (is_public, created_at DESC):

is_public=true  │ 2025-03-30  ← LIMIT 10이면 여기서 멈춤
is_public=true  │ 2025-03-29
is_public=true  │ 2025-03-28
is_public=true  │ ...
                │ (10만개 전부 읽지 않아도 됨)
```

`is_public = true`가 전체의 99%를 차지해도, 복합 인덱스에서 `created_at` 순서가 이미 정렬되어 있으므로 앞에서 10개만 읽고 반환할 수 있다.

쿼리 변화를 정리하면 이렇다.

```sql
-- 개선 전: OR 조건으로 인덱스 풀스캔
WHERE (f.feed_type = 'PUBLIC' OR p.user_id IS NOT NULL
       OR EXISTS (SELECT 1 FROM feed_subscription ...))
ORDER BY created_at DESC LIMIT 0, 10

-- 개선 후: 단일 등치 조건으로 복합 인덱스 탐
WHERE p.is_public = 1
ORDER BY created_at DESC LIMIT 0, 10
```

### 결과 — VUS 50

```
[P95 응답시간]
  개선 전 : 6.63s
  개선 후 : 852ms
  변화   : 7.8배 개선

[평균 응답시간]
  개선 전 : 5.42s
  개선 후 : 394ms
  변화   : 13.8배 개선

[RPS]
  개선 전 : 6.8/s
  개선 후 : 30.5/s
  변화   : 4.5배 증가

[실패율]
  개선 전 : 0%
  개선 후 : 0%
  변화   : 유지
```

### 결과 — VUS 100 (목표 트래픽)

```
[P95 응답시간]
  개선 전   : 13.68s
  개선 후   : 2.39s
  변화     : 5.7배 개선
  목표 충족 : O (목표 3초 미만)

[평균 응답시간]
  개선 전   : 12.06s
  개선 후   : 1.35s
  변화     : 8.9배 개선

[RPS]
  개선 전   : 6.8/s
  개선 후   : 36.6/s
  변화     : 5.4배 증가

[실패율]
  개선 전   : 0%
  개선 후   : 0%
  변화     : 유지
  목표 충족 : O (목표 1% 미만)
```

### 모니터링으로 본 병목 분석 (Grafana)

```
[System CPU max]
  Before : 100%
  After  : 100%
  변화   : 여전히 포화

[Load Average max]
  Before : 10.3
  After  : 19.9
  변화   : 증가 (처리 쿼리 수 증가)

[HikariCP Size]
  Before : 10
  After  : 20
  변화   : 풀 확대

[HikariCP Pending max]  ← 핵심 지표
  Before : 84
  After  : 46
  변화   : 45% 감소

[Connection Acquire Time]
  Before : 최대 8초
  After  : 감소
  변화   : 커넥션 대기 해소
```

#### CPU는 여전히 100%인데 왜 빨라졌나?

쿼리당 CPU 소모시간이 줄었기 때문이다. CPU 총 가용량은 동일하지만, 한 쿼리가 빨리 끝나니 같은 CPU로 더 많은 쿼리를 처리한다.

```
Before: 쿼리 1개당 CPU 점유 길음 → 초당 6.8개 처리, RPS 고착
After:  쿼리 1개당 CPU 점유 짧음 → 초당 36.6개 처리, RPS 5.4배 증가
```

Load Average가 오히려 올라간 것도 같은 이유다. Before는 적은 쿼리를 오래 붙잡고 있었고, After는 많은 쿼리가 빠르게 돌고 있다. 동시 실행 프로세스가 많아져 Load Average는 올라가지만, 각각 빨리 끝나서 전체 응답은 빠르다.

**HikariCP Pending이 84→46으로 45% 감소**한 것이 핵심 증거다. 쿼리가 빨리 끝나서 커넥션이 빨리 반납되니 대기가 줄었고, 이것이 p95 13.68s → 2.39s의 직접 원인이다.

### 한계 — 다음 병목이 드러남

VUS 100 목표는 달성했지만 **System CPU가 여전히 100%**. 쿼리당 CPU 소모를 줄여 처리량을 끌어올렸을 뿐, 동시 요청이 일정 수준을 넘으면 총 CPU 소모량은 여전히 증가한다. 인덱스 최적화만으로는 T3.Small에서 VUS 100 이상의 여유를 확보하기 어려운 지점이었다.

다음 개선 방향은 명확했다. **쿼리를 더 빠르게 하는 게 아니라, DB 요청 자체를 줄이자.**

---

## 2. DB 부하 자체를 없애기 — 비로그인 첫 페이지 Redis 캐싱

> 이 섹션 한 줄 요약 — 비로그인 첫 페이지 요청을 Redis에 캐시해 **MySQL CPU 포화(100%)를 55.6%까지 해소**하고 **p95 2.39s → 46ms**로 단축한 이야기.

### 배경

트래픽 패턴을 분석하면, 비로그인 사용자의 첫 페이지 조회가 전체 요청의 대다수를 차지한다. 이 요청은 항상 동일한 결과를 반환하므로 캐싱 적합도가 매우 높다.

### 구현

```
비로그인 + page=0 + size=10 → Redis 캐시 조회
  └ HIT  → DB 쿼리 없이 즉시 반환
  └ MISS → DB 조회 → 결과를 Redis에 저장 (TTL 5분) → 반환
```

캐싱 조건과 그 이유:

- **비로그인 사용자만** — 로그인 사용자는 구독 피드에 따라 결과가 달라짐
- **첫 페이지(page=0)만** — 대다수 트래픽이 첫 페이지에 집중
- **TTL 5분** — 새 글 반영과 캐시 효율의 균형

```java
public Page<PostDetailResponse> readAll(int size, int page) {
    boolean isAnonymous = (auth == null || "anonymousUser".equals(auth.getPrincipal()));

    if (isAnonymous && page == 0 && size == 10) {
        return readAllCached(pageable);
    }
    return postCustomRepository.findAll(pageable);
}

private Page<PostDetailResponse> readAllCached(Pageable pageable) {
    Object cached = redisService.getValue(cacheKey);
    if (cached instanceof PostPageCacheDto dto) {
        return new PageImpl<>(dto.getContent(), pageable, dto.getTotalCount());
    }
    Page<PostDetailResponse> result = postCustomRepository.findAll(pageable);
    redisService.setValueWithExpire(cacheKey, new PostPageCacheDto(...), Duration.ofMinutes(5));
    return result;
}
```

### 결과 (VUS 100)

```
[P95 응답시간]
  인덱스 개선 전 : 13.68s
  인덱스 개선 후 : 2.39s
  캐싱 적용 후   : 46.39ms
  전체 개선     : 295배

[평균 응답시간]
  인덱스 개선 전 : 12.06s
  인덱스 개선 후 : 1.35s
  캐싱 적용 후   : 35.57ms
  전체 개선     : 339배

[RPS]
  인덱스 개선 전 : 6.8/s
  인덱스 개선 후 : 36.6/s
  캐싱 적용 후   : 79.8/s
  전체 개선     : 11.7배

[실패율]
  인덱스 개선 전 : 0%
  인덱스 개선 후 : 0%
  캐싱 적용 후   : 0%
  전체 개선     : 유지
```

### 모니터링 비교 (Grafana, VUS 100)

```
[System CPU max]
  인덱스 개선 후 : 100%
  캐싱 적용 후   : 55.6%
  변화          : CPU 포화 해소

[Load Average max]
  인덱스 개선 후 : 19.9
  캐싱 적용 후   : 1.85
  변화          : 10.8배 감소

[HikariCP Pending max]
  인덱스 개선 후 : 46
  캐싱 적용 후   : 14
  변화          : 70% 감소
```

#### 왜 CPU 포화가 해소되었나?

```
인덱스 개선: 쿼리당 CPU ↓ → 같은 CPU로 더 많이 처리 → 그래도 100% 포화
캐싱 적용:   쿼리 수 자체 ↓ → MySQL CPU 사용량 자체가 감소 → 55.6%로 여유
```

인덱스 개선은 "쿼리가 빨라졌지만 DB 요청 횟수는 그대로"였다. 캐싱은 **DB 요청 자체를 제거**한다. 비로그인 첫 페이지 요청이 Redis에서 처리되니 MySQL로 가는 쿼리 수가 급감했다.

HikariCP Pending이 0이 아닌 14로 찍힌 건 **캐시 cold start** 때문이다. 테스트 시작 직후 캐시가 비어있어 첫 요청들이 DB로 직행하면서 순간적으로 커넥션 경합이 발생하지만, 캐시가 워밍된 이후에는 Pending이 거의 0으로 유지된다.

### 다음 문제로 이어지는 결정

캐싱은 성공적이었지만, 여기서 **TTL 5분**이 또 다른 문제의 씨앗이 된다. 이 이야기는 다음 섹션으로 이어진다.

---

## 3. TTL을 줄였더니 터진 Cache Stampede — SWR 패턴 도입

> 이 섹션 한 줄 요약 — 최신성 개선을 위해 TTL을 줄였더니 **VUS 1500에서 실패율 4.10%**까지 악화. Stale-While-Revalidate 패턴으로 캐시가 비는 순간을 제거해 **실패율 0.51%, RPS 1.9배 증가**로 회복한 이야기.

### 배경 — 캐시 최신성 vs 안정성의 트레이드오프

TTL 5분에는 명확한 단점이 있었다. **신규 게시글이 최대 5분간 비로그인 첫 페이지에 반영되지 않는 문제**다. 로그인 사용자는 캐시를 거치지 않아 즉시 확인 가능하지만, 비로그인 방문자는 "방금 올라온 글"을 볼 수 없다.

최신성을 개선하기 위해 두 가지 변경을 적용했다.

- **TTL 5분 → 60초** — 최대 지연 시간을 1분 이내로 단축
- **멤버 글 작성 시 캐시 eviction** — 작성 즉시 다음 요청에서 새 글 반영

RSS 배치 저장은 빈도가 높아 eviction 시 Cache Churn 우려가 있어 TTL 기반 갱신만 적용했다.

### 새로운 문제 — VUS 1500에서 실패율 급증

변경 적용 후 VUS 1500 부하 테스트를 재실행한 결과가 충격적이었다.

```
[p95 응답시간]
  TTL 5분          : 46ms
  TTL 60초 적용 후 : 5.36s

[평균 응답시간]
  TTL 5분          : 35ms
  TTL 60초 적용 후 : 2.20s

[실패율]           ← 핵심
  TTL 5분          : 0%
  TTL 60초 적용 후 : 4.10%

[최대 응답시간]
  TTL 5분          : -
  TTL 60초 적용 후 : 28.88s
```

HikariCP 커넥션 풀 타임아웃이 다수 발생. 캐싱 이전으로 거의 회귀한 수준의 지표다.

### 원인 — Cache Stampede (Thundering Herd)

TTL이 60초가 되면서 캐시가 1분마다 만료되는데, 이 순간 **VUS 1500개가 동시에 MISS를 맞으면서 DB에 한꺼번에 쿼리가 몰리는 현상**이 발생했다.

```
t=0~59초: 캐시 HIT → 정상 (DB 요청 0)
t=60초:  캐시 만료 → 1500개 동시 MISS → DB에 1500개 쿼리 폭탄
          └ HikariCP 풀 소진 → 대기 중인 요청들 타임아웃
t=60+수초: 캐시 재생성 완료 → 정상 복귀
```

이 현상이 **1분마다 반복**되면서 평균 응답시간과 실패율이 모두 악화됐다. TTL을 5분에서 60초로 줄이면서 Stampede 발생 빈도가 5배가 된, 구조적 부작용이었다.

### 해결 전략 비교

Stampede를 막는 방법은 여러 가지가 있다. 여덟 가지 대안을 비교했다.

```
[TTL Jitter]
  Stampede 방지 : 부분적 / 수요 기반 : O / 응답 지연 : 없음 / 복잡도 : 낮음
  → 단일 키 문제엔 부적합

[Distributed Lock (SETNX)]
  Stampede 방지 : O / 수요 기반 : O / 응답 지연 : 있음 / 복잡도 : 중간
  → 락 대기 요청에 지연 발생

[Request Coalescing]
  Stampede 방지 : O / 수요 기반 : O / 응답 지연 : 최소 / 복잡도 : 중간
  → 적합하지만 캐시 비는 순간 존재

[Probabilistic Early Expiration]
  Stampede 방지 : O / 수요 기반 : O / 응답 지연 : 없음 / 복잡도 : 높음
  → 메타데이터 관리 복잡

[Stale-While-Revalidate]  ← 선택
  Stampede 방지 : O / 수요 기반 : O / 응답 지연 : 없음 / 복잡도 : 중간
  → 최적

[Background Refresh (스케줄러)]
  Stampede 방지 : O / 수요 기반 : X / 응답 지연 : 없음 / 복잡도 : 낮음
  → 수요 없어도 주기적 DB 호출

[Two-tier Cache (L1+L2)]
  Stampede 방지 : O / 수요 기반 : O / 응답 지연 : 없음 / 복잡도 : 중간
  → 단일 서버라 효과 제한적

[Event-driven (Kafka)]
  Stampede 방지 : O / 수요 기반 : O / 응답 지연 : 없음 / 복잡도 : 높음
  → 이 규모엔 오버엔지니어링
```

#### 스케줄러 vs SWR — 왜 SWR인가

스케줄러(`@Scheduled`로 50초마다 캐시 pre-warm)도 Stampede를 원천 차단할 수 있지만, **수요와 무관하게 돌아가는 구조적 낭비**가 있다. 새벽 3시에 아무도 접속하지 않아도 50초마다 DB 쿼리가 실행되고, 이는 캐싱의 철학("자주 쓰는 것을 빠르게")과 어긋난다.

SWR은 **요청이 들어올 때만, 갱신이 필요하면 비동기로 갱신**하는 방식이라 수요 기반으로 동작하면서도 Stampede를 방지한다.

### 구현 — Stale-While-Revalidate

#### 핵심 아이디어 — 이중 키 구조

```
데이터 키 (post:public:page:0)      → 실제 게시글 데이터 (hard TTL: 70초)
신호 키  (post:public:page:0:stale) → 갱신 필요 여부 플래그 (soft TTL: 50초)
```

신호 키가 만료되면 "이제 갱신할 때가 됐다"는 신호가 된다. 이때 요청이 들어오면:

1. **기존 데이터 키는 아직 살아있으므로 즉시 반환** (사용자는 기다리지 않음)
2. **백그라운드에서 비동기로 DB 조회 → 두 키 모두 갱신**

데이터 키가 한 번도 비지 않기 때문에 Stampede가 발생할 구조적 여지가 없다.

#### 동작 흐름

```
[정상 HIT]   요청 → 데이터 키 HIT + 신호 키 존재 → 즉시 반환

[stale HIT]  요청 → 데이터 키 HIT + 신호 키 없음 → 즉시 반환 (stale 데이터)
                                                 + refreshAsync() 트리거
                                                   ├ 신호 키 즉시 재설정 (중복 방지)
                                                   └ DB 조회 → 두 키 갱신

[MISS]       요청 → 데이터 키 없음 → refreshSync() (동기 fallback)
                                   → DB 조회 → 두 키 저장 → 반환

[글 작성]    save() → DB INSERT → evict() → 두 키 삭제
                    → 다음 요청에서 MISS → refreshSync()로 새 글 포함 캐시 생성
```

#### 중복 갱신 방지가 핵심

`refreshAsync()` 진입 시 **첫 줄에서 신호 키를 즉시 재설정**하는 것이 핵심 디테일이다. 100개 요청이 동시에 "stale" 상태를 감지해도, 첫 번째 요청이 비동기 작업 시작 직후 신호 키를 복구하면 그 사이 들어온 다른 요청들은 "stale 아님"으로 판정되어 중복 트리거가 차단된다.

```java
@Async("cacheTaskExecutor")
@Transactional(readOnly = true)
public void refreshAsync(String cacheKey, Pageable pageable) {
    // 중복 갱신 방지 — DB 조회 전에 신호 키부터 재설정
    redisService.setValueWithExpire(cacheKey + STALE_SUFFIX, "1", STALE_TTL);
    fetchAndStore(cacheKey, pageable);
}
```

#### 설계 판단 — `PostCacheService` 별도 빈 분리

`@Async`, `@Transactional`은 Spring AOP 프록시 기반이라 **같은 클래스 내부 호출 시 적용되지 않는 한계**가 있다. 캐시 관리 책임을 `PostCacheService`로 분리해 프록시가 정상 동작하도록 했고, 동시에 캐시 로직과 비즈니스 로직의 단일 책임 원칙(SRP)도 지켰다.

전용 스레드 풀(`cacheTaskExecutor`, core=2/max=4)로 격리해 RSS 수집, 메일 발송 등 다른 비동기 작업과 스레드 경합이 생기지 않도록 했다.

### 결과 (VUS 1500)

```
[P95 응답시간]
  TTL 60초 (Stampede) : 5.36s
  SWR 적용 후          : 1.35s
  변화                 : 75% 감소

[평균 응답시간]
  TTL 60초 (Stampede) : 2.20s
  SWR 적용 후          : 680ms
  변화                 : 69% 감소

[실패율]                ← 핵심
  TTL 60초 (Stampede) : 4.10%
  SWR 적용 후          : 0.51%
  변화                 : 87% 감소

[RPS]                   ← 핵심
  TTL 60초 (Stampede) : 260/s
  SWR 적용 후          : 496/s
  변화                 : 1.9배 증가

[총 처리 요청]
  TTL 60초 (Stampede) : 72,535
  SWR 적용 후          : 150,041
  변화                 : 2배 이상
```

RPS가 거의 2배가 된 것이 핵심이다. "빨라졌다"가 아니라 **같은 자원으로 더 많은 요청을 처리**하고 있다는 의미다. Stampede 구간에 모든 스레드가 대기로 막혀있던 시간이 사라지면서 처리량 자체가 증가했다.

정상 부하 구간(VUS 300)에서는 **p95 102ms, 실패율 0.03%**로, 사실상 모든 요청이 캐시 HIT로 처리되어 Redis 조회 + 네트워크 오버헤드 수준의 응답 속도를 보여준다.

### 남은 0.51% 실패

VUS 1500에서 `max=30.42s`가 관측됐다. 30초는 전형적인 타임아웃 경계값이다.

```
[Cold start]
  설명 : 테스트 시작 직후 캐시가 비어있어 refreshSync() 경로 진입
  대응 : 일회성이라 허용

[글 작성 evict 직후]
  설명 : save() → evict() → 다음 요청이 MISS
  대응 : 기능상 불가피
```

두 케이스 모두 구조상 일회성이고 즉시 복구되므로, VUS 1500 부하에서 0.51%는 **목표(1% 미만)의 절반 수준**으로 허용 가능하다.

---

## 마치며 — 세 단계에서 얻은 교훈

### 캐싱 자체가 또 다른 병목을 만들 수 있다

이번 경험에서 가장 크게 바뀐 관점이다. 처음에는 "DB가 느리니 캐시로 해결"이라는 단순한 구도로 접근했지만, 캐시는 **만료 시점에 DB 부하를 집중시키는 구조적 함정**을 동반한다는 것을 직접 겪었다. TTL을 줄이면 부작용이 오히려 심해지는 반직관적 현상도 데이터로 확인했다. 앞으로 캐싱을 도입할 때는 "캐시 만료 시의 동작"까지 설계 단계에서 함께 고려할 것이다.

### "문제가 발생하지 않았으니 괜찮다"는 잘못된 기준

초기 TTL 60초 구현 시 "Stampede가 발생할 수 있다는 건 알지만 현재 규모에선 감당 가능"이라는 판단을 내릴 뻔했다. 그러나 VUS 1500이라는 목표 트래픽을 설정한 순간부터, **발생할 가능성이 있는 문제는 미리 대비하는 것이 목표 자체의 일관성**이라는 점을 인식하게 됐다.

### 대안 비교는 그 자체로 설계 역량

TTL Jitter, 분산 락, Request Coalescing, XFetch, SWR, 스케줄러, Two-tier, Event-driven 여덟 가지 대안을 비교하면서 각 방식이 **해결하는 병목이 무엇인지, 어떤 트레이드오프를 수반하는지**를 정리했다. 단순히 "가장 유명한 방법"이나 "가장 간단한 방법"이 아닌, **현재 서비스 특성과 규모에 맞는 판단 기준**을 세우는 훈련이 됐다.

SWR 선택 근거를 한 줄로 정리하면 이렇다.

> 수요 기반으로 동작하면서(스케줄러 단점 해소), 캐시가 비는 순간 자체를 제거해 Stampede를 원천 차단하고(분산 락 단점 해소), 응답 지연 없이(모든 사용자가 즉시 응답 수신) 이 프로젝트 규모에 적정한 구현 복잡도를 갖는다.

### 숫자로 본 전체 여정

```
[초기]
  p95 (VUS 100) : 13.68s
  RPS           : 6.8/s
  병목          : 인덱스 풀스캔

[인덱스 최적화]
  p95 (VUS 100) : 2.39s
  RPS           : 36.6/s
  병목          : MySQL CPU 포화

[Redis 캐싱]
  p95 (VUS 100) : 46ms
  RPS           : 79.8/s
  병목          : 캐시 만료 시 Stampede

[SWR 적용]
  p95           : 1.35s (VUS 1500 기준)
  RPS           : 496/s
  병목          : —
```

**13.68초 → 46ms (VUS 100), 그리고 VUS 1500에서도 1.35초.** 한 병목을 해결하면 다음 병목이 드러나는 과정에서, "더 빠른 쿼리"에서 "더 적은 쿼리"로, 그리고 "만료되지 않는 캐시"로 관점이 단계적으로 이동했던 것이 이번 여정의 핵심이었다.
