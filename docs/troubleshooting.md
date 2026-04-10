# Troubleshooting

---

## [성능] /api/posts 게시글 목록 조회 성능 개선

### 성능 목표

| 지표 | 목표값 | 근거 |
|---|---|---|
| 응답시간 p(95) | < 3초 | Google 연구 기준, 페이지 로드 3초 초과 시 사용자 이탈률 53% |
| 에러율 | < 1% | 일반 웹 서비스 SLA 기준 |
| 동시 사용자 | 100명 | 개인 블로그 애그리게이터 서비스의 예상 피크 트래픽 |

---

### 문제 인식

k6 부하 테스트(VUS 100) 결과, **p95 응답시간이 13.68초로 목표(3초) 대비 4.6배 초과**.

EXPLAIN으로 실행 계획을 분석한 결과,
post 테이블 조회에서 `type = ALL` (인덱스 풀스캔)가 발생하는 것을 확인.
여러 사람이 동시에 요청하는 상황에서 DB 부하가 집중될 수 있는 구조였음.

---

### 원인 분석

#### 1. OR 조건으로 인한 인덱스 무력화

`PostCustomRepositoryImpl`의 `getVisibilityCondition()`:

```java
BooleanExpression isPublic = qPost.feed.feedType.eq(FeedType.PUBLIC)
        .or(qPost.author.isNotNull());  // OR 조건
```

실제 실행되는 SQL:

```sql
WHERE (f.feed_type = 'PUBLIC' OR p.user_id IS NOT NULL)
```

OR 조건은 두 컬럼에 걸쳐 있기 때문에 MySQL 옵티마이저가 하나의 인덱스만 선택할 수밖에 없고,
결과적으로 인덱스 풀스캔(`type = ALL`)이 발생함.

#### 2. IS NOT NULL의 선택도 문제

`p.user_id IS NOT NULL` 조건은 전체 데이터의 대다수를 포함하는 조건.
MySQL 옵티마이저가 "어차피 대부분의 행이 해당되니 인덱스보다 풀스캔이 낫다"고 판단할 수 있음.

#### 3. ORDER BY + LIMIT에서 손해 보는 구조

페이지당 10개만 필요한데, WHERE 조건을 만족하는 모든 행을 찾은 뒤
정렬(filesort)을 거쳐 10개를 자르는 구조로 동작함.
인덱스와 ORDER BY가 연계되지 않아 불필요한 연산이 발생함.

---

### 고민 과정

#### Post의 3가지 타입 정리

현재 Post는 생성 방식에 따라 3가지로 분류됨:

| 타입 | 생성 방식 | 공개 범위 |
|---|---|---|
| 내부 작성글 | `Post.from()` | 누구나 |
| PUBLIC RSS | `Post.fromRss()` + `feed.feedType = PUBLIC` | 누구나 |
| PRIVATE RSS | `Post.fromRss()` + `feed.feedType = PRIVATE` | 해당 피드 구독자만 |

#### PostType ENUM 추가 검토 → 한계 확인

처음에는 `PostType { MEMBER, RSS }` ENUM을 추가하는 방법을 검토했으나,
PRIVATE RSS의 경우 "구독자만 볼 수 있음"이라는 동적 조건이 남아 있어
OR 조건 자체를 제거하지 못한다는 한계를 확인함.

#### is_public 컬럼 도입 결정

타입 구분 대신 **조회 시점의 계산을 저장 시점으로 옮기는 방식** 채택:

- `Post.from()` 저장 시 → `isPublic = true`
- `Post.fromRss()` 저장 시 → `isPublic = (feed.feedType == PUBLIC)`

이렇게 하면 내부 작성글과 PUBLIC RSS를 하나의 컬럼으로 통합할 수 있음.

#### UNION ALL 전략 (Java 레벨 구현)

OR 조건을 제거하고 각각 인덱스를 타게 분리하는 것이 목표.
SQL UNION ALL이 이상적이나 **QueryDSL `JPAQueryFactory`가 UNION ALL을 네이티브로 지원하지 않아**,
DB에 쿼리 2개를 따로 날리고 Java `Stream.concat()`으로 합치는 방식으로 구현:

```
비로그인 → 단일 쿼리: WHERE is_public = 1
로그인   → 쿼리1: WHERE is_public = 1
           쿼리2: WHERE is_public = 0 AND feed_id IN (구독 피드 목록)
           → Java Stream.concat() 후 createdAt 내림차순 정렬 + 페이징
```

목표(OR 없이 각각 인덱스 탐)는 SQL UNION ALL과 동일.
네트워크 왕복이 1회 늘지만, 각 쿼리가 인덱스를 타므로 풀스캔 비용 절감이 훨씬 큼.

---

### 핵심 개선 포인트

#### `(is_public, created_at DESC)` 복합 인덱스

단순히 인덱스를 타는 것에서 그치지 않고,
**ORDER BY + LIMIT에서 10개만 읽고 멈추는 구조**를 만드는 것이 목표.

```
복합 인덱스 (is_public, created_at DESC):

is_public=true  │ 2025-03-30  ← LIMIT 10이면 여기서 멈춤
is_public=true  │ 2025-03-29
is_public=true  │ 2025-03-28
is_public=true  │ ...
                │ (10만개 전부 읽지 않아도 됨)
```

`is_public = true`가 전체의 99%를 차지해도,
복합 인덱스에서 `created_at` 순서가 이미 정렬되어 있으므로
앞에서 10개만 읽고 반환 가능.

---

### 구현 내용

#### 변경 파일

| 파일 | 변경 내용 |
|---|---|
| `Post.java` | `isPublic` boolean 필드 추가, `@Table(indexes)` 복합 인덱스 선언 |
| `Post.from()` | `isPublic = true` (사용자 작성글은 항상 공개) |
| `Post.fromRss()` | `isPublic = (feed.getFeedType() == FeedType.PUBLIC)` |
| `PostCustomRepositoryImpl` | `getVisibilityCondition()` / `isSubscribed()` 제거, `is_public` 기반 쿼리로 전환 |
| `PostRepository` | `updateIsPublicByFeedId()` 추가 (피드 타입 변경 시 일괄 업데이트) |
| `FeedService` | `changeFeedType()` 추가 (Feed 타입 변경 + Post is_public 일괄 갱신 트랜잭션) |
| `FeedController` | `PATCH /api/rss/feed/{feedId}/type` 추가 (관리자용 피드 타입 변경 API) |

#### 비로그인 쿼리 (개선 전 → 후)

```sql
-- 개선 전: OR 조건으로 인덱스 풀스캔
WHERE (f.feed_type = 'PUBLIC' OR p.user_id IS NOT NULL
       OR EXISTS (SELECT 1 FROM feed_subscription ...))
ORDER BY created_at DESC LIMIT 0, 10

-- 개선 후: 단일 등치 조건으로 복합 인덱스 탐
WHERE p.is_public = 1
ORDER BY created_at DESC LIMIT 0, 10
```

---

### k6 성능 테스트 결과 (EC2 T3.Small)

EC2 T3.Small (2 vCPU, 2GB RAM) 환경에서 측정.
MySQL, Spring Boot, k6가 동일 인스턴스에서 실행. 데이터셋: 10만건, 비로그인 요청.

#### VUS 50

| 지표 | 개선 전 | 개선 후 | 변화 |
|---|---|---|---|
| P95 응답시간 | 6.63s | **852ms** | **7.8배 개선** |
| 평균 응답시간 | 5.42s | 394ms | 13.8배 개선 |
| RPS | 6.8/s | **30.5/s** | 4.5배 증가 |
| 실패율 | 0% | 0% | 유지 |

#### VUS 100 — 목표 트래픽

| 지표 | 개선 전 | 개선 후 | 변화 | 목표 충족 |
|---|---|---|---|---|
| P95 응답시간 | 13.68s | **2.39s** | **5.7배 개선** | ✓ (목표 < 3초) |
| 평균 응답시간 | 12.06s | 1.35s | 8.9배 개선 | |
| RPS | 6.8/s | **36.6/s** | 5.4배 증가 | |
| 실패율 | 0% | 0% | 유지 | ✓ (목표 < 1%) |

---

### 모니터링으로 병목 분석 (Grafana)

#### VUS 50 Before vs After

| 지표 | Before | After | 변화 |
|---|---|---|---|
| System CPU max | **1.0 (100%)** | **1.0 (100%)** | 여전히 포화 |
| Process CPU 평균 | 0.032 (3.2%) | 0.124 (12.4%) | Spring 처리량 증가 |
| Load Average max | 9.83 | 14.0 | 증가 (처리 쿼리 수 증가) |
| HikariCP Pending max | **35** | **8** | **77% 감소** |

#### VUS 100 Before vs After

| 지표 | Before | After | 변화 |
|---|---|---|---|
| System CPU max | **1.0 (100%)** | **1.0 (100%)** | 여전히 포화 |
| Process CPU 평균 | 0.061 (6.1%) | 0.055 (5.5%) | 비슷 |
| Load Average max | 10.3 | 19.9 | 증가 (처리 쿼리 수 증가) |
| HikariCP Size | 10 | 20 | 풀 확대 |
| HikariCP Pending max | **84** | **46** | **45% 감소** |
| Connection Acquire Time | **최대 8초** | 감소 | 커넥션 대기 해소 |

#### CPU는 100%인데 왜 빨라졌나?

쿼리당 CPU 소모시간이 줄었기 때문이다.
CPU 총 가용량은 동일하지만, 한 쿼리가 빨리 끝나니 같은 CPU로 더 많은 쿼리를 처리한다.

```
Before: 쿼리 1개당 CPU 점유 길음 → 초당 6.8개 처리, RPS 고착
After:  쿼리 1개당 CPU 점유 짧음 → 초당 36.6개 처리, RPS 5.4배 증가
```

Load Average가 오히려 올라간 것도 같은 이유다.
Before는 적은 쿼리를 오래 붙잡고 있었고, After는 많은 쿼리가 빠르게 돌고 있다.
동시 실행 프로세스가 많아져 Load Average는 올라가지만, 각각 빨리 끝나서 전체 응답은 빠르다.

#### HikariCP Pending 감소 = 개선의 핵심 증거

```
VUS 50: Pending 35 → 8 (77% 감소)
VUS 100: Pending 84 → 46 (45% 감소)
```

쿼리가 빨리 끝나서 커넥션이 빨리 반납되니 대기가 줄었다.
이것이 응답시간 13.68s → 2.39s의 직접 원인이다.

---

### 로컬 환경 참고 결과

로컬 환경(i5-6300HQ, 4코어)에서도 동일한 개선 패턴을 확인함. 참고용으로 기록.

#### VUS 100

| 지표 | 개선 전 | 개선 후 | 변화 |
|---|---|---|---|
| P95 응답시간 | 7.4s | **756ms** | 9.8배 개선 |
| RPS | 15/s | 50/s | 3.3배 증가 |

#### VUS 300 (스트레스)

| 지표 | 개선 전 | 개선 후 | 변화 |
|---|---|---|---|
| P95 응답시간 | 35.3s | **10.4s** | 3.4배 개선 |
| RPS | 14.9/s | 47/s | 3.2배 증가 |
| 실패율 | **7.81%** | **0.04%** | 실패 사실상 제거 |

#### 로컬 Grafana 분석 (VUS 300)

HikariCP 설정이 적용되지 않는 버그를 발견:
`DataSourceProxyConfig`에서 DataSource를 수동 생성할 때 `spring.datasource.hikari.*` 설정이 바인딩되지 않아
HikariCP 기본값(10)으로 동작함.

```java
// 문제 코드: hikari.* 설정 무시됨
DataSource actualDataSource = properties.initializeDataSourceBuilder().build();

// 수정 코드: @ConfigurationProperties로 hikari.* 설정 바인딩
@Bean
@ConfigurationProperties(prefix = "spring.datasource.hikari")
public HikariDataSource hikariDataSource(DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
}
```

커넥션 풀을 40으로 확장해도 MySQL CPU 포화(System CPU 100%)로 인해 오히려 악화:

```
pool=10 (기본): avg=4.15s, RPS=47/s
pool=40 (확장): avg=4.47s, RPS=44/s
```

커넥션 풀 적정값 공식: (코어 수 × 2) + 1 = (4 × 2) + 1 = 9
→ 기본값 10이 이미 이 머신의 최적값에 근접.

---

### 한계 분석 및 다음 개선 방향

#### 현재 한계

| 병목 자원 | 포화 시점 | 증거 |
|---|---|---|
| MySQL CPU | VUS 50 이상 (T3.Small) | System CPU 100%, Load Average 2코어 대비 5~10배 |

인덱스 개선으로 쿼리당 CPU 소모를 줄여 처리량을 5배 이상 높였지만,
동시 요청이 일정 수준을 넘으면 총 CPU 소모량은 여전히 증가한다.
T3.Small에서 인덱스 최적화만으로는 VUS 100 이상에서 여유를 확보하기 어렵다.

#### 다음 개선: DB 요청 자체를 줄이기

| 항목 | 예상 효과 | 근거 |
|---|---|---|
| 비로그인 1페이지 Redis 캐싱 | RPS 수배 증가 | DB 요청 자체를 제거, CPU 경합 해소 |
| COUNT 쿼리 Redis 캐싱 (TTL 5분) | 커넥션 소비 50% 감소 | 매 요청 2쿼리 → 1쿼리, 총 개수는 실시간 불필요 |

#### 그 이후: 인프라 스케일링

캐싱으로도 한계에 도달하면, 애플리케이션 레벨의 최적화는 소진된 것이므로 인프라 확장이 필요:
- **스케일 업**: CPU/메모리가 더 큰 인스턴스로 교체
- **스케일 아웃**: MySQL Read Replica로 읽기 트래픽 분산

---

---

## [성능] 비로그인 첫 페이지 Redis 캐싱

### 배경

인덱스 개선으로 VUS 100 목표(p95 < 3초)는 달성했지만,
Grafana 모니터링에서 **MySQL CPU 100% 포화**가 지속되는 것을 확인.
쿼리당 CPU 소모를 줄이는 것만으로는 한계가 있었고,
**DB 요청 자체를 줄이는 것**이 다음 개선 방향이었다.

트래픽 패턴을 분석하면, 비로그인 사용자의 첫 페이지 조회가 전체 요청의 대다수를 차지.
이 요청은 항상 동일한 결과를 반환하므로 캐싱 적합도가 높다.

---

### 구현

#### 캐싱 전략

```
비로그인 + page=0 + size=10 → Redis 캐시 조회
  └ HIT  → DB 쿼리 없이 즉시 반환
  └ MISS → DB 조회 → 결과를 Redis에 저장 (TTL 5분) → 반환
```

#### 캐싱 조건

| 조건 | 이유 |
|---|---|
| 비로그인 사용자만 | 로그인 사용자는 구독 피드에 따라 결과가 달라짐 |
| 첫 페이지(page=0)만 | 대다수 트래픽이 첫 페이지에 집중 |
| TTL 5분 | 새 글 반영과 캐시 효율의 균형 |

#### 변경 파일

| 파일 | 변경 내용 |
|---|---|
| `PostPageCacheDto` | Redis 직렬화용 DTO (content + totalCount) |
| `PostServiceImpl.readAll()` | 비로그인 + page=0 + size=10 판별 → `readAllCached()` 분기 |
| `PostServiceImpl.readAllCached()` | Redis 조회 → 캐시 미스 시 DB 조회 후 저장 |
| `RedisKeyConstants` | `POST_PUBLIC_PAGE` 키 상수 추가 |

#### 핵심 코드

```java
// PostServiceImpl.java
public Page<PostDetailResponse> readAll(int size, int page) {
    boolean isAnonymous = (auth == null || "anonymousUser".equals(auth.getPrincipal()));

    if (isAnonymous && page == 0 && size == 10) {
        return readAllCached(pageable);  // Redis 캐시 경로
    }
    return postCustomRepository.findAll(pageable);  // DB 직접 조회
}

private Page<PostDetailResponse> readAllCached(Pageable pageable) {
    Object cached = redisService.getValue(cacheKey);
    if (cached instanceof PostPageCacheDto dto) {
        return new PageImpl<>(dto.getContent(), pageable, dto.getTotalCount());
    }
    // 캐시 미스: DB 조회 → Redis 저장 (TTL 5분)
    Page<PostDetailResponse> result = postCustomRepository.findAll(pageable);
    redisService.setValueWithExpire(cacheKey, new PostPageCacheDto(...), Duration.ofMinutes(5));
    return result;
}
```

---

### k6 성능 테스트 결과 (EC2 T3.Small, VUS 100)

| 지표 | 인덱스 개선 전 | 인덱스 개선 후 | 캐싱 적용 후 | 변화 (전체) |
|---|---|---|---|---|
| P95 응답시간 | 13.68s | 2.39s | **46.39ms** | **295배 개선** |
| 평균 응답시간 | 12.06s | 1.35s | **35.57ms** | **339배 개선** |
| RPS | 6.8/s | 36.6/s | **79.8/s** | **11.7배 증가** |
| 실패율 | 0% | 0% | **0%** | 유지 |

---

### 모니터링 비교 (Grafana, VUS 100)

| 지표 | 인덱스 개선 후 | 캐싱 적용 후 | 변화 |
|---|---|---|---|
| System CPU max | **1.0 (100%)** | **0.556 (55.6%)** | CPU 포화 해소 |
| System CPU 평균 | - | 0.111 (11.1%) | 여유 확보 |
| Process CPU 평균 | 0.055 (5.5%) | 0.062 (6.2%) | 비슷 |
| Load Average max | 19.9 | **1.85** | **10.8배 감소** |
| HikariCP Pending max | 46 | **14** | **70% 감소** |
| Connection Timeout | - | 0 | 타임아웃 없음 |

#### 왜 CPU 포화가 해소되었나?

인덱스 개선은 **쿼리가 빨라졌지만 DB 요청 횟수는 그대로**였다.
요청이 몰리면 결국 MySQL CPU가 100%에 도달했다.

캐싱은 **DB 요청 자체를 제거**한다.
비로그인 첫 페이지 요청이 Redis에서 처리되니 MySQL로 가는 쿼리 수가 급감.
같은 VUS 100이지만 MySQL이 처리할 쿼리가 줄어 CPU 55.6%로 여유가 생겼다.

```
인덱스 개선: 쿼리당 CPU ↓ → 같은 CPU로 더 많이 처리 → 그래도 100% 포화
캐싱 적용:   쿼리 수 자체 ↓ → MySQL CPU 사용량 자체가 감소 → 55.6%로 여유
```

#### HikariCP Pending max 14의 의미

Pending이 0이 아닌 이유는 **캐시 cold start** 때문이다.
테스트 시작 직후 캐시가 비어있어 첫 요청들이 DB로 직행하면서 순간적으로 커넥션 경합 발생.
캐시가 워밍된 이후에는 Pending이 거의 0으로 유지된다.

---

### SQL 인덱스 안티패턴 (학습 내용)

| 안티패턴 | 이유 | 대안 |
|---|---|---|
| `OR` 조건 | 두 인덱스 동시 사용 불가 | `UNION ALL` |
| `IS NOT NULL` | 선택도 낮으면 옵티마이저가 풀스캔 선택 | 명시적 값 컬럼 |
| 함수 적용 `YEAR(col)` | 인덱스 무효화 | 범위 조건으로 변경 |
| `LIKE '%keyword%'` | 앞에 % 있으면 인덱스 무효 | Full-Text Index |

---

---

## [성능] TTL 단축으로 드러난 Cache Stampede 문제와 SWR 패턴 도입

### 배경 — 캐시 최신성 vs 안정성의 트레이드오프

Redis 캐싱 도입 당시 TTL을 5분으로 설정했으나, **신규 게시글이 최대 5분간 비로그인 첫 페이지에 반영되지 않는 문제**가 있었다.
로그인 사용자는 캐시를 거치지 않아 즉시 확인 가능하지만, 비로그인 방문자는 "방금 올라온 글"을 볼 수 없다.

최신성을 개선하기 위해 두 가지 변경을 적용:

| 변경 | 목적 |
|---|---|
| TTL 5분 → 60초 | 최대 지연 시간을 1분 이내로 단축 |
| 멤버 글 작성 시 캐시 eviction | 작성 즉시 다음 요청에서 새 글 반영 |

RSS 배치 저장은 빈도가 높아 eviction 시 Cache Churn 우려가 있어 TTL 기반 갱신만 적용.

---

### 새로운 문제 발생 — VUS 1500 부하 테스트에서 실패율 급증

변경 적용 후 VUS 1500 부하 테스트를 재실행한 결과:

| 지표 | TTL 5분 | TTL 60초 적용 후 |
|---|---|---|
| p95 응답시간 | 46ms | **5.36s** |
| 평균 응답시간 | 35ms | 2.20s |
| 실패율 | 0% | **4.10%** |
| 최대 응답시간 | - | 28.88s |

HikariCP 커넥션 풀 타임아웃이 다수 발생. 캐싱 이전으로 거의 회귀한 수준의 성능 지표.

---

### 원인 분석 — Cache Stampede (Thundering Herd)

TTL이 60초가 되면서 캐시가 1분마다 만료되는데, 이 순간 **VUS 1500개가 동시에 MISS를 맞으면서 DB에 한꺼번에 쿼리가 몰리는 현상**이 발생.

```
t=0~59초: 캐시 HIT → 정상 (DB 요청 0)
t=60초:  캐시 만료 → 1500개 동시 MISS → DB에 1500개 쿼리 폭탄
          └ HikariCP 풀 소진 → 대기 중인 요청들 타임아웃
t=60+수초: 캐시 재생성 완료 → 정상 복귀
```

이 현상이 **1분마다 반복**되면서 평균 응답시간과 실패율이 모두 악화.
TTL을 5분에서 60초로 줄이면서 Stampede 발생 빈도가 5배가 된 구조적 부작용.

---

### 고민 과정 — Stampede 방지 전략 비교

| 전략 | Stampede 방지 | 수요 기반 | 응답 지연 | 복잡도 | 이 프로젝트 적합성 |
|---|:---:|:---:|:---:|:---:|---|
| TTL Jitter | 부분적 | ✓ | 없음 | 낮음 | 단일 키 문제엔 부적합 |
| Distributed Lock (SETNX) | ✓ | ✓ | 있음 | 중간 | 락 대기 요청에 지연 발생 |
| Request Coalescing (Single-flight) | ✓ | ✓ | 최소 | 중간 | 적합하지만 캐시 비는 순간 존재 |
| Probabilistic Early Expiration (XFetch) | ✓ | ✓ | 없음 | 높음 | 메타데이터 관리 복잡 |
| **Stale-While-Revalidate** | **✓** | **✓** | **없음** | **중간** | **최적** |
| Background Refresh (스케줄러) | ✓ | ✗ | 없음 | 낮음 | 수요 없어도 주기적 DB 호출 |
| Two-tier Cache (L1+L2) | ✓ | ✓ | 없음 | 중간 | 단일 서버라 효과 제한적 |
| Event-driven (Kafka) | ✓ | ✓ | 없음 | 높음 | 이 규모엔 오버엔지니어링 |

#### 스케줄러 vs SWR — 왜 SWR인가

스케줄러(`@Scheduled`로 50초마다 캐시 pre-warm)도 Stampede를 원천 차단할 수 있지만,
**수요와 무관하게 돌아가는 구조적 낭비**가 있다:

- 새벽 3시에 아무도 접속하지 않아도 50초마다 DB 쿼리 실행
- 캐싱의 철학("자주 쓰는 것을 빠르게")과 어긋남

SWR은 **요청이 들어올 때만 "갱신이 필요하면" 비동기로 갱신**하는 방식이라 수요 기반으로 동작하면서 Stampede도 방지한다.

---

### 구현 — Stale-While-Revalidate

#### 핵심 아이디어

```
데이터 키 (post:public:page:0)      → 실제 게시글 데이터 (hard TTL: 70초)
신호 키  (post:public:page:0:stale) → 갱신 필요 여부 플래그 (soft TTL: 50초)
```

신호 키가 만료되면 "이제 갱신할 때가 됐다"는 신호. 이때 요청이 들어오면:
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

#### 중복 갱신 방지

`refreshAsync()` 진입 시 **첫 줄에서 신호 키를 즉시 재설정**하는 것이 핵심.
100개 요청이 동시에 "stale" 상태를 감지해도, 첫 번째 요청이 비동기 작업 시작 직후 신호 키를 복구하면
그 사이 들어온 다른 요청들은 "stale 아님" 으로 판정되어 중복 트리거가 차단된다.

```java
@Async("cacheTaskExecutor")
@Transactional(readOnly = true)
public void refreshAsync(String cacheKey, Pageable pageable) {
    // 중복 갱신 방지 — DB 조회 전에 신호 키부터 재설정
    redisService.setValueWithExpire(cacheKey + STALE_SUFFIX, "1", STALE_TTL);

    fetchAndStore(cacheKey, pageable);
}
```

#### 설계 판단 — PostCacheService 별도 빈 분리

`@Async`, `@Transactional`은 Spring AOP 프록시 기반이라 **같은 클래스 내부 호출 시 적용되지 않는 한계**가 있다.
캐시 관리 책임을 `PostCacheService`로 분리해 프록시가 정상 동작하도록 했고,
동시에 캐시 로직과 비즈니스 로직의 단일 책임 원칙(SRP)도 지켰다.

전용 스레드 풀(`cacheTaskExecutor`, core=2/max=4)로 격리해 RSS 수집, 메일 발송 등 다른 비동기 작업과 스레드 경합이 생기지 않도록 분리.

#### 변경 파일

| 파일 | 변경 내용 |
|---|---|
| `RedisKeyConstants.java` | `STALE_SUFFIX` 상수 추가 |
| `AsyncConfig.java` | `cacheTaskExecutor` 전용 풀 빈 추가 |
| `PostCacheService.java` | 신규 생성 — SWR 로직 (`get`, `isStale`, `refreshSync`, `refreshAsync`, `evict`) |
| `PostServiceImpl.java` | `readAllCached()` 를 SWR 기반으로 교체, `save()` 는 `evict()` 호출 |

---

### k6 성능 테스트 결과 (EC2 T3.Small, VUS 1500)

| 지표 | TTL 60초 (Stampede) | SWR 적용 후 | 변화 |
|---|---|---|---|
| P95 응답시간 | 5.36s | **1.35s** | **75% 감소** |
| 평균 응답시간 | 2.20s | **680ms** | 69% 감소 |
| 실패율 | 4.10% | **0.51%** | **87% 감소** |
| RPS | 260/s | **496/s** | **1.9배 증가** |
| 총 처리 요청 | 72,535 | 150,041 | 2배 이상 |

RPS가 거의 2배가 된 것이 핵심 지표.
"빨라졌다"가 아니라 **같은 자원으로 더 많은 요청을 처리**하고 있다는 의미.
Stampede 구간에 모든 스레드가 대기로 막혀있던 시간이 사라지면서 처리량 자체가 증가.

#### VUS 300 결과 (정상 부하 구간)

| 지표 | 값 |
|---|---|
| P95 응답시간 | **102ms** |
| 평균 응답시간 | 32ms |
| 실패율 | 0.03% |

정상 부하 구간에서는 사실상 모든 요청이 캐시 HIT로 처리되어 Redis 조회 + 네트워크 오버헤드 수준의 응답 속도를 보여준다.

---

### 한계와 남은 리스크

#### 남은 0.51% 실패 원인

VUS 1500에서 `max=30.42s`가 관측됨. 30초는 전형적인 타임아웃 경계값.

| 원인 | 설명 | 대응 |
|---|---|---|
| Cold start | 테스트 시작 직후 캐시가 비어있어 `refreshSync()` 경로 진입 | 일회성이라 허용 |
| 글 작성 evict 직후 | `save()` → `evict()` → 다음 요청이 MISS | 기능상 불가피 |

두 케이스 모두 구조상 일회성이고 즉시 복구되므로, VUS 1500 부하에서 0.51%는 **허용 가능한 수준** (목표 < 1%의 절반).

#### 다음 개선 후보

트래픽이 더 커지면 고려할 사항:
- **Cold start 대응**: 앱 시작 시 `@PostConstruct` 로 초기 캐시 워밍 1회 실행
- **Two-tier 캐시**: 서버 다중화 시 Caffeine L1 + Redis L2 구조로 응답 시간 추가 단축

---

### 학습 내용 (Reflection)

#### 캐싱 자체가 또 다른 병목을 만들 수 있다

이번 경험에서 가장 크게 바뀐 관점이다.
처음에는 "DB가 느리니 캐시로 해결"이라는 단순한 구도로 접근했지만, 캐시는 **만료 시점에 DB 부하를 집중시키는 구조적 함정**을 동반한다는 것을 직접 겪었다.
TTL을 줄이면 부작용이 오히려 심해지는 반직관적 현상도 데이터로 확인했다.
앞으로는 캐싱을 도입할 때 "캐시 만료 시의 동작"까지 설계 단계에서 함께 고려하게 될 것이다.

#### "문제가 발생하지 않았으니 괜찮다"는 잘못된 기준

초기 TTL 60초 구현 시 "Stampede가 발생할 수 있다는 건 알지만 현재 규모에선 감당 가능"이라는 판단을 내릴 뻔했다.
그러나 VUS 1500이라는 목표 트래픽을 설정한 순간부터, **발생할 가능성이 있는 문제는 미리 대비하는 것이 목표 자체의 일관성**이라는 점을 명확히 인식했다.
포트폴리오 관점에서도 "문제를 예상하고 선제 대응한 흐름"이 더 강한 서사가 된다.

#### 여러 대안을 비교한 후 선택하는 과정이 설계 역량

TTL Jitter, 분산 락, Request Coalescing, XFetch, SWR, 스케줄러, Two-tier Cache, Event-driven 8가지 대안을 비교하면서
각 방식이 **해결하는 병목이 무엇인지, 어떤 트레이드오프를 수반하는지**를 정리했다.
단순히 "가장 유명한 방법"이나 "가장 간단한 방법"이 아닌, **현재 서비스 특성과 규모에 맞는 판단 기준**을 세우는 훈련이 됐다.

SWR 선택 근거를 한 줄로 정리하면:
> 수요 기반으로 동작하면서(스케줄러 단점 해소), 캐시가 비는 순간 자체를 제거해 Stampede를 원천 차단하고(분산 락 단점 해소), 응답 지연 없이(모든 사용자가 즉시 응답 수신) 이 프로젝트 규모에 적정한 구현 복잡도를 갖는다.
