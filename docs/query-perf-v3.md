# 게시글 목록 조회 API 성능 개선 3라운드 — 로그인 경로 최적화

> 2라운드(query-perf-v2.md)에서 비로그인 경로(p95 31.62s → 514ms)를 해결했다.  
> 3라운드는 **로그인 사용자 조회 경로**의 구조적 문제를 분석하고 개선한다.  
> 핵심 문제: 두 스트림(공개 + 구독 비공개)을 병합하기 위해 DB에서 **최대 100,000행**을 가져온 뒤 Java에서 버리는 구조.

---

## 측정 환경

| 항목 | 값 |
|------|-----|
| 인스턴스 | EC2 T3.Small (2 vCPU, 2GB RAM) |
| DB | AWS RDS (MySQL 8, 별도 인스턴스) |
| 데이터셋 | Post 1,000만 건 |
| 테스트 도구 | k6 |
| 테스트 대상 | `GET /api/posts?page=N&size=10` (로그인 사용자) |
| 부하 | VUS 100 |

> EC2 메모리 제약(2GB)으로 page=10000은 서버 다운으로 측정 불가. page=10 / 100 / 1000 기준으로 측정한다.  
> (C-5 적용 후에는 힙 적재량이 줄어 page=10000도 측정 가능해졌다. 다만 **베이스라인 값이 없으므로 page=10000은 베이스라인 대비 개선폭을 산출하지 않고**, C-4 대비 비교로만 다룬다.)

---

## 성능 목표

| 지표 | 목표값 |
|------|--------|
| p(95) 응답시간 | < 3s |
| 에러율 | < 1% |

---

## 베이스라인 — 개선 전

---

### EC2 환경 — page=10 (OFFSET 90)

> fetchSize = 90 + 10 = 100 — 상대적으로 부하 낮음

| 지표 | 값 |
|------|-----|
| avg | 1.48s |
| med | 1.06s |
| p(90) | 2.96s |
| **p(95)** | **4.59s** |
| max | 18.34s |
| RPS | 33.43/s |
| 에러율 | 0.00% |
| 전송 데이터 | 1.9 GB (6.8 MB/s) |

> 에러는 없지만 p(95) **4.59s**로 목표(3s) 미달.  
> 전송 데이터 1.9GB = 요청당 약 213KB — content(LONGTEXT) 포함 10행의 무게.

---

### EC2 환경 — page=100 (OFFSET 990)

> fetchSize = 990 + 10 = 1,000 — 공개/비공개 각 1,000행 full fetch

| 지표 | 값 |
|------|-----|
| avg | 17.7s |
| med | 20.86s |
| p(90) | 23.4s |
| **p(95)** | **23.95s** |
| max | 30.87s |
| RPS | 4.60/s |
| **에러율** | **1.46%** |

> page=100에서 이미 p(95) **23.95s**, 에러 1.46% 발생.  
> fetchSize=1,000이면 공개/비공개 각 1,000행 LONGTEXT full fetch → EC2 2GB RAM에서 빠르게 힙 압박.

---

### EC2 환경 — page=1000 (OFFSET 9,990)

> fetchSize = 9,990 + 10 = 10,000 — 공개/비공개 각 10,000행 full fetch

| 지표 | 값 |
|------|-----|
| avg | 32.13s |
| med | 33.52s |
| p(90) | 48.22s |
| **p(95)** | **59.58s** |
| max | 1m 1s |
| RPS | 2.44/s |
| **에러율** | **85.30%** |
| 전송 데이터 | 436 kB (1.4 kB/s) |

> page=1000에서 이미 **85% 실패**. page가 깊어질수록 fetchSize가 커지고  
> 100K LONGTEXT 행이 힙을 점유 → HikariCP 커넥션 고갈 → EC2 서버 다운.

---

## 원인 분석

로그인 사용자는 공개 글 + 구독 비공개 글을 날짜 순으로 합쳐서 보여줘야 한다.  
QueryDSL은 UNION ALL을 지원하지 않아 쿼리 2개를 따로 실행하고 Java에서 병합한다.

**병합을 위한 현재 fetchSize 계산:**

```java
// fetchPageable(): 해당 페이지까지 존재할 수 있는 최대 행 수
int fetchSize = (int) pageable.getOffset() + pageable.getPageSize();
// page=10000, size=10 → fetchSize = 99990 + 10 = 100,000
// page=1000,  size=10 → fetchSize = 9990  + 10 = 10,000
return PageRequest.of(0, fetchSize);
```

**실제 발생하는 쿼리 흐름 (page=10000 기준):**

```sql
-- Q1: 공개글 ID 100,000개 조회
SELECT post.id FROM post
WHERE post.is_public = 1
ORDER BY post.created_at DESC
LIMIT 100000 OFFSET 0;

-- Q2: 공개글 상세 100,000행 — IN 100,000개 + LONGTEXT(HTML) 전송
SELECT post.id, post.title, post.content,
       COALESCE(u.nickname, ea.name), post.created_at, post.like_count
FROM post
LEFT JOIN users u ON post.user_id = u.user_id
LEFT JOIN external_author ea ON post.external_author_id = ea.id
LEFT JOIN feed ON post.feed_id = feed.id
WHERE post.id IN (?, ?, ...  /* 100,000개 */)
ORDER BY post.created_at DESC;

-- Q3: 비공개글 ID 100,000개 조회
SELECT post.id FROM post
WHERE post.is_public = 0 AND post.feed_id IN (?, ...)
ORDER BY post.created_at DESC
LIMIT 100000 OFFSET 0;

-- Q4: 비공개글 상세 100,000행 — 동일하게 IN 100,000개 + LONGTEXT
SELECT post.id, post.title, post.content, ...
FROM post LEFT JOIN ...
WHERE post.id IN (?, ...  /* 100,000개 */);

-- Q5: 비공개글 count — 전체 풀스캔 후 Java에서 Math.min()
SELECT COUNT(post.id) FROM post
WHERE post.is_public = 0 AND post.feed_id IN (?...);
```

**병목 요인:**

| 요인 | 세부 내용 |
|------|----------|
| **IN 100,000개** | Q2, Q4 각각 IN절에 10만 개 바인딩 파라미터 → 쿼리 파싱 + 플랜 캐시 미스 |
| **LONGTEXT 100,000행 전송** | content 컬럼은 RSS HTML 본문 포함 → DB→App 수백 MB 전송 후 99,990행 즉시 버림 |
| **Java 힙 200,000개 객체** | PostDetailResponse(title, content, author 등) 200,000개 생성 후 GC |
| **비공개 count 풀스캔** | getCappedTotalCount()가 전체 COUNT 실행 후 Java에서 min(result, 100000) |
| **feed_id 인덱스 없음** | Q3에 `(feed_id, created_at)` 인덱스 없어 비공개글 쿼리가 테이블 스캔 |

---

## EXPLAIN ANALYZE — 변경 전후 쿼리 실행 비교

쿼리 구조 변경 전후를 EXPLAIN ANALYZE로 실측한 결과:

| 쿼리 | 변경 전 | 변경 후 | 개선 |
|------|--------|--------|------|
| 공개글 조회 | **1,647ms** | **72.9ms** | **22.6배** |
| 비공개글 조회 | **92.5ms** | **16.9ms** | **5.5배** |
| 최종 상세 10개 | (없음) | **< 5ms*** | 신규 |
| 비공개 count | 4.36ms | 3.73ms | 미미 |
| **합계** | **~1,744ms** | **~95ms** | **~18배** |

> *최종 상세 조회는 Java merge로 확정된 10개 ID로 PRIMARY KEY 직접 조회 → 사실상 < 5ms.  
> EXPLAIN ANALYZE 시뮬레이션은 OFFSET 방식으로 ID를 구해 52ms로 측정됐으나 실제 코드와 다름.

### 변경 전 핵심 병목 — `loops=100000`

```
-> Single-row index lookup on post using PRIMARY (id=`<subquery2>`.id)
   (actual time=0.00394..0.00398 rows=1 loops=100000)
```

100,000번의 PRIMARY KEY 랜덤 조회 + LEFT JOIN(users, external_author, feed) × 100,000.  
여기에 Temporary table 생성 + Sort가 더해져 **공개글 조회 1건에 1,647ms** 소요.

### 변경 후 — `loops=1`

```
-> Covering index lookup on post using idx_post_is_public_created_at (is_public=1)
   (actual time=0.497..47.4 rows=100000 loops=1)
```

인덱스 순차 스캔 1번. 테이블 접근 0번. JOIN 0번. **72.9ms**로 완료.  
`idx_post_is_public_created_at (is_public, created_at DESC)` 리프에 PK(id)가 자동 포함되어  
`SELECT id, created_at` 쿼리가 커버링 인덱스로 처리됨.

---

## 개선 항목

| ID | 분류 | 설명 | 예상 효과 |
|----|------|------|----------|
| C-1 | 쿼리 구조 | two-phase fetch: (id, createdAt)만 먼저 병합 후 최종 10개만 상세 조회 | IN 100K → 10, LONGTEXT 전송 제거 |
| C-2 | 인덱스 | `(feed_id, created_at DESC)` 인덱스 추가 | 비공개글 테이블 스캔 제거 |
| C-3 | count 쿼리 | getCappedTotalCount → LIMIT 서브쿼리 방식 | 비공개글 count 풀스캔 차단 |

---

## C-1: two-phase fetch

### 배경

`getPostList(fetchPageable, condition)` 호출 시:
- **1단계** (ID 조회): `LIMIT 100000 OFFSET 0` → 100,000 ID 수집
- **2단계** (상세 조회): `WHERE id IN (100,000개)` → 100,000행 full fetch (content 포함)
- **Java merge**: 200,000개 정렬 → 10개 선택 → 나머지 199,990개 GC

병합에 필요한 건 `(id, createdAt)` 두 컬럼뿐인데 content까지 모두 가져오고 있다.

### 변경 내용

**변경 전 — `getPostList`로 상세까지 100,000행:**

```java
List<PostDetailResponse> publicPosts  = getPostList(fetch, qPost.isPublic.isTrue());
List<PostDetailResponse> privatePosts = feedIds.isEmpty() ? List.of()
        : getPostList(fetch, subscribedPrivateCondition(feedIds));

private Page<PostDetailResponse> mergeAndPage(
        List<PostDetailResponse> list1,
        List<PostDetailResponse> list2, ...) {
    List<PostDetailResponse> merged = Stream.concat(list1.stream(), list2.stream())
            .sorted(Comparator.comparing(PostDetailResponse::getCreatedAt).reversed())
            .collect(Collectors.toList());
    int start = (int) pageable.getOffset();
    int end   = Math.min(start + pageable.getPageSize(), merged.size());
    return new PageImpl<>(merged.subList(start, end), pageable, total);
}
```

**변경 후 — `fetchIdAndDate`로 경량 조회, 최종 10개만 상세:**

```java
// 새 메서드: (id, createdAt)만 조회 — 커버링 인덱스, LONGTEXT 없음
private List<Tuple> fetchIdAndDate(Pageable pageable, BooleanExpression condition) {
    return queryFactory
            .select(qPost.id, qPost.createdAt)
            .from(qPost)
            .where(condition)
            .orderBy(qPost.createdAt.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
}

// 새 메서드: 최종 pageSize(10개)만 상세 조회
private List<PostDetailResponse> fetchDetailsByIds(List<Long> ids) {
    if (ids.isEmpty()) return List.of();
    return queryFactory
            .select(postDetailsProjection())
            .from(qPost)
            .leftJoin(qPost.author, qUser)
            .leftJoin(qPost.externalAuthor, qExternalAuthor)
            .leftJoin(qPost.feed, qFeed)
            .where(qPost.id.in(ids))
            .orderBy(qPost.createdAt.desc())
            .fetch();
}

// mergeAndPageTuples: Tuple 병합 → ID 10개 추출 → 상세 1회
private Page<PostDetailResponse> mergeAndPageTuples(
        List<Tuple> list1, List<Tuple> list2, long total, Pageable pageable) {
    List<Long> pageIds = Stream.concat(list1.stream(), list2.stream())
            .sorted(Comparator.comparing(
                    (Tuple t) -> t.get(qPost.createdAt),
                    Comparator.nullsLast(Comparator.reverseOrder())))
            .skip(pageable.getOffset())
            .limit(pageable.getPageSize())
            .map(t -> t.get(qPost.id))
            .collect(Collectors.toList());
    return new PageImpl<>(fetchDetailsByIds(pageIds), pageable, total);
}

// findAllLoggedIn 호출부
List<Tuple> publicPosts  = fetchIdAndDate(fetch, qPost.isPublic.isTrue());
List<Tuple> privatePosts = feedIds.isEmpty() ? List.of()
        : fetchIdAndDate(fetch, subscribedPrivateCondition(feedIds));
```

**변경 후 발생 쿼리:**

```sql
-- Q1: 공개글 (id, created_at)만 — 커버링 인덱스, LONGTEXT 없음
SELECT post.id, post.created_at FROM post
WHERE post.is_public = 1
ORDER BY post.created_at DESC
LIMIT 100000 OFFSET 0;

-- Q2: 비공개글 (id, created_at)만
SELECT post.id, post.created_at FROM post
WHERE post.is_public = 0 AND post.feed_id IN (?, ...)
ORDER BY post.created_at DESC
LIMIT 100000 OFFSET 0;

-- (Java: Tuple 200,000개 병합 정렬 → ID 10개 추출)

-- Q3: 최종 상세 조회 — IN 10개
SELECT post.id, post.title, post.content,
       COALESCE(u.nickname, ea.name), post.created_at, post.like_count
FROM post
LEFT JOIN users u ON post.user_id = u.user_id
LEFT JOIN external_author ea ON post.external_author_id = ea.id
LEFT JOIN feed ON post.feed_id = feed.id
WHERE post.id IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ORDER BY post.created_at DESC;
```

**변경 전후 비교:**

| 항목 | 변경 전 | 변경 후 |
|------|--------|--------|
| 상세 조회 IN 크기 | **100,000개** | **10개** |
| DB→App 전송 (상세) | content 포함 수백 MB | 10행만 |
| Java 힙 | PostDetailResponse 200,000개 (content 포함) | Tuple 200,000개 (id+date만, ~8MB) |
| 버려지는 행 | 199,990행 full data | 0행 |

### 테스트 결과 (C-1+C-2+C-3 통합 적용, page=10)

| 지표 | 베이스라인 | 개선 후 | 변화 |
|------|-----------|--------|------|
| avg | 1.48s | **820ms** | 45% 감소 |
| med | 1.06s | **567ms** | 47% 감소 |
| p(90) | 2.96s | **1.57s** | 47% 감소 |
| **p(95)** | **4.59s** | **2.49s** | **46% 감소, 목표(3s) 달성** |
| RPS | 33.43/s | **44.52/s** | 33% 증가 |
| 에러율 | 0.00% | **0.00%** | — |
| 전송 데이터 | 1.9 GB | 2.5 GB | (요청 수 증가) |

---

## C-2: feed_id 인덱스 추가

### 배경

비공개글 쿼리:
```sql
WHERE post.is_public = 0 AND post.feed_id IN (1,2,...,52)
ORDER BY post.created_at DESC
LIMIT 100000 OFFSET 0
```

현재 인덱스 `(is_public, created_at DESC)`는 `is_public=0` 조건은 타지만  
`feed_id IN (...)` 필터를 위해 비공개 글 전체를 스캔한 뒤 필터링한다.  
`(feed_id, created_at)` 인덱스가 있으면 구독 피드별로 정렬된 행에 바로 접근한다.

### 변경 내용

**`Post.java`**

```java
// 변경 전
@Table(indexes = @Index(
    name = "idx_post_is_public_created_at",
    columnList = "is_public, created_at DESC"))

// 변경 후
@Table(indexes = {
    @Index(name = "idx_post_is_public_created_at",
           columnList = "is_public, created_at DESC"),
    @Index(name = "idx_post_feed_created_at",
           columnList = "feed_id, created_at DESC")   // 비공개 구독글 스캔용 추가
})
```

> InnoDB 세컨더리 인덱스 리프에 PK(id)가 자동 포함되므로  
> `SELECT id, created_at WHERE feed_id IN (...)` 도 이 인덱스로 커버링.

### 테스트 결과 (C-1+C-2+C-3 통합 적용, page=100)

| 지표 | 베이스라인 | 개선 후 | 변화 |
|------|-----------|--------|------|
| avg | 17.7s | **1.49s** | 91% 감소 |
| med | 20.86s | **1.13s** | 95% 감소 |
| p(90) | 23.4s | **3.07s** | 87% 감소 |
| **p(95)** | **23.95s** | **4.52s** | **81% 감소** |
| RPS | 4.60/s | **33.05/s** | 7.2배 증가 |
| 에러율 | **1.46%** | **0.00%** | 에러 제거 |

---

## C-3: getCappedTotalCount — LIMIT 서브쿼리 방식

### 배경

```java
// 현재: 전체 COUNT 후 Java에서 min
Long count = queryFactory.select(qPost.count()).from(qPost)
        .where(condition).fetchOne();
return Math.min(count != null ? count : 0L, 100_000L);
// 구독 피드 글이 50만 건이면 50만 건 전부 스캔 후 Java에서 잘라냄
```

비로그인 `countPublic()`에서 이미 검증한 LIMIT 서브쿼리 방식을 동일하게 적용한다.

### 변경 내용

**변경 전 — QueryDSL COUNT (전체 스캔):**
```java
private long getCappedTotalCount(BooleanExpression finalCondition) {
    Long count = queryFactory
            .select(qPost.count())
            .from(qPost)
            .where(finalCondition)
            .fetchOne();
    return Math.min(count != null ? count : 0L, 100_000L);
}
```

**변경 후 — JDBC LIMIT 서브쿼리 (100K 도달 즉시 중단):**
```java
private long countCappedPrivate(List<Long> feedIds) {
    if (feedIds.isEmpty()) return 0L;
    String placeholders = String.join(",", Collections.nCopies(feedIds.size(), "?"));
    String sql = "SELECT COUNT(*) FROM " +
            "(SELECT 1 FROM post WHERE is_public = 0 " +
            "AND feed_id IN (" + placeholders + ") LIMIT 100000) t";
    Long count = jdbcTemplate.queryForObject(sql, Long.class, feedIds.toArray());
    return count != null ? count : 0L;
}
```

**SQL 비교:**
```sql
-- 변경 전: 매칭 행 전부 COUNT
SELECT COUNT(post.id) FROM post
WHERE post.is_public = 0 AND post.feed_id IN (?, ...);

-- 변경 후: 100,000건 도달 즉시 중단
SELECT COUNT(*) FROM (
    SELECT 1 FROM post
    WHERE is_public = 0 AND feed_id IN (?, ...)
    LIMIT 100000
) t;
```

### 테스트 결과 (C-1+C-2+C-3 통합 적용, page=1000)

| 지표 | 베이스라인 | 개선 후 | 변화 |
|------|-----------|--------|------|
| avg | 32.13s | **1.67s** | 95% 감소 |
| med | 33.52s | **1.73s** | 95% 감소 |
| p(90) | 48.22s | **3.0s** | 94% 감소 |
| **p(95)** | **59.58s** | **3.94s** | **93% 감소** / 목표(3s) 미달 |
| RPS | 2.44/s | **30.89/s** | 12.7배 증가 |
| 에러율 | **85.30%** | **0.00%** | **서버 다운 → 완전 안정화** |

---

---

## C-4: getSubscribedPrivateFeedIds + countCappedPrivate Redis 캐싱

### 배경

C-1~C-3 적용 후에도 로그인 사용자 요청마다 5개 쿼리가 고정으로 실행된다.

```
① getSubscribedPrivateFeedIds  ← 매 요청 DB 조회
② fetchIdAndDate(public)
③ fetchIdAndDate(private)
④ countCappedPrivate            ← 매 요청 DB 조회
⑤ fetchDetailsByIds(10개)
```

① 구독 피드 목록은 사용자가 구독/해지할 때만 변한다.  
④ 비공개글 수는 RSS 수집(5분 주기)으로만 변한다.  
두 값 모두 변경 빈도가 매우 낮으므로 매 요청마다 DB를 찌르는 것은 낭비다.

### 방안 비교

| 방안 | 미채택 이유 |
|------|------------|
| SWR 패턴(공개 count와 동일) | 공개 count는 전역 단일 값이라 SWR 효과가 크지만, 사용자별 값은 구독 시점에 evict 가능하므로 단순 TTL로 충분. SWR 구현 비용 대비 효과 낮음<br>※ 구독 등록 시 evict는 구현했으나 **구독 해지 기능 자체가 미구현**이라 해지 경로의 evict는 해당 없음. 해지 구현 시 동일 evict 추가 필요 |
| Caffeine 로컬 캐시 | 서버 2대 이상 환경에서 각 인스턴스가 독립된 캐시를 보유 → 사용자가 구독 변경 시 일부 서버는 stale 데이터 유지. Redis는 분산 환경에서도 일관된 evict 가능 |
| **Redis TTL 5분 + 구독 변경 시 evict** | 구현이 단순하고 변경 빈도(RSS 5분 주기)와 TTL이 자연스럽게 맞음 → **채택** |

### 변경 내용

**`RedisKeyConstants.java`** — 캐시 키 추가:

```java
public static final String PRIVATE_FEED_IDS   = "post:private:feed_ids:";
public static final String PRIVATE_POST_COUNT = "post:private:count:";
```

**`PostCacheService.java`** — 캐싱 메서드 추가:

```java
private static final Duration PRIVATE_CACHE_TTL = Duration.ofMinutes(5);

public List<Long> getCachedPrivateFeedIds(Long userId) {
    String key = RedisKeyConstants.PRIVATE_FEED_IDS + userId;
    Object cached = redisService.getValue(key);
    if (cached instanceof List<?> list) return (List<Long>) list;

    List<Long> feedIds = postCustomRepository.getSubscribedPrivateFeedIds(userId);
    redisService.setValueWithExpire(key, feedIds, PRIVATE_CACHE_TTL);
    return feedIds;
}

public long getCachedPrivateCount(Long userId, List<Long> feedIds) {
    if (feedIds.isEmpty()) return 0L;
    String key = RedisKeyConstants.PRIVATE_POST_COUNT + userId;
    Object cached = redisService.getValue(key);
    if (cached instanceof Number n) return n.longValue();

    long count = postCustomRepository.countCappedPrivate(feedIds);
    redisService.setValueWithExpire(key, count, PRIVATE_CACHE_TTL);
    return count;
}

// feedIds가 바뀌면 그 feedIds로 계산된 count도 무효화
public void evictPrivateFeedIds(Long userId) {
    redisService.deleteValue(RedisKeyConstants.PRIVATE_FEED_IDS + userId);
    redisService.deleteValue(RedisKeyConstants.PRIVATE_POST_COUNT + userId);
}
```

**`PostServiceImpl.java`** — 서비스 레이어에서 캐시 조회 후 Repository에 전달:

```java
// 변경 전
long publicCount = postCacheService.getCachedPublicCount();
return postCustomRepository.findAllLoggedIn(pageable, publicCount);

// 변경 후
TokenBody tokenBody = (TokenBody) auth.getPrincipal();
Long userId = tokenBody.getMemberId();

long publicCount   = postCacheService.getCachedPublicCount();
List<Long> feedIds = postCacheService.getCachedPrivateFeedIds(userId);
long privateCount  = postCacheService.getCachedPrivateCount(userId, feedIds);

return postCustomRepository.findAllLoggedIn(pageable, publicCount, feedIds, privateCount);
```

> Repository는 전달받은 값으로 쿼리만 실행. 캐시 결정은 Service 레이어에서만 이루어짐.

**`FeedSubscriptionService.java`** — 구독 등록 시 캐시 evict:

```java
public void registerFeedSubscription(FeedSubscription subscription) {
    subscriptionRepository.save(subscription);
    postCacheService.evictPrivateFeedIds(subscription.getUser().getId());
}
```

### 변경 후 요청당 쿼리 수

| 경우 | 변경 전 | 변경 후 |
|------|--------|--------|
| 캐시 콜드 (첫 요청) | 5쿼리 | 5쿼리 |
| 캐시 웜 (이후 요청) | 5쿼리 | **3쿼리** |

### 테스트 결과

**page=10 비교**

| 지표 | C-1~C-3 | C-4 추가 | 변화 |
|------|--------|---------|------|
| avg | 820ms | **708ms** | 14% 감소 |
| p(90) | 1.57s | **1.36s** | 13% 감소 |
| **p(95)** | **2.49s** | **1.86s** | **25% 감소** |
| RPS | 44.52/s | **47.43/s** | 6% 증가 |

**page=100 비교**

| 지표 | C-1~C-3 | C-4 추가 | 변화 |
|------|--------|---------|------|
| avg | 1.49s | **1.18s** | 21% 감소 |
| p(90) | 3.07s | **2.31s** | 25% 감소 |
| **p(95)** | **4.52s** | **3.33s** | **26% 감소** |
| RPS | 33.05/s | **37.76/s** | 14% 증가 |

**page=1000 비교**

| 지표 | C-1~C-3 | C-4 추가 | 변화 |
|------|--------|---------|------|
| avg | 1.67s | **1.70s** | — (노이즈 수준) |
| **p(95)** | **3.94s** | **4.27s** | — |
| RPS | 30.89/s | **30.49/s** | — |

> page=1000은 캐싱 효과가 없다. 병목이 ①④ 제거가 아닌  
> ②③의 fetchIdAndDate(각 10,000행 scan)에 있기 때문이다.  
> 이 병목은 Keyset Pagination 또는 인프라 업그레이드 없이는 개선 불가.

> **측정 라운드 주의 — 위 표는 1차 측정값이다.**  
> C-4는 이후 C-5와 동일 조건에서 한 번 더 측정했고, 그 값은 상당히 다르다
> (page=1000 기준 p(95) **4.27s → 2.43s**, page=10 **1.86s → 1.59s**).
> 2차 측정값은 [C-5 섹션의 비교표](#c-5-union-all-이관--t3small-적용-결과)에 있다.  
> 두 라운드 간 차이의 원인은 규명하지 못했다(RDS 버스터블 크레딧 잔량, 캐시 워밍 상태 차이 등이 후보). **재측정 필요.**  
> 따라서 **C-1~C-3 → C-4 비교는 이 표(1차)**를, **C-4 → C-5 비교는 C-5 섹션 표(2차)**를 사용한다.
> 라운드가 다른 값끼리 직접 비교하지 않는다.

---

## 전체 개선 요약

> **아래 표의 `+C-4` 행은 1차 측정, `+C-5` 행은 2차 측정값이다** (위 C-4 섹션의 측정 라운드 주의 참고).
> 라운드가 다르므로 **`+C-4` → `+C-5` 행 간의 차이를 그대로 C-5의 개선 효과로 읽으면 안 된다.**
> 동일 라운드 기준의 C-4 → C-5 비교는 C-5 섹션의 비교표를 참고할 것.

### page=10

| 단계 | 주요 변경 | avg | p(95) | RPS | 에러율 |
|------|---------|-----|-------|-----|--------|
| 베이스라인 | — | 1.48s | 4.59s | 33.43/s | 0% |
| C-1+C-2+C-3 | two-phase fetch + 인덱스 + count 서브쿼리 | 820ms | 2.49s | 44.52/s | 0% |
| +C-4 | feedIds + privateCount Redis 캐싱 | 708ms | 1.86s | 47.43/s | 0% |
| **+C-5** | **UNION ALL DB 이관** | **618ms** | **2.06s** | **37.6/s** | **0%** |

### page=100

| 단계 | 주요 변경 | avg | p(95) | RPS | 에러율 |
|------|---------|-----|-------|-----|--------|
| 베이스라인 | — | 17.7s | 23.95s | 4.60/s | 1.46% |
| C-1+C-2+C-3 | 동일 | 1.49s | 4.52s | 33.05/s | 0% |
| +C-4 | feedIds + privateCount Redis 캐싱 | 1.18s | 3.33s | 37.76/s | 0% |
| **+C-5** | **UNION ALL DB 이관** | **582ms** | **1.51s** | **39.0/s** | **0%** |

### page=1000

| 단계 | 주요 변경 | avg | p(95) | RPS | 에러율 |
|------|---------|-----|-------|-----|--------|
| 베이스라인 | — | 32.13s | 59.58s | 2.44/s | **85.3%** |
| C-1+C-2+C-3 | 동일 | 1.67s | 3.94s | 30.89/s | 0% |
| +C-4 | feedIds + privateCount Redis 캐싱 | 1.70s | 4.27s | 30.49/s | 0% |
| **+C-5** | **UNION ALL DB 이관** | **87ms** | **194ms** | **55.6/s** | **0%** |

### page=10000

| 단계 | 주요 변경 | avg | p(95) | RPS | 에러율 |
|------|---------|-----|-------|-----|--------|
| C-4 | (C-5 적용 전) | 28.6s | 46.1s | 2.2/s | **21.22%** |
| **C-5** | **UNION ALL DB 이관** | **1.63s** | **3.24s** | **24.0/s** | **0%** |

### 목표(p95 < 3s, 에러율 < 1%) 달성 현황 — 최종(C-5) 기준

| 페이지 | p(95) | 에러율 | 판정 |
|--------|-------|--------|------|
| page=10 | 2.06s | 0% | 달성 (단, C-4 대비 퇴행) |
| page=100 | 1.51s | 0% | 달성 |
| page=1000 | 194ms | 0% | 달성 |
| page=10000 | 3.24s | 0% | 응답시간 미달 (에러율만 충족) |

> 중간 단계(C-1~C-3의 3.94s, C-4의 3.33s)는 목표 미달 상태였고, **C-5에 와서야 설계 상한 직전까지 목표를 충족**했다.

---

## I-1: EC2 인스턴스 업그레이드 — 메모리 병목 확인

### 배경

C-4 적용 후에도 page=1000 기준 p95=4.27s. vus=5로 줄이면 p95=337ms로 정상이므로  
**쿼리 자체 비용이 아닌 동시성 부하 처리 능력**이 문제라는 가설을 세우고  
CloudWatch + JVM 모니터링으로 원인을 규명했다.

> 이 섹션의 C-4 기준값(4.27s)은 **1차 측정 라운드** 값이다. 아래 진단·비교는 모두 같은 라운드 안에서 이뤄졌으므로 내부적으로는 일관된다.
> (C-4의 2차 측정값 2.43s와 혼동하지 않도록 주의.)

---

### 진단 1 — RDS는 여유롭다 (CloudWatch, vus=100)

| 지표 | 측정값 | 판정 |
|------|--------|------|
| CPUUtilization | 최대 5.45% | 여유 |
| DatabaseConnections | 최대 22개 (한계 ~80) | 여유 |
| FreeableMemory | 90~121MB | 낮지만 위험 수준 아님 |

**RDS는 병목이 아님. 문제는 애플리케이션 서버.**

---

### 진단 2 — EC2 t3.small JVM 모니터링 (vus=100)

| 지표 | 값 | 의미 |
|------|-----|------|
| EC2 Process CPU | 최대 100% (테스트 내내 지속) | EC2 완전 포화 |
| Load Average [1m] | 최대 18.0 (코어 2개) | 16개 요청이 CPU 대기 중 |
| Minor GC STW 최대 | 275ms | 앱 전체 275ms 동안 멈춤 |
| Minor GC 빈도 | 최대 6.6회/s | Eden 영역 지속 포화 |
| HikariCP Pending | 최대 41개 | 연결 대기 급증 |
| Connection Acquire Time | 600ms | DB 연결 얻는 데만 600ms 소요 |

---

### 근본 원인 — Tuple 20,000개 × vus 100

`fetchPageable()` 계산:

```java
// page=1000, size=10 → fetchSize = 10000 + 10 = 10,010
int fetchSize = (int) pageable.getOffset() + pageable.getPageSize();
```

요청 1건당 메모리 흐름:

```
공개글  (id, createdAt) Tuple 10,010개 → Java 힙 적재
구독글  (id, createdAt) Tuple 10,010개 → Java 힙 적재
Stream.concat().sorted() → skip(10000) → limit(10) → ID 10개만 사용
→ 나머지 20,010개 Tuple → GC 대상
```

vus=100 동시 부하:

```
Tuple 20,020개 × 100 요청 = 2,002,000개 동시 힙 적재
→ Eden 영역 포화 → Minor GC 초당 6.6회
→ GC Stop-the-World 최대 275ms → 스레드 전체 블로킹
→ CPU 100% 지속 → HikariCP Pending 41개 누적
→ Connection Acquire 600ms → 응답 avg 1.26s, p95 4.27s
```

t3.small의 CPU 100%는 **CPU 성능 부족이 아니라 메모리 부족으로 인한 GC 폭발**의 결과.

---

### 인스턴스별 비교 결과 (vus=100, page=1000)

| 항목 | t3.small (2GB) | c7i-flex.large (4GB) | m7i-flex.large (8GB) |
|------|:---:|:---:|:---:|
| EC2 CPU 최대 | 100% **지속** | 67% (순간) | 99% (순간) |
| Load Average 최대 | 18.0 | **4.37** | 18.0 |
| GC STW 최대 | 275ms | **50.9ms** | 275ms |
| Major GC 발생 | O | **없음** | O |
| HikariCP Pending 최대 | 41 | **0** | 41 |
| **p(95)** | 4.27s | **227ms** | 325ms |
| RPS | ~30/s | **~60/s** | ~59/s |

> **c7i(4GB)가 m7i(8GB)보다 p95가 낮은 이유:**  
> c7i는 Compute 최적화 인스턴스(Intel Ice Lake)로 단일 코어 처리 속도가 높다.  
> 4GB RAM은 Tuple 20K × vus100 부하를 GC 없이 흡수하기 충분해 Major GC가 전혀 발생하지 않았다.

### 결론

| 항목 | 판정 |
|------|------|
| RDS CPU | 5% — 병목 아님, 여유 충분 |
| EC2 메모리 | t3.small 2GB → JVM 힙 한계, GC 폭발 |
| 실제 병목 | fetchIdAndDate 2회 → Tuple 20K 힙 적재 |
| 인프라 해결책 | c7i-flex.large(4GB) 이상 권장 |

---

## 다음 개선 방향 — UNION ALL을 DB로 이관

인프라 업그레이드 없이 t3.small급 서버에서도 같은 성능을 얻으려면  
**Java에서 수행하는 Tuple 20K 병합/정렬을 DB로 이관**하면 된다.

**현재 흐름:**

```
DB → EC2: 공개 Tuple 10,010개 + 구독 Tuple 10,010개 (총 20,020개)
EC2: 20,020개 정렬 → skip(10000) → 10개 추출 → 상세 조회
```

**UNION ALL 이관 후:**

```
DB: 두 결과 합쳐 정렬 → EC2에 10개만 전송
EC2: 10개 받아 상세 조회
```

```sql
SELECT id FROM (
  (SELECT id, created_at FROM post
   WHERE is_public = 1 ORDER BY created_at DESC LIMIT 10010)
  UNION ALL
  (SELECT id, created_at FROM post
   WHERE is_public = 0 AND feed_id IN (...)
   ORDER BY created_at DESC LIMIT 10010)
) AS combined
ORDER BY created_at DESC
LIMIT 10 OFFSET 10000;
```

| 항목 | 현재 | UNION ALL 이관 후 |
|------|------|-----------|
| EC2 Tuple 객체 수 | 20,020개 | **10개** |
| EC2 GC 부하 | 6.6회/s | 거의 없음 |
| RDS CPU | 5% | 10~15% 수준 (여유 내) |
| t3.small 안정 여부 | 불안정 | 안정적 가능성 |

> QueryDSL은 UNION ALL 미지원 → `JdbcTemplate` 네이티브 SQL로 구현.  
> 기존 `countPublic()`, `countCappedPrivate()` 패턴과 동일한 방식.

---

### EXPLAIN ANALYZE — 현재 vs UNION ALL 실측 비교

#### 현재 쿼리 1 — 공개글 (id, created_at) 조회

```sql
EXPLAIN ANALYZE
SELECT id, created_at FROM post
WHERE is_public = 1
ORDER BY created_at DESC
LIMIT 10010;
```

```
-> Limit: 10010 row(s)  (actual time=0.707..4.51 rows=10010 loops=1)
    -> Covering index lookup on post using idx_post_is_public_created_at (is_public=1)
       (actual time=0.706..3.16 rows=10010 loops=1)
```

| 항목 | 값 |
|------|-----|
| key | `idx_post_is_public_created_at` |
| type | covering index lookup |
| rows (실제) | 10,010 |
| Extra | Using index (커버링) |
| actual time | **4.51ms** |
| EC2 전달 rows | **10,010** |

---

#### 현재 쿼리 2 — 구독 비공개글 (id, created_at) 조회

```sql
EXPLAIN ANALYZE
SELECT id, created_at FROM post
WHERE is_public = 0 AND feed_id IN (1, 2, 3)
ORDER BY created_at DESC
LIMIT 10010;
```

```
-> Limit: 10010 row(s)  (actual time=1.39..1.4 rows=40 loops=1)
    -> Sort: post.created_at DESC  (actual time=1.39..1.4 rows=40 loops=1)
        -> Filter: (post.is_public = 0)  (actual time=0.0922..0.177 rows=40 loops=1)
            -> Index range scan on post using idx_post_feed_created_at
               over (feed_id=1) OR (feed_id=2) OR (feed_id=3)
               (actual time=0.0908..0.172 rows=40 loops=1)
```

| 항목 | 값 |
|------|-----|
| key | `idx_post_feed_created_at` |
| type | index range scan |
| rows (실제) | 40 (피드 3개 기준) |
| Extra | Using index condition, Using filesort (40행) |
| actual time | **1.4ms** |
| EC2 전달 rows | **40** |

> 구독 피드가 적고 비공개글도 적어 40행만 반환.  
> 피드 수·비공개글 수가 늘어날수록 최대 10,010행까지 증가.

---

#### UNION ALL 쿼리

```sql
EXPLAIN ANALYZE
SELECT id FROM (
  (SELECT id, created_at FROM post WHERE is_public = 1
   ORDER BY created_at DESC LIMIT 10010)
  UNION ALL
  (SELECT id, created_at FROM post WHERE is_public = 0 AND feed_id IN (1, 2, 3)
   ORDER BY created_at DESC LIMIT 10010)
) AS combined
ORDER BY created_at DESC
LIMIT 10 OFFSET 10000;
```

```
-> Limit/Offset: 10/10000 row(s)  (actual time=16.8..16.8 rows=10 loops=1)
    -> Sort: combined.created_at DESC  (actual time=15.8..16.4 rows=10010 loops=1)
        -> Table scan on combined  (actual time=7.21..8.39 rows=10050 loops=1)
            -> Union all materialize  (actual time=7.21..7.21 rows=10050 loops=1)
                -> Limit: 10010 rows  (actual time=0.697..3.69 rows=10010 loops=1)
                    -> Covering index lookup using idx_post_is_public_created_at
                -> Limit: 10010 rows  (actual time=0.172..0.177 rows=40 loops=1)
                    -> Sort + Index range scan using idx_post_feed_created_at
```

| 항목 | 값 |
|------|-----|
| key | 두 인덱스 모두 사용 |
| 임시 테이블 rows | 10,050 (materialize) |
| Extra | Union all materialize, Using filesort (10,050행) |
| actual time | **16.8ms** |
| EC2 전달 rows | **10** |

---

### 비용 교환 분석 (page=1000 기준)

| 항목 | 현재 (쿼리 2개) | UNION ALL | 변화 |
|------|--------------|-----------|------|
| DB actual time | 5.91ms | **16.8ms** | +10.9ms |
| EC2 전달 Tuple | 10,050개 | **10개** | 1/1000 |
| EC2 Stream 정렬 | 10,050개 정렬 | **없음** | 제거 |
| EC2 GC STW | 최대 275ms | **없음** | 제거 |
| RDS CPU 사용 | 5% | ~10~15% | 여유 내 |

**판정: UNION ALL 채택**

DB에서 +10.9ms를 쓰는 대신 EC2의 GC Stop-the-World(최대 275ms)를 제거한다.  
RDS CPU가 5%로 여유롭기 때문에 +10.9ms는 충분히 수용 가능하다.

> 단, `Union all materialize`로 MySQL이 10,050행짜리 임시 테이블을 생성한다.  
> 현재 RDS 여유 수준에서는 문제 없으나, 구독 피드 수가 매우 많아지면 재측정 필요.

---

#### 구독 피드 52개 조건 재측정 (feed_id IN 1~52)

```
-> Limit/Offset: 10/10000 row(s)  (actual time=14.2..14.2 rows=10 loops=1)
    -> Sort: combined.created_at DESC, limit input to 10010 row(s) per chunk
       (actual time=13.2..13.8 rows=10010 loops=1)
        -> Table scan on combined  (actual time=8.78..10.1 rows=11362 loops=1)
            -> Union all materialize  (actual time=8.78..8.78 rows=11362 loops=1)
                -> Limit: 10010 row(s)  (actual time=0.721..3.76 rows=10010 loops=1)
                    -> Covering index lookup on post
                       using idx_post_is_public_created_at (is_public=1)
                       (actual time=0.72..3.08 rows=10010 loops=1)
                -> Limit: 10010 row(s)  (actual time=0.364..3.5 rows=1352 loops=1)
                    -> Filter: (post.feed_id in (1..52))
                       (actual time=0.363..3.4 rows=1352 loops=1)
                        -> Index lookup on post
                           using idx_post_is_public_created_at (is_public=0)
                           (cost=1351 rows=1352) (actual time=0.361..3.25 rows=1352 loops=1)
```

**피드 3개 vs 52개 비교:**

| 항목 | feed IN (1,2,3) | feed IN (1~52) |
|------|:-:|:-:|
| 구독글 사용 인덱스 | `idx_post_feed_created_at` | **`idx_post_is_public_created_at`** |
| 구독글 실제 rows | 40 | 1,352 |
| 임시 테이블 rows | 10,050 | 11,362 |
| actual time | 16.8ms | **14.2ms** |

**인덱스 switch 원인:**

피드 수가 3개일 때는 `idx_post_feed_created_at`으로 각 feed_id별 range scan을 했다.  
피드 수가 52개로 늘어나자 옵티마이저가 "52번 range scan보다 is_public=0 전체를 한 번 훑는 게 더 싸다"고 판단해  
`idx_post_is_public_created_at (is_public=0)`으로 전환 후 feed_id 필터를 적용했다.

**구조적 리스크:**

```
현재: 비공개글 1,352건 → is_public=0 스캔 후 필터 → 빠름

비공개글이 수백만 건으로 늘어나면:
→ is_public=0 전체 수백만 건 스캔
→ feed_id IN (1~52) 필터 적용
→ 구독 피드에 비공개글이 드문드문 있을수록 스캔량 급증
→ 비공개글 규모에 비례해 쿼리 성능 저하
```

**최종 판정:**

| 항목 | 판정 |
|------|------|
| 14.2ms 자체 | 허용 수준 |
| 인덱스 switch | 현재 데이터 기준 합리적, 비공개글 대규모 증가 시 재측정 필요 |
| 피드 수 증가 영향 | 16.8ms → 14.2ms, 측정 노이즈 범위 내 |
| UNION ALL 채택 | 진행 가능 |

---

## C-5: UNION ALL 이관 — t3.small 적용 결과

> 구현: `PostCustomRepositoryImpl.findAllLoggedIn()` → `fetchIdsByUnionAll()` (JdbcTemplate 네이티브 SQL)

### k6 결과 (vus=100, t3.small) — C-4 vs C-5 전 페이지 비교

| 페이지 | C-4 avg | C-4 p(95) | C-4 RPS | C-4 에러율 | C-5 avg | C-5 p(95) | C-5 RPS | C-5 에러율 |
|--------|---------|-----------|---------|-----------|---------|-----------|---------|-----------|
| page=10 | 505ms | 1.59s | 40.8/s | 0% | 618ms | 2.06s | 37.6/s | 0% |
| page=100 | 974ms | 2.67s | 31.6/s | 0% | **582ms** | **1.51s** | **39.0/s** | 0% |
| page=1000 | 699ms | 2.43s | 36.4/s | 0% | **87ms** | **194ms** | **55.6/s** | 0% |
| page=10000 | 28.6s | 46.1s | 2.2/s | **21.22%** | **1.63s** | **3.24s** | **24.0/s** | **0%** |

> **page=10에서는 오히려 퇴행했다** — avg 505→618ms(+22%), p(95) 1.59→2.06s(+30%), RPS 40.8→37.6.
> 얕은 페이지는 C-4 구조에서도 힙 적재량이 작아 UNION ALL의 이득이 없는 반면,
> UNION ALL materialize 비용(+10.9ms 측정)과 JdbcTemplate 경로가 상시 추가되기 때문으로 **추정**한다. 원인 분석 미완료.
> **가장 트래픽이 많은 구간이므로 후속 확인이 필요하다.**  
> page=100 이상: C-5가 전면 우세. page=10000에서 C-4는 에러율 21% 서버 불안정, C-5는 에러 0%.  
> 동일한 t3.small 인스턴스에서 쿼리 구조 변경만으로 page=1000 기준 p95 **2.43s → 194ms (92% 감소)**.

---

### JVM 모니터링 비교 (vus=100, page=1000, t3.small)

| 지표 | C-4 이전 | **C-5 이후** | 변화 |
|------|:--------:|:-----------:|------|
| EC2 CPU 최대 | 100% **지속** | 99% (순간 스파이크) | 지속 포화 → 순간 피크 |
| Load Average 최대 | 18.0 | **3.30** | 5.5배 감소 |
| Minor GC STW 최대 | 275ms | **35.8ms** | 87% 감소 |
| GC 빈도 (mean) | 6.6회/s | **0.101회/s** | 65배 감소 |
| HikariCP Pending 최대 | 41개 | **2개** | 95% 감소 |
| Connection Acquire Time | 600ms | **~15ms** | 97% 감소 |
| Connection Timeout | 발생 | **0** | 완전 해소 |
| JVM Memory Allocate 최대 | ~384MiB | **~40MiB** | 89% 감소 |

---

### 원인 분석

| 항목 | C-4 | C-5 |
|------|-----|-----|
| EC2로 전달되는 객체 수 | Tuple **20,020개** (page=1000) | ID **10개** |
| Java Stream 정렬 | 20,020개 정렬 + skip(10000) | **없음** |
| GC 대상 객체 누적 | vus100 × 20,020 = **200만 개** | vus100 × 10 = **1,000개** |
| DB → EC2 전송량 | 10,050행 × 2컬럼 | **10행 × 1컬럼** |
| RDS 추가 부담 | 없음 | UNION ALL materialize +10.9ms |

```
변경 전: EC2에서 Tuple 20K 정렬 → GC 폭발(6.6/s, STW 275ms) → CPU 지속 100% → HikariCP 41 Pending
변경 후: DB에서 정렬 완료 후 ID 10개만 EC2 전달 → GC 거의 없음 → CPU 순간 피크만 발생
```

---

### 누적 성능 요약 (page=1000, vus=100, t3.small)

| 단계 | 주요 변경 | p(95) | 비고 |
|------|---------|-------|------|
| 베이스라인 | — | 59.58s | 에러율 85.3% |
| C-1~C-3 | two-phase fetch + 커버링 인덱스 + count 서브쿼리 | 3.94s | — |
| C-4 | feedIds + privateCount Redis 캐싱 | 2.43s | 2차 측정값 (C-5와 동일 라운드). 1차 측정은 4.27s — 라운드 간 차이 원인 미규명 |
| **C-5** | **UNION ALL DB 이관 (JdbcTemplate)** | **194ms** | **인프라 업그레이드 없이 달성** |

> C-4까지 캐싱으로 DB 왕복을 줄여도 page=1000에서는 개선이 없었던 이유:  
> 병목이 쿼리 횟수(캐싱 대상)가 아니라 **EC2 힙 적재 20K Tuple → GC** 였기 때문.  
> C-5에서 힙 적재 자체를 10개로 줄이자 GC가 해소되고 p95가 **92% 감소**(2.43s → 194ms)했다.

---

## C-5 심화: page=10000 한계 분석

설계 상한(최대 10,000페이지)에서 C-5 구조의 한계를 측정했다.

### k6 결과 (page=10000, t3.small)

| 조건 | avg | p(95) | 에러율 | 비고 |
|------|-----|-------|--------|------|
| vus=5 | 125ms | **311ms** | 0% | 단일 쿼리 기준선 |
| vus=100 | 1.73s | **3.25s** | 0% | 동시 부하 |

vus가 20배(5→100) 늘었는데 p95가 10.5배(311ms→3.25s) 증가 → 선형이 아님 → **동시 요청 경합**이 주요 원인.

---

### RDS CloudWatch (vus=100, page=10000)

| 지표 | 값 | 판정 |
|------|-----|------|
| CPUUtilization | 최대 **76.7%** | RDS CPU가 새 천장 |
| DatabaseConnections | 23개 (flat) | 안정 |
| FreeableMemory | 62~124MB (상승) | 정상 |
| CPUCreditUsage | 최대 7.03 | 버스터블 크레딧 소모 |
| CPUCreditBalance | 31.66 → 감소 | t4g.micro 한계 접근 |

---

### 병목 후보 3가지 검증

| 후보 | 측정 방법 | 결과 | 판정 |
|------|-----------|------|------|
| Disk I/O | `SHOW GLOBAL STATUS LIKE 'Innodb_buffer_pool_read%'` | hit ratio **99.983%** | 원인 아님 |
| tmp_table 디스크 spill | `SHOW VARIABLES LIKE 'tmp_table_size'` | 16MB, 임시 테이블 ~3.2MB | 원인 아님 |
| 200K 행 정렬 연산 | vus=100 RDS CPU 모니터링 | 76.7% 지속 | **원인** |

**Buffer Pool hit ratio 계산:**
```
Innodb_buffer_pool_reads         = 96,557   (디스크에서 읽은 횟수)
Innodb_buffer_pool_read_requests = 565,744,815 (전체 읽기 요청)
hit ratio = 1 - (96,557 / 565,744,815) = 99.983%
```
→ 디스크 거의 안 씀. RDS CPU 76.7%는 순수 연산 비용.

---

### 근본 원인 — fetchSize=100,010

```java
// page=10000, size=10
int fetchSize = (int) pageable.getOffset() + pageable.getPageSize();
// = 100,000 + 10 = 100,010
```

UNION ALL 쿼리가 DB에서 처리하는 규모:

```
공개글  100,010행 materialize
구독글  100,010행 materialize
────────────────────────────
임시 테이블 최대 200,020행 → ORDER BY created_at DESC → LIMIT 10 OFFSET 100,000
```

page=1000(임시 테이블 10,050행) 대비 **20배 규모**.  
100명이 동시에 이 연산 → RDS CPU 76.7% 경합.

---

### 병목 이동 요약

| 단계 | page | 병목 위치 | p(95) |
|------|------|-----------|-------|
| C-4 이전 | 1000 | EC2 (GC STW 275ms) | 2.43s (2차) / 4.27s (1차) |
| C-5 | 1000 | 없음 | **194ms** |
| C-5 | 10000 | RDS (CPU 76.7%) | **3.25s** |

> C-5 page=1000은 실행 회차에 따라 194~214ms, page=10000은 3.24~3.25s로 편차가 있다. 대표값은 앞의 비교표 기준(194ms / 3.24s)으로 통일한다.

UNION ALL이 EC2 힙 부하를 RDS로 이관했다.  
일반 깊이(page=1000)에서는 RDS 부담이 작아 효과적이지만,  
극단적 깊이(page=10000)에서는 **RDS CPU가 새로운 천장**이 된다.

---

### 결론

| 항목 | 판정 |
|------|------|
| p(95)=3.25s, 에러 0% | **목표(3s) 미달** — 에러율 0%로 안정성은 확보했으나 응답시간 목표는 미충족 |
| RDS CPU 76.7% | 여유는 있으나 부하 증가 시 한계 도달 가능 |
| CPUCredit 소모 | t4g.micro 버스터블 — 지속 부하 시 스로틀링 위험 |
| page=10000 요청 빈도 | 실제 트래픽 로그 확인 필요 |

page=10000 접근이 드물면 현재 구조로 충분하다.  
빈번하다면 **페이지 경계 ID 캐싱**(Redis에 page→created_at 저장, OFFSET 제거)이 다음 단계.
