# RSS 피드 수집 성능 최적화

---

## P — Problem

RSS 스케줄러는 등록된 모든 피드를 순회하며 새 게시글을 수집한다.
Loki 로그 기준, 피드 200개 / 게시글 1,400건 기준으로 약 12,700ms가 소요됐다.

문제는 수치 자체보다 구조에 있었다.
동기 순차 처리는 각 피드의 HTTP 요청이 끝나야 다음 피드로 넘어간다.

```
피드 1 HTTP 요청 → 완료
                    → 피드 2 HTTP 요청 → 완료
                                          → 피드 3 ...
```

피드 수 N에 비례해 처리 시간이 O(N)으로 증가하는 구조다.
피드가 늘수록 수집 주기를 단축하는 것이 구조적으로 불가능해진다.

---

## A — Analyze

### Analyze 1 — 쿼리 모니터링: 무엇이 느린가

DataSource Proxy 기반 queryCount 대시보드로 스케줄러 실행 시 발생하는 SQL을 확인했다.
두 가지 비효율이 보였다.

**N+1 SELECT**

피드마다 "이미 저장된 게시글인지" 확인하는 SELECT가 개별 실행됐다.
피드 200개면 SELECT 200번.

```sql
-- 피드마다 반복 실행됨
SELECT p.link FROM post p WHERE p.feed_id = ? AND p.link IN (...)
```

**개별 INSERT**

게시글 1,400건이 각각 단건 INSERT로 실행됐다. DB 왕복 1,400번.

---

### Analyze 2 — JDBC Batch 적용: DB가 병목이 아님을 확인

개별 INSERT를 JDBC Batch로 전환했다.

```java
int chunkSize = 200;
for (int offset = 0; offset < posts.size(); offset += chunkSize) {
    List<Post> chunk = posts.subList(offset, Math.min(offset + chunkSize, posts.size()));
    jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
        @Override
        public void setValues(PreparedStatement ps, int i) throws SQLException {
            Post post = chunk.get(i);
            ps.setString(1, post.getTitle());
            // ...
        }
        @Override
        public int getBatchSize() { return chunk.size(); }
    });
}
```

건당 1,400번이던 DB 왕복이 피드 단위 batchUpdate 호출로 줄었다.
(호출 수는 새 글이 있는 피드 수 × ceil(글 수 / 200)로, 데이터에 따라 달라진다.)
그런데 전체 처리 시간은 미미하게 줄었을 뿐이었다.

**해석**: DB 저장이 병목이 아니었다. 진짜 병목은 다른 곳에 있다.

---

### Analyze 3 — 비동기 전환: 외부 HTTP I/O가 병목임을 확인

동기 순차 구조를 비동기 병렬로 전환하자 처리 시간이 의미 있게 줄었다.

외부 HTTP 응답을 기다리는 시간이 전체 처리 시간의 대부분이었다는 것이 확인됐다.
피드 200개의 HTTP 요청이 순서대로 기다리고 있었던 것이다.

---

### Analyze 4 — 가상 스레드 적용: 커넥션 풀 고갈 발생

외부 I/O 병목을 해결하기 위해 수집 경로를 가상 스레드 + 논블로킹 HTTP로 전환했다.

동시성의 주역은 `httpClient.sendAsync()`의 논블로킹 I/O다.
요청을 보낸 뒤 스레드가 응답을 기다리지 않으므로 수백 개의 HTTP 요청이 동시에 진행된다.
가상 스레드는 요청을 시작하고 즉시 반환하는 보조 역할로,
요청 시작 오버헤드를 플랫폼 스레드 풀에서 분리한다.
네트워크 수집 속도가 크게 빨라졌다.

그런데 새로운 문제가 생겼다.

```
피드 200개의 HTTP 수집이 동시에 완료
→ DB 저장 요청 200개가 한꺼번에 몰림
→ HikariCP 커넥션 풀 고갈
→ Connection timeout 발생
```

I/O를 빠르게 처리했더니, 그 결과물이 한꺼번에 DB로 쏟아지는 구조적 문제였다.

---

## A — Action

수집 단계와 저장 단계를 서로 다른 실행 모델로 분리했다.

**수집 단계: 가상 스레드 (`fetchRssExecutor`)**

```java
@Bean(name = "fetchRssExecutor")
public Executor fetchRssExecutor() {
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
    executor.setVirtualThreads(true);  // Java 21 가상 스레드
    executor.setThreadNamePrefix("rss-vt-");
    return executor;
}
```

I/O 대기 중 carrier thread를 점유하지 않아 피드 수에 상관없이 동시 처리 가능.
플랫폼 스레드와 달리 수천 개를 생성해도 OS 스레드 고갈이 없다.

**저장 단계: 플랫폼 스레드 풀 (`rssTaskExecutor`, corePoolSize=40)**

```java
@Bean(name = "rssTaskExecutor")
public Executor rssTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(40);
    executor.setMaxPoolSize(200);
    executor.setThreadNamePrefix("rss-exec-");
    executor.initialize();
    return executor;
}
```

저장 작업을 이 executor로 던지면 최대 40개의 스레드만 동시에 DB에 접근한다.
`maxPoolSize`가 200으로 잡혀 있지만 실제로는 **core(40)를 넘어 성장하지 않는다** —
`queueCapacity`가 무한(`Integer.MAX_VALUE`)이라 큐가 가득 차는 일이 없고,
`ThreadPoolExecutor`는 큐가 가득 찼을 때만 core를 넘어 스레드를 만들기 때문이다. maxPoolSize 200은 도달 불가능한 값이다.

다만 HikariCP `maximum-pool-size`는 20이므로, 40개 스레드가 동시에 저장을 시도하면
**실제 동시 DB 작업은 20개로 제한되고 나머지 20개는 커넥션을 대기**한다.
즉 이 구조가 보장하는 것은 "커넥션 풀 고갈 방지"라기보다 **대기자 수의 상한**이다 —
가상 스레드처럼 무제한으로 몰릴 때 발생하던 커넥션 타임아웃을, 대기 큐 길이를 예측 가능한 범위로 묶어 해소했다.

> 처음에는 `Semaphore(40)`으로 동시 DB 접근을 직접 제한하려 했다.
> 그러나 스레드 풀을 분리하는 순간 `corePoolSize`가 이미 같은 상한을 강제하므로
> 세마포어는 중복 방어가 됐고, `acquire`/`release`를 호출하지 않는 빈만 남아 있었다.
> **동작하지 않는 코드가 설정에 남아 있으면 구조를 잘못 읽게 만들기 때문에 제거했다.**
> 동시 접근 제한은 전적으로 스레드 풀이 담당한다.

**스케줄러 흐름**

```
fetchRssExecutor (가상 스레드)          rssTaskExecutor (플랫폼 스레드 풀, max 40)
        ↓                                           ↓
HTTP 수집 완료 → thenComposeAsync() → 파싱 + DB 저장
(피드 수 무관, 동시 처리)               (최대 40개 동시 DB 접근)
```

**중복 확인: Redis ZSet (Cache-Aside)**

피드별 반복 SELECT를 제거하기 위해 Redis ZSet에 수집된 게시글 링크를 캐시했다.

```java
public List<SyndEntry> extractPostListImprove(Feed dtoFeed, List<SyndEntry> entries) {
    String key = RedisKeyConstants.RSS_FEED + dtoFeed.getId();
    List<String> links = entries.stream().map(SyndEntry::getLink).toList();

    if (!redisService.hasKey(key)) {
        // 최초 실행: DB Bulk SELECT 1회
        Set<String> alreadyInDb = postRepository.findExistingLinksByFeed(dtoFeed, links);
        return entries.stream()
                .filter(e -> !alreadyInDb.contains(e.getLink()))
                .collect(Collectors.toList());
    }

    // 이후: Redis에서 한 번에 확인, DB SELECT 없음
    List<Boolean> existenceList = redisService.checkLinksExistence(key, links);
    ...
}
```

캐시 히트 시 DB SELECT 0회. 캐시 미스(최초 실행)일 때만 Bulk SELECT 1회.

---

## R — Result

| 항목 | 개선 전 | 개선 후 |
|---|---|---|
| INSERT | 게시글 1건당 1회 — 총 약 1,400회 | 피드 단위 `batchUpdate`(chunk 200)<br>→ 호출 횟수 = 새 글이 있는 피드 수 × ⌈피드별 글 수 / 200⌉ |
| 중복 확인 SELECT | 피드당 1회 — 총 약 200회 (피드 200개) | 캐시 히트 시 **총 0회**<br>캐시 미스(최초 실행)일 때만 피드당 Bulk SELECT 1회 |
| 전체 처리 시간 | 약 12,700ms | 약 8,990ms (약 29% 개선) |

> 개선 후 INSERT 호출 횟수는 **수집 시점에 새 글이 있는 피드 수와 피드별 글 수에 따라 달라진다** —
> 고정된 값이 아니므로 단일 숫자로 적지 않는다.
> 다만 1,400건이 200개 피드에 분산되는 이 측정 조건에서는 피드당 글 수가 chunk(200)에 크게 못 미쳐
> 대부분 피드당 1회로 수렴하며, JDBC `rewriteBatchedStatements=true`가 각 호출을 다중 행 INSERT 한 문장으로 합친다.
> **네트워크 왕복 횟수가 줄어드는 것이 이득의 실체**이고, 아래 Analyze 2에서 확인했듯 이 구간은 병목이 아니었다.

처리 시간 기준 29% 개선이다.
단, 외부 HTTP 응답 시간 자체는 제어할 수 없어 I/O 응답 속도에 따라 수치는 달라진다.

속도보다 중요한 변화는 구조다.

- **수집**: 피드 수 N에 무관하게 동시 처리 → O(N) 증가 구조 해소
- **저장**: 스레드 풀(40)로 동시 저장 작업 수의 상한을 고정 → 커넥션 대기 큐가 예측 가능한 범위로 묶여 타임아웃 해소
- **중복 확인**: Redis 캐시 우선 → 피드마다 반복되던 SELECT 제거

---

## 마치며

이 작업에서 가장 중요한 발견은 **병목이 어디 있는지 확인한 다음 개선해야 한다**는 것이다.

JDBC Batch를 먼저 적용했지만 속도가 크게 안 줄었다.
DB가 병목이 아니었기 때문이다.
비동기로 전환하고 나서야 외부 HTTP I/O가 주 병목임이 확인됐다.

가상 스레드는 I/O 바운드 작업에 효과적이었지만,
동시성이 증가하면서 DB 접근 집중이라는 새로운 문제를 만들었다.
수집과 저장을 다른 실행 모델로 분리한 것이 이 균형을 맞추는 방법이었다.
