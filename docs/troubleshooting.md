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
post 테이블 조회에서 `type = index` (인덱스 풀스캔)가 발생하는 것을 확인.
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
결과적으로 인덱스 풀스캔(`type = index`)이 발생함.

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

### SQL 인덱스 안티패턴 (학습 내용)

| 안티패턴 | 이유 | 대안 |
|---|---|---|
| `OR` 조건 | 두 인덱스 동시 사용 불가 | `UNION ALL` |
| `IS NOT NULL` | 선택도 낮으면 옵티마이저가 풀스캔 선택 | 명시적 값 컬럼 |
| 함수 적용 `YEAR(col)` | 인덱스 무효화 | 범위 조건으로 변경 |
| `LIKE '%keyword%'` | 앞에 % 있으면 인덱스 무효 | Full-Text Index |
