# 문제 해결 경험

---

## 1. 좋아요 토글 동시성 — 데드락에서 Race Condition까지

### Problem

`toggleLike`는 "좋아요 존재 확인 → INSERT/DELETE" 2단계 연산이다.
조회와 삽입 사이에 간격이 있어 동시 요청이 몰리면 정합성이 깨질 수 있다고 판단,
구현 직후 CountDownLatch 기반 동시성 테스트를 작성했다.

테스트를 실행하자 예상했던 race condition보다 먼저 데드락이 터졌다.

```
Deadlock found when trying to get lock; try restarting transaction
```

---

### Analyze 1 — 데드락

**원인:** 초기 구현은 likes 조작 후 Post를 조회해 dirty checking으로 likeCount를 업데이트하는 방식이었다.

```java
likesRepository.save(...);         // likes INSERT → FK로 posts에 shared lock 획득
Post post = getPostById(postId);   // posts SELECT
post.setLikeCount(count);          // commit 시 posts UPDATE → exclusive lock 필요
```

InnoDB는 FK 참조 무결성 확인을 위해 INSERT 시 참조 대상 행에 shared lock을 자동 획득한다.
두 트랜잭션이 각각 shared lock을 보유한 채 exclusive lock으로 upgrade를 기다리면 교착상태가 된다.

```
TX A: INSERT likes → posts shared lock → commit 시 exclusive 필요 (TX B 보유 중 → 대기)
TX B: INSERT likes → posts shared lock → commit 시 exclusive 필요 (TX A 보유 중 → 대기)
→ 교착상태 4조건 중 점유대기(hold-and-wait) 성립
```

**해결:** posts UPDATE를 먼저 실행해 exclusive lock을 선점하고, 이후 likes 조작 순서로 변경했다.

```java
postRepository.increaseLikeCount(postId);  // posts 먼저 → exclusive lock 선점
likesRepository.save(...);                  // likes 나중 → FK shared lock은 own lock으로 만족
```

데드락을 해소하고 재테스트를 돌렸다. 데드락은 사라졌지만 더 근본적인 문제가 드러났다.

---

### Analyze 2 — Race Condition

**문제:** dirty checking 방식은 read → modify → write 3단계가 분리되어 비원자적이다.

```
TX A: likeCount 읽음(5) → +1 → 6 commit
TX B: likeCount 읽음(5) → +1 → 6 commit  ← A 덮어씀 → 기대값 7, 실제값 6
```

테스트로 직접 확인했다.

```
[dirty checking] likeEntity 수: 100,  likeCount: 6   → 불일치
[@Modifying]     likeEntity 수: 100,  likeCount: 100  → 일치
```

100명이 동시에 좋아요를 눌렀을 때 likes 행은 100개가 정상 저장됐지만 likeCount는 6으로 기록됐다.

**방안 비교**

| 방안 | 미채택 이유 |
|---|---|
| 비관적 락 | SELECT FOR UPDATE 시점부터 COMMIT까지 exclusive lock 보유 → 인기 게시글에 트래픽 집중 시 락 경합 선형 증가. 데드락 해결 방향(Post SELECT 제거)과 역행 |
| 낙관적 락 + @Retryable | 100명 동시 테스트에서 30회 재시도에도 12/100만 커밋 성공, 88건 유실. 고경합 환경에서 재시도가 재시도를 부르는 구조. Post @Version이 RSS·조회수 등 다른 연산과 버전 충돌 발생 |
| Redis 분산락 | DB 결과를 써야 하는 작업이라 DB 왕복을 줄일 수 없음. Redis 락 획득/해제 왕복이 추가로 붙어 비관적 락보다 느렸음(3550ms → 6167ms). Redis 장애 시 좋아요 기능 전체 마비 |
| synchronized | JVM 레벨 락. 서버 2대 이상에서 각 JVM이 서로를 인식하지 못해 무효 |
| **DB UNIQUE + @Modifying** | 두 문제를 가장 단순하게 해결. 추가 의존성 없음 → **채택** |

Redis 분산락은 재고 차감처럼 UNIQUE 제약으로 막을 수 없는 상황에 적합하다.
낙관적 락도 결국 UNIQUE가 필요하므로 @Modifying만으로 충분하다.

---

### Action

- `@UniqueConstraint({"user_id", "post_id"})` — DB 레벨 중복 INSERT 원천 차단
- `@Modifying` JPQL (`likeCount = likeCount + 1`) — DB 원자적 증감으로 lost update 방지
- `DataIntegrityViolationException` catch — 중복 요청 무시. 트랜잭션 롤백으로 likeCount도 함께 원복되어 정합성 유지

---

### Result

| 테스트 | likeEntity 수 | likeCount | 결과 |
|---|---|---|---|
| dirty checking 100명 동시 | 100 | 6 | 불일치 — 문제 증명 |
| @Modifying JPQL 100명 동시 | 100 | 100 | 일치 ✓ |
| 동일 사용자 100회 동시 요청 | 1 | 1 | 중복 차단 ✓ |

**방식별 성능 비교** (100명 동시, 단일 서버 기준 / JVM 워밍업 영향으로 절대값보다 방향성 참고)

| 방식 | 처리시간 | 성공 | 비고 |
|---|---|---|---|
| 낙관적 락 | 1502ms | 12/100 | 빠른 게 아니라 88건 포기한 것 — 데이터 유실 |
| **@Modifying** | **2339ms** | **100/100** | **정합성 + 처리량 모두 확보** |
| synchronized | 2642ms | 100/100 | 단일 JVM에서만 유효 |
| 비관적 락 | 2617ms | 100/100 | lock 보유 시간 길어 고부하 시 불리 |
| Redis 분산락 | 6167ms | 100/100 | DB 왕복에 Redis 왕복 추가 → 가장 느림 |

---

<!-- ## 2. RSS 스케줄러 — 추가 예정 -->

<!-- ## 3. 게시글 조회 성능 개선 — 추가 예정 -->
