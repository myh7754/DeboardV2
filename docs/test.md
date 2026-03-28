# Test Checklist

> Generated: 2026-03-29
> Total: 15 items (7 unit, 8 integration)
> Sub-items: 80 scenarios

---

## Group A — Unit Tests

### 🔴 High Priority

- [x] **JwtTokenProviderTest**
  - **Target:** `src/main/java/org/example/deboardv2/user/service/JwtTokenProvider.java`
  - **Test file:** `src/test/java/org/example/deboardv2/user/service/JwtTokenProviderTest.java`
  - **Dependencies:** none
  - **What to test:**
    - [x] `issue()` → 생성된 토큰에서 subject(userId), role claim 정상 파싱
    - [x] `validateToken()` — 유효한 토큰 → true
    - [x] `validateToken()` — 만료된 토큰 → false
    - [x] `validateToken()` — 잘못된 서명 → false
    - [x] `parseJwt()` — subject, role 정확히 추출
    - [x] `tokenAddCookie()` — accessToken path="/", refreshToken path="/api/auth/refresh"
    - [x] `tokenAddCookie()` — httpOnly=true, sameSite="Strict" 속성 확인

- [x] **NaverRssParserTest**
  - **Target:** `src/main/java/org/example/deboardv2/rss/parser/Impl/NaverRssParser.java`
  - **Test file:** `src/test/java/org/example/deboardv2/rss/parser/NaverRssParserTest.java`
  - **Dependencies:** none (SyndEntry mock)
  - **Fixtures:** `src/test/resources/fixtures/rss/naver_normal.xml`, `naver_no_author.xml`
  - **What to test:**
    - [x] `supports()` — d2.naver.com 포함 URL → true, 그 외 → false
    - [x] `resolve()` — /d2.atom 없으면 추가, 이미 있으면 그대로
    - [x] `resolve()` — trailing slash 제거
    - [x] `parse()` — `entry.getContents()` 있을 때 content 정상 추출
    - [x] `parse()` — author 없을 때 폴백 "NAVER D2" 반환
    - [x] `parse()` — publishedDate null → publishedAt null

- [x] **TistoryRssParserTest**
  - **Target:** `src/main/java/org/example/deboardv2/rss/parser/Impl/TistoryRssParser.java`
  - **Test file:** `src/test/java/org/example/deboardv2/rss/parser/TistoryRssParserTest.java`
  - **Dependencies:** none (SyndEntry mock)
  - **Fixtures:** `src/test/resources/fixtures/rss/tistory_normal.xml`, `tistory_no_description.xml`
  - **What to test:**
    - [x] `supports()` — tistory.com 포함 URL → true
    - [x] `resolve()` — /rss 없으면 추가, 이미 있으면 그대로, trailing slash 제거
    - [x] `parse()` — description 있을 때 정상 추출
    - [x] `parse()` — description null → "(내용 없음)"
    - [x] `parse()` — publishedDate null → publishedAt null

- [x] **VelogRssParserTest**
  - **Target:** `src/main/java/org/example/deboardv2/rss/parser/Impl/VelogRssParser.java`
  - **Test file:** `src/test/java/org/example/deboardv2/rss/parser/VelogRssParserTest.java`
  - **Dependencies:** none (SyndEntry mock)
  - **Fixtures:** `src/test/resources/fixtures/rss/velog_normal.xml`
  - **What to test:**
    - [x] `supports()` — velog.io 포함 URL → true
    - [x] `resolve()` — `@username` 추출 → `https://v2.velog.io/rss/{username}` 조합
    - [x] `resolve()` — `@` 없는 URL → null 반환 (NPE 지뢰 확인)
    - [x] `parse()` — link에서 `@username` 추출 → "velog@{username}"
    - [x] `parse()` — URI 파싱 불가 시 → "velog@unknown"

- [x] **KakaoRssParserTest**
  - **Target:** `src/main/java/org/example/deboardv2/rss/parser/Impl/KakaoRssParser.java`
  - **Test file:** `src/test/java/org/example/deboardv2/rss/parser/KakaoRssParserTest.java`
  - **Dependencies:** none (Element 직접 생성)
  - **Fixtures:** `src/test/resources/fixtures/rss/kakao_with_thumbnail.xml`, `kakao_no_thumbnail.xml`
  - **What to test:**
    - [x] `supports()` — tech.kakao.com/blog 포함 → true
    - [x] `resolve()` — /feed 없으면 추가, trailing slash 처리
    - [x] `parse(entry, element)` — thumbnail element 있을 때 `<img>` 포함 HTML 생성
    - [x] `parse(entry, element)` — element null → 이미지 없는 HTML 생성

- [x] **WoowahanRssParserTest**
  - **Target:** `src/main/java/org/example/deboardv2/rss/parser/Impl/WoowahanRssParser.java`
  - **Test file:** `src/test/java/org/example/deboardv2/rss/parser/WoowahanRssParserTest.java`
  - **Dependencies:** none (SyndEntry mock)
  - **Fixtures:** `src/test/resources/fixtures/rss/woowahan_normal.xml`, `woowahan_no_content.xml`
  - **What to test:**
    - [x] `supports()` — techblog.woowahan.com 포함 → true
    - [x] `resolve()` — /feed 추가, trailing slash 처리
    - [x] `parse()` — contents 있을 때 첫 번째 값 추출
    - [x] `parse()` — contents 없을 때 → "(내용 없음)"

- [x] **RssParserServiceSelectTest**
  - **Target:** `src/main/java/org/example/deboardv2/rss/service/RssParserService.java`
  - **Test file:** `src/test/java/org/example/deboardv2/rss/service/RssParserServiceSelectTest.java`
  - **Dependencies:** none (파서 목록 직접 주입)
  - **What to test:**
    - [x] 각 지원 도메인 URL → 올바른 파서 선택 (Naver/Tistory/Velog/Kakao/Woowahan)
    - [x] 미지원 URL → `IllegalArgumentException`

---

## Group B — Integration Tests

### 🔴 High Priority

- [x] **AuthServiceIntegrationTest**
  - **Target:** `src/main/java/org/example/deboardv2/user/service/impl/AuthServiceImpl.java`
  - **Test file:** `src/test/java/org/example/deboardv2/user/service/AuthServiceIntegrationTest.java`
  - **Dependencies:** MySQL, Redis
  - **Setup required:** 테스트용 User 사전 등록, Redis 인증 코드 세팅
  - **What to test:**
    - [x] `signUp()` — 이메일 미인증 상태 → `EMAIL_NOT_VERIFIED` 예외
    - [x] `signUp()` — 인증 완료 후 정상 가입 → User DB 저장 확인
    - [x] `signUp()` — 닉네임 중복 → `NICKNAME_DUPLICATED` 예외
    - [x] `signIn()` — 존재하지 않는 이메일 → `EMAIL_MISMATCH` 예외
    - [x] `signIn()` — 비밀번호 불일치 → `PASSWORD_MISMATCH` 예외
    - [x] `signIn()` — 정상 → accessToken + refreshToken 반환
    - [x] `sendEmailAuthCode()` — 중복 이메일 → `EMAIL_DUPLICATED` 예외
    - [x] `sendEmailAuthCode()` — 신규 이메일 → Redis에 인증 코드 저장 확인
    - [x] `validEmail()` — 올바른 코드 → `certified:` 키 저장, 코드 키 삭제 확인
    - [x] `validEmail()` — 잘못된 코드 → `EMAIL_VERIFICATION_ERROR` 예외
    - [x] `reissue()` — 유효한 refresh → 새 accessToken 반환
    - [x] `reissue()` — 블랙리스트 등록된 refresh → `INVALID_REFRESH_TOKEN` 예외
    - [x] `logout()` — refresh를 Redis 블랙리스트에 저장 확인
    - [x] `authCheck()` — POST 작성자 본인 → 통과 / 타인 → `FORBIDDEN` 예외

- [x] **PostCustomRepositoryTest**
  - **Target:** `src/main/java/org/example/deboardv2/post/repository/PostCustomRepositoryImpl.java`
  - **Test file:** `src/test/java/org/example/deboardv2/post/repository/PostCustomRepositoryTest.java`
  - **Dependencies:** MySQL
  - **Setup required:** PUBLIC/PRIVATE Feed, User, ExternalAuthor, Post, FeedSubscription 사전 데이터
  - **What to test:**
    - [x] `findAll()` — 비인증 → PUBLIC 피드 + author 있는 게시글만 노출
    - [x] `findAll()` — 인증 + 구독 → PRIVATE 피드 게시글 포함
    - [x] `findAll()` — PRIVATE 피드 → 비구독자에게 미노출
    - [x] `searchPost()` — type=title, keyword 매칭 확인
    - [x] `searchPost()` — type=content, keyword 매칭 확인
    - [x] `searchPost()` — type=author, User nickname + ExternalAuthor name 둘 다 검색
    - [x] `searchPost()` — type=titleContent, 제목 OR 내용 검색
    - [x] `searchPost()` — 빈 keyword → 전체 조회 (가시성 필터만 적용)
    - [x] `findLikesPosts()` — 비인증 → 빈 페이지
    - [x] `findLikesPosts()` — 인증 → 본인 좋아요 게시글만 반환
    - [x] `searchLikePosts()` — 좋아요 게시글 중 keyword 검색

- [ ] **LikesServiceIntegrationTest**
  - **Target:** `src/main/java/org/example/deboardv2/likes/service/Impl/LikesServiceImpl.java`
  - **Test file:** `src/test/java/org/example/deboardv2/likes/service/LikesServiceIntegrationTest.java`
  - **Dependencies:** MySQL
  - **Setup required:** User, Post 사전 등록
  - **What to test:**
    - [ ] `toggleLike()` — 좋아요 없을 때 → Likes 추가 + likeCount +1
    - [ ] `toggleLike()` — 좋아요 있을 때 → Likes 삭제 + likeCount -1
    - [x] `getLikeStatus()` — 비인증 → false
    - [ ] `getLikeStatus()` — 좋아요한 게시글 → true / 안한 게시글 → false

- [ ] **LikesConcurrencyTest**
  - **Target:** `src/main/java/org/example/deboardv2/likes/service/Impl/LikesServiceImpl.java`
  - **Test file:** `src/test/java/org/example/deboardv2/likes/service/LikesConcurrencyTest.java`
  - **Dependencies:** MySQL
  - **Setup required:** User 100명, Post 1개 사전 등록
  - **What to test:**
    - [ ] `CountDownLatch` + `ExecutorService` — 100개 스레드 동시 toggleLike → 완료 후 likeCount가 실제 Likes 행 수와 일치
    - [ ] 같은 userId 100개 동시 요청 → Likes 레코드 최대 1개, `DataIntegrityViolationException` 무시 처리 확인

- [x] **CommentsIntegrationTest**
  - **Target:** `src/main/java/org/example/deboardv2/comment/service/Impl/CommentsServiceImpl.java`
  - **Test file:** `src/test/java/org/example/deboardv2/comment/service/CommentsIntegrationTest.java`
  - **Dependencies:** MySQL
  - **Setup required:** User, Post, 부모 댓글 사전 등록
  - **What to test:**
    - [x] `createComments()` — parentId null → 부모 댓글 생성
    - [x] `createComments()` — parentId 지정 → 대댓글 생성, parent 연관 확인
    - [x] `createComments()` — 존재하지 않는 parentId → `COMMENT_NOT_FOUND`
    - [x] `updateComments()` — 본인 댓글 → 내용 수정 성공
    - [x] `updateComments()` — 타인 댓글 → `FORBIDDEN` 예외
    - [x] `deleteComments()` — 본인 댓글 → 삭제 성공
    - [x] `deleteComments()` — 타인 댓글 → `FORBIDDEN` 예외
    - [x] `readComments()` — 부모 댓글만 조회, repliesCount 정확성
    - [x] `replies()` — 특정 부모의 대댓글 목록, repliesCount=0

- [ ] **RedisServiceIntegrationTest**
  - **Target:** `src/main/java/org/example/deboardv2/redis/service/RedisServiceImpl.java`
  - **Test file:** `src/test/java/org/example/deboardv2/redis/service/RedisServiceIntegrationTest.java`
  - **Dependencies:** Redis
  - **Setup required:** 각 테스트 후 키 정리 (@AfterEach)
  - **What to test:**
    - [x] `setValue/getValue` — 저장 후 정상 조회
    - [x] `setValueWithExpire()` — TTL 만료 후 null 반환
    - [x] `checkAndDelete()` — 키 존재 시 삭제 후 true 반환
    - [x] `checkAndDelete()` — 키 없을 때 false 반환
    - [x] `addToSet()` — maxSize 초과 시 오래된 항목 삭제, 크기 유지
    - [ ] `addAllToZSet()` — 배치 추가 후 maxSize 유지 확인
    - [x] `checkLinksExistence()` — 존재/비존재 Boolean 리스트 정확성

### 🟡 Medium Priority

- [x] **FeedServiceIntegrationTest**
  - **Target:** `src/main/java/org/example/deboardv2/rss/service/FeedService.java`
  - **Test file:** `src/test/java/org/example/deboardv2/rss/service/FeedServiceIntegrationTest.java`
  - **Dependencies:** MySQL, Redis
  - **Setup required:** User 인증 컨텍스트, Feed 사전 등록
  - **What to test:**
    - [x] `registerFeed()` — 중복 URL → `DUPLICATED_FEED` 예외
    - [x] `registerFeed()` — 신규 URL → Feed 저장, PUBLIC 타입으로 생성
    - [x] `subscribeUserFeed()` — 이미 구독 → `ALREADY_SUBSCRIBED` 예외
    - [x] `subscribeUserFeed()` — 신규 구독 → FeedSubscription 저장
    - [x] `unsubscribe()` — 타인 구독 삭제 시도 → `FORBIDDEN` 예외
    - [x] `unsubscribe()` — PRIVATE 피드 마지막 구독자 탈퇴 → Feed + 게시글 전부 삭제
    - [x] `disableFeeds()` — 실패 피드 ID 목록 → isActive=false 처리

- [ ] **ControllerApiTest**
  - **Target:** `AuthController`, `PostController`, `CommentsController`, `FeedController`
  - **Test file:** `src/test/java/org/example/deboardv2/controller/ControllerApiTest.java`
  - **Dependencies:** MockMvc (Spring Security 컨텍스트 포함)
  - **Setup required:** MockMvc 빈, 인증 토큰 픽스처
  - **What to test:**
    - [ ] `POST /api/auth/signup` — @NotBlank 위반 (빈 닉네임/비밀번호/이메일) → 400
    - [ ] `POST /api/auth/signup` — @Email 위반 → 400
    - [ ] `POST /api/auth/signup` — @Size 위반 (닉네임 1자, 비밀번호 7자) → 400
    - [ ] `POST /api/auth/signin` — @Valid 위반 → 400
    - [x] `POST /api/auth/signin` — 정상 → 200 + `Set-Cookie` 헤더 accessToken/refreshToken 포함
    - [x] `POST /api/posts` — 비인증 요청 → 401
    - [ ] `POST /api/posts` — 인증 + @NotBlank title/content 위반 → 400
    - [x] `PUT /api/posts/{id}` — 비인증 → 401
    - [x] `DELETE /api/posts/{id}` — 비인증 → 401
    - [x] `GET /api/posts` — 비인증 → 200 (공개 엔드포인트)
    - [x] `POST /api/comments` — 비인증 → 401
    - [x] `DELETE /api/comments/{id}` — 비인증 → 401
    - [x] `POST /api/rss/feed` — 비인증 → 401
    - [x] `POST /api/rss/user-feed` — 비인증 → 401

---

## Status

| Category    | Total | Done | Remaining |
|-------------|-------|------|-----------|
| Unit        | 7     | 7    | 0         |
| Integration | 8     | 5    | 3         |
| **Total**   | 15    | 12   | 3         |
