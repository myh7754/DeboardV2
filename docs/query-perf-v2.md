# 게시글 목록 조회 API 성능 개선 2라운드 — 1000만 건 기준 재측정

> 1라운드(troubleshooting.md)에서 10만 건 기준으로 측정·개선했던 조회 성능을  
> **1000만 건 + 허용 최대 페이지(10000p, OFFSET 99,990) 기준**으로 재검증한다.  
> 개선 항목을 하나씩 적용하면서 각 단계의 효과를 측정한다.

---

## 측정 환경

| 항목 | 값 |
|------|-----|
| 인스턴스 | EC2 T3.Small (2 vCPU, 2GB RAM) |
| 데이터셋 | Post 1,000만 건 |
| 테스트 도구 | k6 |
| 테스트 대상 | `GET /api/posts?page=10000&size=10` (비로그인, 최대 허용 페이지) |
| 부하 | VUS 100 |
| 허용 최대 페이지 | 10,000p (OFFSET 99,990) — 프론트에서 초과 차단 |

---

## 성능 목표

| 지표 | 목표값 |
|------|--------|
| p(95) 응답시간 | < 3s |
| 에러율 | < 1% |

---

## 베이스라인 — 개선 전

> 개선 항목을 적용하기 전 현재 상태를 기록한다.

### 테스트 조건

- VUS 100, `page=10000&size=10` (OFFSET 99,990)
- 비로그인 사용자 기준
- 캐시 없음 (첫 페이지만 SWR 캐싱, 10000페이지는 캐싱 대상 외)

### 결과

| 지표 | 값          |
|------|------------|
| avg | 22.59s     |
| med | 22.85s     |
| p(90) | 30.13s     |
| **p(95)** | **31.62s** |
| max | 30.59s     |
| RPS | 5.72/s     |
| 에러율 | 0.04%      |

> p(95) **31.62s** — 목표(3s) 대비 **10.5배 초과**

> **주의:** 위 표는 p(95) 31.62s가 max 30.59s보다 커서 수학적으로 성립하지 않는다. 측정값 전사 과정의 오류로 보이며, 원본 k6 출력이 남아 있지 않아 **베이스라인 재측정이 필요**하다. 에러율(0.04%)도 같은 이유로 재측정 필요 — 이하 비교 표의 베이스라인 값은 이 기록을 그대로 옮긴 것이다.

### 원인 분석 (사전 EXPLAIN 기반)

현재 비로그인 `findAll()` 쿼리 구조:

```sql
-- 데이터 쿼리
SELECT p.id, p.title, ...
FROM post p
LEFT JOIN users u ON p.user_id = u.user_id
LEFT JOIN external_author ea ON p.external_author_id = ea.id
LEFT JOIN feed f ON p.feed_id = f.id
WHERE p.is_public = 1
ORDER BY p.created_at DESC
LIMIT 10 OFFSET 99990;

-- count 쿼리 (getCappedTotalCount)
-- QueryDSL이 실제로 실행하는 것:
SELECT p.id
FROM post p
LEFT JOIN users u ON p.user_id = u.user_id
LEFT JOIN feed f ON p.feed_id = f.id
WHERE p.is_public = 1
LIMIT 100000;
-- → 100,000개 Long 값을 Java 힙에 올린 뒤 .size() 호출
```

**병목 요인:**

1. **OFFSET 99,990**: 복합 인덱스 `(is_public, created_at DESC)` 를 타더라도 99,990개 인덱스 항목을 스캔·버린 후 10개를 반환
2. **count 쿼리 잘못 구현**: `SELECT id ... LIMIT 100000` + `fetch().size()` → 100,000개 Long 값을 매 요청마다 네트워크로 전송 후 Java에서 카운트
3. **count 쿼리 불필요한 JOIN**: `is_public` 조건에는 `user`, `feed` 테이블 JOIN 불필요

---

## 개선 항목

| ID | 분류 | 설명 | 예상 효과 |
|----|------|------|----------|
| B-1 | count 쿼리 | `fetch().size()` → `COUNT(id)` 집계 + 불필요 JOIN 제거 | 네트워크/힙 낭비 제거 |
| B-2 | 정렬 일치 | `Sort.by("id")` → `Sort.by("createdAt")` dead code 제거 | 코드 정합성 |
| B-3 | No-offset | OFFSET → Keyset pagination `(created_at, id)` 커서 방식 | deep page 근본 해결 |
| B-4 | SWR 원자성 | `isStale` + `refreshAsync` 원자적 처리 | Stampede 완전 차단 |

---

## B-1: count 쿼리 개선

### 변경 내용

**`PostCustomRepositoryImpl.getCappedTotalCount()`**

```java
// 변경 전
private long getCappedTotalCount(BooleanExpression finalCondition) {
    return queryFactory
            .select(qPost.id)
            .from(qPost)
            .leftJoin(qPost.author, qUser)   // 불필요
            .leftJoin(qPost.feed, qFeed)     // 불필요
            .where(finalCondition)
            .limit(100_000)
            .fetch()                         // 100,000 Long을 힙에 적재
            .size();                         // Java에서 카운트
}

// 변경 후
private long getCappedTotalCount(BooleanExpression finalCondition) {
    Long count = queryFactory
            .select(qPost.count())           // SELECT COUNT(id) — DB에서 집계
            .from(qPost)
            // JOIN 제거 — is_public, feed_id 조건 모두 post 컬럼
            .where(finalCondition)
            .fetchOne();
    return Math.min(count != null ? count : 0L, 100_000L);
}
```

**변경 포인트:**
- `SELECT id ... LIMIT 100000 → fetch().size()` : 100,000개 ID가 Java 힙에 올라오던 것을 `SELECT COUNT(id)` 집계 쿼리로 교체
- DB → Java 전송량: ~800KB → 8 bytes
- 불필요한 `user`, `feed` LEFT JOIN 제거 (`is_public` 조건은 `post` 컬럼만 참조)
- count 결과 100,000 상한은 `Math.min()`으로 유지 (10000p 이상 탐색 방지 정책 동일)

### 테스트 결과

> B-1은 B-5(count Redis 캐시)와 동시 적용되어 단독 측정값 없음. B-1+B-5 1차 적용 결과로 기록.

| 지표 | 베이스라인 | B-1+B-5 1차 | 변화 |
|------|-----------|------------|------|
| avg | 22.59s | 3.33s | 85% 감소 |
| p(95) | 31.62s | 23.27s | 악화 |
| RPS | 5.72/s | 21/s | 3.7배 향상 |
| 에러율 | 0.04% (재측정 필요) | 0.00% | — |

> **p(95) 악화 원인:** count 캐시 TTL 70s 만료 순간 VUS 100이 동시에 캐시 미스 → 동시 `countPublic()` 실행 → DB 커넥션 포화 (cache stampede). → B-5 SWR+SETNX 패턴으로 해결 (아래 참고)

---

## B-2: Sort 정합성 수정

### 변경 내용

**`PostServiceImpl.readAll()`**

```java
// 변경 전 — QueryDSL이 무시하는 Sort 지정
Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

// 변경 후 — 실제 정렬 기준과 일치
Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
```

dead code 제거 목적이며, 응답 속도 변화는 없음.

### 테스트 결과

<!-- B-1 이후 재측정 예정 -->

---

## B-3: No-offset (Keyset) Pagination

### 배경

OFFSET 방식의 근본 한계: `LIMIT 10 OFFSET 99990`은 복합 인덱스를 타더라도  
**99,990개의 인덱스 항목을 순서대로 읽고 버린 후** 다음 10개를 반환한다.  
OFFSET이 클수록 선형으로 느려지는 구조적 문제다.

### 해결 방향

```sql
-- 기존 OFFSET 방식
WHERE is_public = 1
ORDER BY created_at DESC
LIMIT 10 OFFSET 99990          -- 99,990개 스캔 후 버림

-- Keyset 방식 (커서 기반)
WHERE is_public = 1
  AND (created_at < :lastCreatedAt
       OR (created_at = :lastCreatedAt AND id < :lastId))
ORDER BY created_at DESC, id DESC
LIMIT 10                        -- 인덱스의 해당 위치로 바로 점프
```

커서 방식은 이전 페이지의 마지막 항목 `(created_at, id)` 를 기준으로  
인덱스에서 그 위치로 바로 이동하기 때문에 페이지 깊이와 무관하게 일정한 응답 속도를 보인다.

### API 변경

| | OFFSET 방식 | Keyset 방식 |
|---|---|---|
| 요청 파라미터 | `?page=10000&size=10` | `?size=10&cursor=...` |
| 응답 | `totalPages`, `number` 포함 | `nextCursor`, `hasNext` |
| 랜덤 페이지 이동 | 가능 | 불가 (이전/다음만) |
| deep page 성능 | O(offset) | O(1) |

> 랜덤 페이지 이동이 불가능한 트레이드오프가 있지만, 이미 10000p 이상은 차단한 상태이므로  
> 실사용에서는 '다음 페이지 이동'이 대부분이다.

### 테스트 결과

<!-- 적용 후 k6 결과 기록 예정 -->

| 지표 | 베이스라인 | B-3 적용 후 | 변화 |
|------|-----------|------------|------|
| avg | 22.59s | - | - |
| p(95) | 31.62s | - | - |
| RPS | 5.72/s | - | - |
| 에러율 | 0.04% (재측정 필요) | - | - |

---

## B-4: SWR refreshAsync 원자성 보강

### 배경

현재 `isStale()` 확인과 `refreshAsync()` 호출 사이에 원자성이 없어,  
VUS가 높을 때 N개 요청이 동시에 `isStale = true`를 확인하고 모두 `refreshAsync`를 큐에 넣을 수 있다.

### 변경 방향

Redis `SETNX` (SET if Not eXists) 를 활용해 refresh 트리거를 원자적으로 처리한다.

### 적용 현황 — count 캐시에만 적용, page 캐시는 미적용

SETNX 단일 진입 보장은 **count 캐시(`refreshCountAsync`)에만 적용**했다 (B-5 참고).
page 캐시의 `refreshAsync`는 SETNX 없이, 첫 줄에서 신호 키를 일반 SET으로 재설정하는 방식이다:

```java
// PostCacheService.refreshAsync — 실제 코드 (SETNX 미적용)
@Async("cacheTaskExecutor")
@Transactional(readOnly = true)
public void refreshAsync(String cacheKey, Pageable pageable) {
    redisService.setValueWithExpire(cacheKey + RedisKeyConstants.STALE_SUFFIX, "1", STALE_TTL);
    fetchAndStore(cacheKey, pageable);

    log.debug("비로그인 첫 페이지 캐시 비동기 갱신 완료: {}", cacheKey);
}
```

첫 줄의 신호 키 재설정이 중복 갱신 창을 크게 줄이지만, 일반 SET은 원자적이지 않아
`isStale()` 확인 ~ SET 사이에 N개 요청이 동시에 진입할 수 있는 짧은 경합 창이 남는다.
page 캐시에도 `setIfAbsent` 를 적용하는 것은 **향후 과제**다.

### 테스트 결과

<!-- B-3 이후 재측정 예정 -->

---

## B-5: 비로그인 total count Redis 캐시

### 배경

B-1(COUNT 집계 쿼리)은 100,000개 Long의 힙 적재를 해결하지만, VUS 100 환경에서 매 요청마다 `SELECT COUNT(*)` 쿼리가 실행된다. 특히 page=1~9999 범위는 SWR 캐시를 우회하므로 count 쿼리 자체가 새 병목이 될 수 있다.

### 에펨코리아 분석 인사이트

대형 커뮤니티 사이트(에펨코리아)는 카운터 패턴을 사용한다:
- 게시글 등록 시 카운터 +1
- 게시글 삭제 시 카운터 -1
- 조회 시 카운터만 읽음 (O(1))

조회 때마다 COUNT(*)를 하지 않으므로 데이터 양과 무관하게 빠르다.

### 의사결정

**적용 범위:** 비로그인 기본 목록(공개 게시글)만 캐시

**이유:**
- 검색: 조건이 무한하여 캐시 키가 폭발함 (title=X, title=Y, ...). 개별 count 캐시 불가
- 로그인: 구독한 피드 조합이 사용자마다 다르므로 통합 count 불가. 개별 캐시는 메모리 낭비
- 비로그인 공개: 고정 조건(is_public=1)이므로 단일 캐시 키로 충분

### 구현 방식

```java
// PostCustomRepository에 추가
long countPublic();  // SELECT COUNT(*) WHERE is_public = 1

List<PostDetailResponse> getPublicList(Pageable pageable);
// 비로그인 page >= 1 경로에서 사용

// PostCacheService에 추가
public long getCachedPublicCount() {
    // Redis에서 조회 → 미스 시 DB 조회 후 저장
    Object cached = redisService.getValue(RedisKeyConstants.POST_PUBLIC_COUNT);
    if (cached instanceof Number n) return n.longValue();
    long count = postCustomRepository.countPublic();
    redisService.setValueWithExpire(
        RedisKeyConstants.POST_PUBLIC_COUNT, count, Duration.ofSeconds(70)
    );
    return count;
}

public void evictPublicCount() {
    redisService.deleteValue(RedisKeyConstants.POST_PUBLIC_COUNT);
}

// PostServiceImpl에서 비로그인 분기
if (isAnonymous) {
    if (page == 0 && size == 10) return readAllCached(pageable);  // SWR 유지
    return readAllAnonymous(pageable);  // count만 캐시
}

// 게시글 등록/삭제 시 무효화
postCacheService.evictPublicCount();
```

**TTL:** 70초 (기존 DATA_TTL 동일). 단, evict는 **수동 게시글 등록/삭제 경로에만** 걸려 있고 RSS 배치 저장 경로는 아무것도 evict하지 않는다. 따라서 글의 대부분을 차지하는 RSS 글에는 **TTL이 유일한 무효화 수단**이며, evict는 수동 글에 한정된 보조 수단이다.

**캐시 미스 시 동작:** 게시글 등록/삭제 직후 TTL 만료 전 요청 → 여러 요청이 동시 COUNT 실행 가능. 하지만 `SELECT COUNT(*) WHERE is_public=1`은 단순 집계이므로 DB 부하는 무시할 수준. 경쟁해서 저장된 값이 같으므로 정합성 문제 없음.

### 구현 효과

| 경로 | 변경 전 | 변경 후 |
|------|--------|--------|
| page=0 (SWR) | Redis 히트, count 캐시됨 | 동일 |
| page=1~9999 | 매 요청 COUNT(*) 실행 | Redis 히트, count 캐시 사용 |
| 게시글 등록 | page:0만 evict | page:0 + count 모두 evict |
| 게시글 삭제 | evict 없음 (버그) | page:0 + count evict 추가 |

### 1차 적용 — 단순 TTL 방식 (문제 발생)

단순 TTL(70s)으로 count를 캐시한 결과 **cache stampede** 발생:

| 지표 | 베이스라인 | B-5 1차 | 변화 |
|------|-----------|---------|------|
| avg | 22.59s | 3.33s | 85% 감소 |
| p(95) | 31.62s | 23.27s | 오히려 악화 |
| RPS | 5.72/s | 21/s | 3.7배 향상 |

**원인 분석:**
```
TTL 70s 만료 순간
→ VUS 100이 동시에 캐시 미스 감지
→ 동시에 countPublic() DB 쿼리 실행
→ HikariCP 커넥션 포화 → p(95) 폭발
```

### 2차 개선 — SWR + SETNX 패턴 적용

page:0 SWR 패턴을 count 캐시에도 동일하게 적용. Redis 키 2개로 stampede 차단:

| 키 | TTL | 역할 |
|----|-----|------|
| `post:public:count` | 70s | 실제 count 값 (안전망) |
| `post:public:count:stale` | 40s | 신선도 신호 |

```java
// 40s마다 신호 키 만료 → 비동기 갱신 트리거
// SETNX로 갱신 스레드 1개만 진입 허용
@Async("cacheTaskExecutor")
public void refreshCountAsync() {
    boolean acquired = redisService.setIfAbsent(
        POST_PUBLIC_COUNT + STALE_SUFFIX, "1", COUNT_STALE_TTL
    );
    if (!acquired) return;   // 이미 갱신 중 → 스킵
    refreshCountSync();
}
```

**흐름:**
1. 요청 → count 키 있음 → 즉시 반환 (stale이어도)
2. 신호 키(40s) 만료 감지 → 비동기 갱신 트리거 (SETNX 덕분에 1개만)
3. count 키(70s) 만료 = cold start → 동기 갱신 (요청 있을 때만 발생)
4. 게시글 등록/삭제 → 두 키 모두 evict → 다음 요청에서 동기 갱신

**추가 수정:** `evictPublicCount()`에서 stale 키도 함께 삭제하도록 보완.
초기 구현에서 count 키만 삭제하면 stale 신호가 살아있어 갱신 트리거가 발생하지 않는 버그가 있었음.

### 최종 테스트 결과 (SWR + SETNX 적용 후)

| 지표 | 베이스라인 | 최종 결과 | 변화 |
|------|-----------|---------|------|
| avg | 22.59s | **1.94s** | 91% 감소 |
| med | 22.85s | **1.9s** | 92% 감소 |
| p(90) | 30.13s | **2.32s** | 92% 감소 |
| **p(95)** | **31.62s** | **2.53s** | **목표(3s) 달성** |
| RPS | 5.72/s | **30/s** | 5.2배 향상 |
| 에러율 | 0.04% (재측정 필요) | 0.00% | — |
| **Grafana 힙 사용률** | **91.8%** | **25.7%** | **66%p 감소** |

> 힙 91.8% → 25.7% 감소는 100k Long 힙 적재 제거(B-1)와 count 쿼리 캐시(B-5)의 복합 효과.  
> GC Stop-the-World 평균 389μs로 안정화됨.

---

## B-6: countPublic — COUNT 풀스캔 근본 해결 (LIMIT 서브쿼리)

### 배경 — SWR 캐시가 있는데도 COUNT가 병목?

B-5 적용 후 `countPublic()`은 SWR 캐시로 보호되고 있었다. 40초마다 비동기 갱신, SETNX로 중복 방지. 이론상 대부분의 요청은 Redis에서 count를 가져와야 했다.

그런데 **VUS 100, page=10000 기준 avg 1.94s는 여전히 납득하기 어려운 수치**였다. EXPLAIN ANALYZE 결과 `countPublic()`의 실제 실행 시간이 **4,597ms**임을 확인했고, "캐시가 있어도 이 쿼리가 병목일 수 있다"는 가설을 세웠다.

### 가설 검증 — count 제거 격리 실험

가설이 맞는지 확인하기 위해 `countPublic()` 호출 자체를 제거하고 상한값 100,000을 하드코딩하는 격리 실험을 진행했다.

```java
// PostServiceImpl — 검증용 임시 변경
private Page<PostDetailResponse> readAllAnonymous(Pageable pageable) {
    List<PostDetailResponse> content = postCustomRepository.getPublicList(pageable);
    // long total = postCacheService.getCachedPublicCount();
    long total = 100_000L;  // count 제거 후 실측
    return new PageImpl<>(content, pageable, total);
}
```

<!-- 재측정 필요: 아래 결과는 page=100 기준으로 측정된 신뢰할 수 없는 수치 -->
<!--
### 검증 결과 (VUS 100)

| 지표 | B-5까지 적용 | count 제거 후 | 변화 |
|------|------------|-------------|------|
| avg | 1.94s | **86ms** | **22배 감소** |
| p(95) | 2.53s | **271ms** | 9배 감소 |
| RPS | 30/s | **73.6/s** | 2.5배 증가 |

**가설 확인. count가 실제 병목이었다.**
-->

### 검증 결과 (VUS 100, page=10000)

| 지표 | B-5까지 적용 | count 제거 후 | 변화 |
|------|------------|-------------|------|
| avg | 1.94s | **671ms** | 65% 감소 |
| med | 1.9s | **582ms** | 69% 감소 |
| p(90) | 2.32s | **1.01s** | 56% 감소 |
| p(95) | 2.53s | **1.36s** | 46% 감소 |
| RPS | 30/s | **49.1/s** | 64% 증가 |
| 에러율 | 0.00% | 0.00% | — |

**가설 확인. count가 실제 병목이었다.**

### 원인 분석 — SWR 캐시가 있어도 왜 병목이 되었나

```
EXPLAIN ANALYZE SELECT COUNT(post.id) FROM post WHERE is_public = 1;

→ Covering index lookup on post using idx_post_is_public_created_at
   actual time=0.582..4166  rows=10e+6  loops=1
→ Aggregate: count(post.id)  actual time=4597ms
```

SWR 캐시 구조상 **40초마다 비동기 갱신이 4.6초짜리 COUNT 쿼리를 실행**한다. 이 시간 동안 DB 커넥션 1개가 묶인다.

```
[40s 주기]
t=0s  : 신호 키 만료 → refreshCountAsync() 트리거 → 4.6초 COUNT 실행
t=0~4.6s : HikariCP 커넥션 1개 점유
           VUS 100 부하에서 커넥션 경합 → 나머지 요청 대기열 증가
t=4.6s : 갱신 완료, 커넥션 반납
→ 40초마다 반복
```

커넥션 1개가 4.6초 점유되는 것이 VUS 100 부하에서 연쇄 대기를 유발한 것이다. 또한 `getCachedPublicCount()` 내부에서 Redis `getValue` + `hasKey` **2번의 네트워크 왕복**이 매 요청마다 직렬로 실행되는 것도 미세한 오버헤드를 누적시켰다.

### 해결 — LIMIT 서브쿼리로 풀스캔 차단

`SELECT COUNT(*) FROM post WHERE is_public=1`은 1000만 건을 전부 스캔한다. 어차피 100,000이 상한이므로, 100K에 도달하면 즉시 멈추는 서브쿼리로 교체했다.

> **트레이드오프 — 총 건수는 실제 값이 아니다.**  
> 이 cap 때문에 API가 응답하는 `totalElements`는 실제 공개 글 수(약 1,000만)와 무관하게 **최대 100,000으로 고정**되고,
> 총 페이지 수도 10,000페이지에서 잘린다. 즉 **사용자에게 보이는 총 개수/총 페이지는 정확하지 않다.**  
> 정확한 총 건수보다 응답 시간을 택한 의도적 결정이다 —
> 게시글 목록에서 "정확히 몇 건인지"를 쓰는 UI가 없고, 10,000페이지 이후를 실제로 탐색하는 사용자도 없다고 판단했다.
> 정확한 총계가 필요해지면 별도 집계 테이블(카운터)을 두는 것이 다음 선택지다.

```sql
-- 변경 전: 1000만 건 풀스캔 (4.6s)
SELECT COUNT(id) FROM post WHERE is_public = 1

-- 변경 후: 100K 도달 즉시 중단 (~46ms)
SELECT COUNT(*) FROM (SELECT 1 FROM post WHERE is_public = 1 LIMIT 100000) t
```

QueryDSL `JPAQueryFactory`는 서브쿼리 COUNT를 지원하지 않아 `JdbcTemplate`으로 직접 실행했다.

**`PostCustomRepositoryImpl.countPublic()`**

```java
// 변경 전
@Override
public long countPublic() {
    Long count = queryFactory
            .select(qPost.count())
            .from(qPost)
            .where(qPost.isPublic.isTrue())
            .fetchOne();
    return Math.min(count != null ? count : 0L, 100_000L);
}

// 변경 후
@Override
public long countPublic() {
    Long count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM (SELECT 1 FROM post WHERE is_public = 1 LIMIT 100000) t",
        Long.class
    );
    return count != null ? count : 0L;
}
```

**변경 범위:** `countPublic()`은 비로그인 캐시 갱신 경로(`PostCacheService.refreshCountSync`)와 `findAll()`의 로그인 분기(`PostCustomRepositoryImpl.findAll`, 로그인 total 합산) 두 곳에서 호출되며 조건이 `is_public=1`로 고정이다. 동적 조건이 필요한 경로(`getCappedTotalCount(BooleanExpression)`)는 별도로 유지했다.

### 한계 — 잔존 풀스캔 경로

B-6은 `countPublic()`만 고쳤다. 다음 경로에는 개선 전 패턴이 그대로 남아 있다 (**향후 개선 대상**):

- **SWR page:0 갱신 경로** (`fetchAndStore` → `findAll` → `getCappedTotalCount`): `getCappedTotalCount`는 LIMIT 없는 `COUNT(*)`라 자체 EXPLAIN 기준 ~4.6s 풀스캔이 잔존한다. 40초 주기 비동기 갱신마다 이 쿼리가 실행된다.
- **`getCappedTotalLikeCount`**: `LIMIT 100000 → fetch().size()` 패턴(B-1에서 제거한 것과 동일한 안티패턴)이 좋아요 목록 경로에 잔존한다.

### 최종 테스트 결과 (VUS 100, page=10000)

| 지표 | B-5까지 (VUS 100) | B-6 적용 후 (VUS 100) | 변화 |
|------|-----------------|---------------------|------|
| avg | 1.94s | **1.17s** | 40% 감소 |
| med | 1.9s | **977ms** | 49% 감소 |
| p(90) | 2.32s | **1.89s** | 19% 감소 |
| **p(95)** | **2.53s** | **2.33s** | 8% 감소 |
| **RPS** | **30/s** | **38.4/s** | 28% 증가 |
| 에러율 | 0.00% | 0.00% | — |

> 모든 지표 개선. COUNT 풀스캔(4.6s) → LIMIT 서브쿼리(~46ms)로 SWR 갱신 시 커넥션 블로킹 감소.
> count 제거 격리 실험(49.1 RPS, p95=1.36s) 대비 여전히 gap이 있어 count가 잔여 병목으로 작용 중.

---

## I-1: MySQL → RDS 분리 (인프라 개선)

### 배경

B-6까지 적용 후에도 VUS 100 avg 1.17s. Grafana에서 EC2 process CPU = 0.06(6%)인데 system CPU = 1.0(100%)으로 **MySQL이 EC2 CPU 대부분을 점유**하고 있었음.

Spring Boot과 MySQL이 같은 EC2(2vCPU)에서 CPU를 경쟁하는 구조가 근본 원인.

### 변경 내용

EC2 내 Docker MySQL → AWS RDS 분리 (별도 인스턴스)

- Spring Boot: EC2 CPU 전용
- MySQL: RDS 전용 CPU

### 테스트 결과 (VUS 100, page=10000)

| 지표 | B-6 (MySQL 동거) | RDS 분리 후 | 변화 |
|------|----------------|------------|------|
| avg | 1.17s | **294ms** | 75% 감소 |
| med | 977ms | **254ms** | 74% 감소 |
| p(90) | 1.89s | **411ms** | 78% 감소 |
| **p(95)** | **2.33s** | **535ms** | 77% 감소 |
| **RPS** | **38.4/s** | **62/s** | 61% 증가 |
| 에러율 | 0.00% | 0.00% | — |

> 쿼리 최적화보다 인프라 분리 효과가 훨씬 컸음.
> 쿼리를 아무리 줄여도 CPU를 공유하는 한 한계가 있었던 것.

---

## I-2: HikariCP pool size 10 → 20

### 배경

RDS 분리 후 CloudWatch에서 DatabaseConnections 최대 14. pool=10은 connection wait 발생 가능.

### 변경 내용

`application-dev.yml`: `maximum-pool-size: 10 → 20`

### 테스트 결과 (VUS 100, page=10000)

| 지표 | RDS 분리 (pool=10) | pool=20 | 변화 |
|------|------------------|--------|------|
| avg | 294ms | **337ms** | 15% 악화 |
| med | 254ms | **273ms** | — |
| p(90) | 411ms | **422ms** | — |
| **p(95)** | **535ms** | **514ms** | — (노이즈 범위) |
| **RPS** | **62/s** | **62/s** | — |
| 에러율 | 0.00% | 0.00% | — |

> **측정 기준 유의미한 효과 없음.** p(95) 535→514ms는 노이즈 범위이고 avg는 294→337ms로 오히려 악화.
> RDS DatabaseConnections=14 기준으로 pool=10도 충분했음. 부하 spike 시 connection wait 방지용
> 여유분 확보 차원에서 설정은 유지.

## VUS 300 부하 테스트 (현재 한계 측정)

### 테스트 결과 (VUS 300, page=10000)

| 지표 | VUS 100 (pool=20) | VUS 300 (pool=20) | 변화 |
|------|-----------------|-----------------|------|
| avg | 337ms | **2.77s** | 8배 악화 |
| med | 273ms | **2.78s** | 10.2배 악화 |
| p(90) | 422ms | **5.2s** | 12.3배 악화 |
| **p(95)** | **514ms** | **5.87s** | 11.4배 악화 |
| **RPS** | **62/s** | **68/s** | — (9%만 증가) |
| 에러율 | 0.00% | 0.00% | — |

> VUS 3배 증가 대비 RPS 9%만 증가 → 처리량 천장 도달.
> pool=20으로 280개 요청이 대기열 적체, RDS T3 CPU 크레딧 소진이 복합 작용.

### 병목 원인

```
VUS 300 → pool=20 → 20개만 DB 접근, 280개 HikariCP 대기
+ RDS T3 CPU 크레딧 소진 → 스로틀링
→ avg 응답시간 = 대기시간 + 실행시간
```

### 다음 개선 방향 (미적용)

| 방법 | 예상 효과 |
|---|---|
| RDS T3 Unlimited 활성화 | CPU 스로틀링 즉시 해소 |
| RDS 스펙 업 (T3 → M 클래스) | 크레딧 없는 안정적 성능 |
| pool size 20 → 50 | 대기 요청 감소, RDS 여유 있을 때 유효 |
| EC2 수평 확장 (ALB + 2대) | Spring Boot 처리 용량 2배 |

---

## 전체 개선 요약

| 단계 | 주요 변경 | p(95) | RPS |
|------|---------|-------|-----|
| 베이스라인 | — | 31.62s | 5.72/s |
| B-1 + B-5 1차 | COUNT 집계 + 단순 TTL 캐시 | 23.27s | 21/s |
| B-5 SWR + SETNX | count 캐시 stampede 해결 | **2.53s** | **30/s** |
| B-2 | Sort dead code 제거 | 변화 없음 | — |
| B-3 No-offset | 미적용 (게시판 형식) | — | — |
| B-4 SWR 원자성 | count 캐시에 적용 완료 | — | — |
| **B-6** | **COUNT LIMIT 서브쿼리** | **2.33s** | **38.4/s** |
| **I-1** | **MySQL → RDS 분리** | **535ms** | **62/s** |
| I-2 | HikariCP pool 10→20 — 효과 없음(측정 기준) | 514ms | 62/s |
