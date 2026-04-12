# 좋아요 동시성 문제 — 데드락에서 Race Condition까지

> `toggleLike` 구현 후 동시성 테스트를 작성하는 과정에서 단계적으로 발견한 두 가지 문제와 해결 과정을 정리한다.

---

## P — 문제 발견 과정

### toggleLike 구조

좋아요 토글은 직관적으로 이렇게 동작한다.

```
1. (userId, postId)로 좋아요 레코드 조회
2. 있으면 → likeCount 감소 + 레코드 삭제
3. 없으면 → likeCount 증가 + 레코드 삽입
```

조회와 삽입이 별도 쿼리로 나뉘어 있어 두 요청이 동시에 진입하면 정합성이 깨질 수 있다고 판단했다.

### 동시성 테스트 작성

CountDownLatch로 100개 스레드를 동시에 출발시키는 테스트를 작성했다.

```java
CountDownLatch startLatch = new CountDownLatch(1);
CountDownLatch doneLatch = new CountDownLatch(100);

for (int i = 0; i < 100; i++) {
    executorService.submit(() -> {
        startLatch.await(); // 100개 스레드 동시 출발 대기
        likeService.toggleLike(postId);
        doneLatch.countDown();
    });
}
startLatch.countDown(); // 동시 출발 신호
doneLatch.await();
```

### 테스트 실행 → 데드락 발생

예상했던 race condition보다 먼저 데드락이 터졌다.

```
com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException:
Deadlock found when trying to get lock; try restarting transaction
```

---

## A — 1차: 데드락 분석 및 해결

### 원인: FK shared lock upgrade 충돌

초기 구현 코드 흐름은 이랬다.

```java
Optional<Likes> likes = likesRepository.findByPostIdAndUserId(postId, userId);
if (likes.isPresent()) {
    likesRepository.delete(likes.get());       // Likes 조작
} else {
    likesRepository.save(Likes.toEntity(...)); // Likes INSERT ← FK로 Post에 shared lock
}
int count = likesRepository.countByPostId(postId);
Post post = postService.getPostById(postId);   // Post SELECT
post.setLikeCount(count);                      // commit 시 Post UPDATE (exclusive lock 필요)
```

likes 테이블은 post_id로 posts에 FK를 가지고 있다. InnoDB는 FK 참조 무결성 확인을 위해 **INSERT 시 참조 대상 행(posts)에 shared lock을 획득**한다.

이후 commit 시점에 dirty checking으로 `UPDATE posts`가 실행되는데 exclusive lock이 필요하다.

```
TX A: INSERT likes → posts shared lock 획득
      → commit 시 posts exclusive lock 필요 (TX B가 shared lock 보유 중 → 대기)

TX B: INSERT likes → posts shared lock 획득
      → commit 시 posts exclusive lock 필요 (TX A가 shared lock 보유 중 → 대기)

→ 두 트랜잭션이 각각 shared lock을 쥔 채 upgrade를 기다림 → Deadlock
```

교착상태 4조건 중 **점유대기(hold-and-wait)** 가 성립하고 있었다.

### 해결: posts UPDATE를 먼저 실행

posts에 대한 UPDATE를 likes 조작보다 먼저 실행하면 exclusive lock을 선점할 수 있다. 이후 INSERT likes의 FK shared lock 요청은 자신의 exclusive lock으로 만족되므로 순환 대기가 사라진다.

```java
// 변경 후 — posts UPDATE 먼저, likes 조작 나중
if (likes.isPresent()) {
    postRepository.decreaseLikeCount(postId);  // posts UPDATE 먼저
    likesRepository.delete(likes.get());        // Likes DELETE 나중
} else {
    postRepository.increaseLikeCount(postId);  // posts UPDATE 먼저
    likesRepository.save(...);                  // Likes INSERT 나중
}
```

또한 `getReferenceById`로 User/Post 프록시만 가져와 불필요한 SELECT를 제거했다.

```java
Post post = postService.getPostReferenceById(postId);  // SELECT 없이 프록시만 반환
User user = userService.getUserReferenceById(userId);  // SELECT 없이 프록시만 반환
```

### 데드락 해결 후 재테스트

데드락은 사라졌다. 하지만 같은 사용자가 동시에 요청을 보내면 likes 행이 중복 삽입되는 문제가 남아 있었다. 그리고 더 근본적인 문제가 보이기 시작했다.

---

## A — 2차: Race Condition 분석 및 해결

### 문제 1: likeCount Lost Update

기존 dirty checking 방식은 read → modify → write 3단계가 분리되어 있어 비원자적이다.

```
TX A: post 읽음 (likeCount = 5) ──── +1 ──── likeCount = 6 commit
TX B: post 읽음 (likeCount = 5) ──── +1 ──── likeCount = 6 commit  ← A 덮어씀
→ 기대값: 7, 실제값: 6
```

실제로 테스트로 확인했다.

```
[dirty checking] likeEntity 수: 100, likeCount: 6 → 불일치
```

100명이 동시에 좋아요를 눌렀는데 likes 행은 100개가 정상 저장됐지만 likeCount는 6으로 기록됐다. 좋아요 자체는 되는데 **화면에 표시되는 숫자가 틀리는 문제**다.

### 문제 2: 중복 likes 행 삽입

같은 사용자가 수십 ms 간격으로 동시에 요청을 보내면 두 요청이 모두 "없음"을 읽고 INSERT를 시도한다.

```
TX A: findByPostIdAndUserId → 없음 → INSERT likes
TX B: findByPostIdAndUserId → 없음 → INSERT likes  ← 중복 행 삽입
```

모바일 더블탭, 네트워크 재전송 같은 상황에서 발생할 수 있는 구조적 취약점이다.

### 방안 비교

**방안 1: 비관적 락 (Pessimistic Lock)**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Post p WHERE p.id = :postId")
Optional<Post> findByIdForUpdate(@Param("postId") Long postId);
```

인기 게시글에 트래픽이 몰릴수록 그 행 하나에 락 경합이 집중되어 처리량이 선형으로 떨어진다. 또한 데드락 해결을 위해 Post SELECT를 제거한 방향과 역행한다.

**방안 2: 낙관적 락 + @Retryable**

```java
@Retryable(value = {ObjectOptimisticLockingFailureException.class}, maxAttempts = 3)
public void toggleLike(Long postId) { ... }
```

lost update는 해결되지만 중복 INSERT 문제는 여전히 UNIQUE 제약이 필요하다. 결국 UNIQUE 제약을 쓰면서 낙관적 락을 추가하는 셈인데, Post 엔티티에 `@Version`을 두면 RSS 업데이트, 조회수 등 Post를 건드리는 다른 모든 연산과 버전 충돌이 생긴다.

실측 검증: `@Version`을 추가하고 100명 동시 좋아요 테스트를 돌렸다.

```
[낙관적 락] likeEntity 수: 12, likeCount: 12  ← 88건 데이터 유실
```

30회 재시도 설정에도 100명 중 12명만 커밋에 성공했다. 인기 게시글은 동시 트래픽이 한 행에 집중되는 특성상 낙관적 락은 재시도가 재시도를 부르는 구조가 된다.

**방안 3: Redis 분산락**

```java
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent("lock:likes:" + userId + ":" + postId, "1", Duration.ofSeconds(5));
```

두 문제를 모두 해결하지만 좋아요처럼 결국 DB에 써야 하는 작업에서는 Redis가 속도 이점을 제공하지 못한다. DB 왕복은 어차피 필요하고, Redis 락 획득/해제 왕복이 추가로 붙는다.

실측 결과: 비관적 락 2617ms → Redis 분산락(spin-wait) 6167ms. DB만 쓰는 것보다 느렸다.

Redis 장애 시 좋아요 기능 전체가 마비된다. Redis 분산락이 유효한 케이스는 재고 차감처럼 UNIQUE 제약으로 막을 수 없거나, 외부 API 호출 제한처럼 DB가 없는 자원을 조정해야 하는 상황이다.

**방안 4: synchronized**

```java
public synchronized void toggleLike(Long postId) { ... }
```

`synchronized`는 JVM 메모리의 모니터 락을 사용한다. Spring Bean은 싱글톤이라 서버 1대 안에서는 동일한 인스턴스를 공유하므로 락이 동작한다.

하지만 서버가 2대 이상이면 각 JVM은 독립된 프로세스라 서버 A의 락이 서버 B에 아무 영향을 주지 않는다. DB는 공유하고 있으므로 두 서버가 동시에 같은 행을 수정하는 상황이 그대로 발생한다.

```
서버 A (JVM 1)                서버 B (JVM 2)
┌─────────────────┐           ┌─────────────────┐
│  synchronized   │           │  synchronized   │
│  → 락 획득 ✓   │           │  → 락 획득 ✓   │  ← 서로 모름
└─────────────────┘           └─────────────────┘
        ↓                             ↓
    DB (공유)  ←─────────────────────┘
```

현재 프로젝트가 단일 서버라도 스케일아웃 순간 무력화되는 방어는 설계 단계에서 제외해야 한다.

**방안 5: DB UNIQUE + @Modifying 원자적 SQL (채택)**

두 문제를 각각 가장 단순하게 해결한다.

- **중복 INSERT 방지**: `(user_id, post_id)` UNIQUE 제약으로 DB 레벨에서 차단
- **lost update 방지**: `UPDATE Post SET likeCount = likeCount + 1` 으로 DB가 현재 값 기준 원자적 처리

```java
// 변경 전 — read-modify-write, 간격 존재 → lost update
post.setLikeCount(post.getLikeCount() + 1);

// 변경 후 — DB가 원자적으로 처리, 간격 없음
@Modifying
@Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
void increaseLikeCount(@Param("postId") Long postId);
```

채택 이유:
- 두 문제를 모두 해결하는 유일한 방안이 DB 제약 + 원자적 UPDATE 조합
- 낙관적 락도 결국 UNIQUE가 필요하므로 @Version 추가 없이 @Modifying만으로 충분
- Redis 의존성 없이 DB만으로 정합성 보장
- 정상 경로에서 추가 쿼리 없음

---

## A — 구현

### 1. DB UNIQUE 제약

```java
@Entity
@Table(
    name = "likes",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "post_id"})
    }
)
public class Likes { ... }
```

### 2. 원자적 likeCount 업데이트

```java
@Modifying
@Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
void increaseLikeCount(@Param("postId") Long postId);

@Modifying
@Query("UPDATE Post p SET p.likeCount = p.likeCount - 1 WHERE p.id = :postId AND p.likeCount > 0")
void decreaseLikeCount(@Param("postId") Long postId);
```

`AND p.likeCount > 0` 조건으로 음수 방지도 함께 처리한다.

### 3. 예외 처리

```java
@Transactional
public void toggleLike(Long postId) {
    Long userId = userService.getCurrentUserId();
    try {
        Optional<Likes> like = likesRepository.findByPostIdAndUserId(postId, userId);
        if (like.isPresent()) {
            postRepository.decreaseLikeCount(postId);
            likesRepository.delete(like.get());
        } else {
            Post post = postService.getPostReferenceById(postId);
            User user = userService.getUserReferenceById(userId);
            postRepository.increaseLikeCount(postId);
            likesRepository.save(Likes.toEntity(user, post));
        }
    } catch (DataIntegrityViolationException e) {
        // UNIQUE 제약 위반 = 중복 요청
        // 트랜잭션 롤백으로 increaseLikeCount도 함께 원복 → likeCount 정합성 유지
        log.error("중복 좋아요 요청 차단: userId={}, postId={}", userId, postId);
    }
}
```

`DataIntegrityViolationException` 발생 시 트랜잭션 전체가 롤백되므로 이미 실행된 `increaseLikeCount`도 함께 원복된다. likeCount 정합성이 자동으로 유지된다.

---

## R — 결과

CountDownLatch 기반 동시성 테스트 3종으로 검증했다.

| 테스트 | likeEntity 수 | likeCount | 결과 |
|---|---|---|---|
| [비교] dirty checking 100명 동시 | 100 | 6 | 불일치 — 문제 증명 |
| @Modifying JPQL 100명 동시 | 100 | 100 | 일치 — 정상 |
| 동일 사용자 100회 동시 요청 | 1 | 1 | 중복 차단 확인 |

비관적 락 대비 인기 게시글에서 락 경합 없이 동시 처리량을 유지하면서 데이터 정합성을 보장한다.

---

## R — 성능 비교

정합성 검증 이후, 각 방식을 동일 조건(100명 동시, 단일 서버)에서 `TransactionTemplate`으로 직접 실행해 처리시간을 측정했다.

| 방식 | 처리시간 | 성공 건수 | 비고 |
|---|---|---|---|
| 낙관적 락 (30회 재시도) | 1502ms | 12/100 | 빠른 게 아니라 88건 포기 — 데이터 유실 |
| **@Modifying JPQL** | **2339ms** | **100/100** | **정합성 + 처리량 모두 확보** |
| synchronized | 2642ms | 100/100 | 단일 JVM에서만 유효 |
| 비관적 락 | 2617ms | 100/100 | 완전 직렬화, lock 보유 시간 길어 |
| Redis 분산락 (spin-wait) | 6167ms | 100/100 | DB 왕복 + Redis 왕복 중복 |

> **주의**: 절대 수치는 JVM 워밍업 순서의 영향을 받는다. 방향성 참고용으로 해석해야 한다.

### 낙관적 락 결과 해석

1502ms로 가장 빠르게 보이지만 12건만 실제로 저장됐다. "빠른 것"이 아니라 88개 스레드가 재시도를 포기하고 조기 종료한 것이다. 인기 게시글처럼 트래픽이 한 행에 집중되는 환경에서 낙관적 락은 성능 해법이 아니다.

### Redis 분산락 결과 해석

Redis 자체는 마이크로초(μs) 단위의 인메모리 저장소다. 느린 이유는 Redis가 아니라 구조에 있다.

```
비관적 락:      앱 → DB (1번 왕복)
Redis 분산락:   앱 → Redis (SETNX) → DB → Redis (DEL)  (3번 왕복)
```

좋아요는 결국 DB에 써야 하므로 DB 왕복을 줄일 수 없다. Redis 락 획득/해제 왕복이 추가로 붙어 항상 DB 단독보다 느리다. Redis 분산락이 의미 있는 케이스는 DB가 없는 자원(외부 API 제한, 이메일 중복 발송 방지 등)을 조정할 때다.

---

## 마치며

**테스트가 없으면 보이지 않는 버그가 있다**

동시성 문제는 코드 리뷰만으로 발견하기 어렵다. CountDownLatch로 동시 실행을 강제하는 테스트를 작성하고 나서야 데드락이 드러났고, 데드락을 해결한 뒤에야 그 아래 숨어 있던 race condition이 보였다.

**하나를 고치면 다음 문제가 보인다**

데드락을 해결하자 lost update가 드러났다. 첫 번째 문제가 더 깊은 구조적 취약점을 가리고 있었던 것이다.

**DB를 신뢰하라**

애플리케이션 레이어의 check-then-act 로직은 동시성 환경에서 보장을 제공하지 못한다. DB UNIQUE 제약과 원자적 UPDATE는 어떤 레이어에서 실수가 나와도 최종 보루가 되어준다.
