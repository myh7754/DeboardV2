-- =====================================================
-- /api/posts GET 조회 쿼리 분석
-- =====================================================
USE deboard;
-- 설명: 로그인 상태에 관계없이 public 피드의 게시글이나
--       자신의 게시글을 조회합니다 (페이징 포함)
-- 기본값: page=0, size=10
-- =====================================================

-- ===== 1. 데이터 조회 쿼리 (SELECT) =====
-- 이 쿼리가 실행 계획을 확인할 주요 쿼리입니다
EXPLAIN FORMAT=JSON
SELECT
    p.id,
    p.title,
    p.content,
    COALESCE(u.nickname, ea.`name`) AS nickname,
    p.created_at,
    p.like_count
FROM post p
LEFT JOIN users u ON p.user_id = u.user_id
LEFT JOIN external_author ea ON p.external_author_id = ea.id
LEFT JOIN feed f ON p.feed_id = f.id
WHERE (f.feed_type = 'PUBLIC' OR p.user_id IS NOT NULL)
ORDER BY p.created_at DESC
LIMIT 10 OFFSET 0;

-- ===== 2. 전체 개수 조회 쿼리 (COUNT) =====
-- 페이지네이션을 위한 전체 개수 조회
EXPLAIN FORMAT=JSON
SELECT COUNT(p.id) AS total
FROM post p
LEFT JOIN users u ON p.user_id = u.user_id
LEFT JOIN feed f ON p.feed_id = f.id
WHERE (f.feed_type = 'PUBLIC' OR p.user_id IS NOT NULL);

-- =====================================================
-- 인덱스 확인
-- =====================================================

-- deboard DB의 모든 인덱스를 한번에 확인 (SHOW INDEX 대체)
SELECT
    TABLE_NAME,         -- 인덱스가 걸린 테이블 이름
    INDEX_NAME,         -- 인덱스 이름. PRIMARY면 PK 인덱스
    COLUMN_NAME,        -- 그 인덱스가 어떤 컬럼에 걸려 있는지
                        -- 복합 인덱스(컬럼 2개 이상)면 같은 INDEX_NAME으로 여러 행이 나옴
    NON_UNIQUE,         -- 0 = 중복 불가 (UNIQUE 인덱스, PK), 1 = 중복 허용 (일반 인덱스)
    SEQ_IN_INDEX        -- 복합 인덱스에서 해당 컬럼의 순서, 단일 컬럼 인덱스는 항상 1 복합 인덱스면 1, 2, 3... 순서로 나옴
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = 'deboard'
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- =====================================================
-- 테이블 크기 및 행 수 확인
-- =====================================================
-- EXPLAIN과는 다른 개념. 테이블 자체의 물리적 메타데이터를 조회하는 것.
-- TABLE_ROWS: 테이블의 전체 행 수 (대략적인 값)
-- DATA_LENGTH: 실제 데이터가 차지하는 디스크 크기 (bytes)
-- INDEX_LENGTH: 인덱스가 차지하는 디스크 크기 (bytes)
-- 인덱스가 데이터보다 크면 오버헤드가 심한 것이므로 인덱스 정리가 필요할 수 있음
SELECT
    TABLE_NAME,
    TABLE_ROWS,
    ROUND(DATA_LENGTH / 1024 / 1024, 2) AS data_mb,
    ROUND(INDEX_LENGTH / 1024 / 1024, 2) AS index_mb
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = 'deboard'
  AND TABLE_NAME IN ('post', 'feed', 'user', 'external_author');

-- ===== 권장 인덱스 생성 쿼리 =====
-- 필요시 아래 인덱스를 생성하여 성능을 개선할 수 있습니다

-- 1. feed_type 인덱스 (visibility 조건에서 사용)
ALTER TABLE feed ADD INDEX idx_feed_type (feed_type);

-- 2. post.user_id 인덱스 (visibility 조건에서 사용)
ALTER TABLE post ADD INDEX idx_post_user_id (user_id);

-- 3. post.created_at 인덱스 (ORDER BY에서 사용, 다른 열과 복합)
ALTER TABLE post ADD INDEX idx_post_created_at_id (created_at DESC, id);

-- 4. feed_id 인덱스 (조인 조건에서 사용)
ALTER TABLE post ADD INDEX idx_post_feed_id (feed_id);

-- 5. external_author_id 인덱스 (LEFT JOIN에서 사용)
ALTER TABLE post ADD INDEX idx_post_external_author_id (external_author_id);

-- ===== 인덱스 추가 후 EXPLAIN 비교 =====
-- 쿼리 SQL 자체는 동일하지만, 인덱스 추가 후 EXPLAIN 결과가 달라짐
-- "인덱스 추가 전" EXPLAIN: type=ALL (full table scan)
-- "인덱스 추가 후" EXPLAIN: type=ref 또는 range (인덱스 스캔)
-- 쿼리 자체가 바뀌는 게 아니라 MySQL 실행 계획(plan)이 바뀌는 것임
EXPLAIN FORMAT=JSON
SELECT
    p.id,
    p.title,
    p.content,
    COALESCE(u.nickname, ea.`name`) AS nickname,
    p.created_at,
    p.like_count
FROM post p
LEFT JOIN users u ON p.user_id = u.user_id
LEFT JOIN external_author ea ON p.external_author_id = ea.id
LEFT JOIN feed f ON p.feed_id = f.id
WHERE (f.feed_type = 'PUBLIC' OR p.user_id IS NOT NULL)
ORDER BY p.created_at DESC
LIMIT 10 OFFSET 0;

-- =====================================================
-- 검색 쿼리 포함 시 (keyword 파라미터 사용)
-- =====================================================

-- 예: /api/posts?searchType=title&keyword=Spring&page=0&size=10
EXPLAIN FORMAT=JSON
SELECT
    p.id,
    p.title,
    p.content,
    COALESCE(u.nickname, ea.`name`) AS nickname,
    p.created_at,
    p.like_count
FROM post p
LEFT JOIN users u ON p.user_id = u.user_id
LEFT JOIN external_author ea ON p.external_author_id = ea.id
LEFT JOIN feed f ON p.feed_id = f.id
WHERE (f.feed_type = 'PUBLIC' OR p.user_id IS NOT NULL)
  AND p.title LIKE '%Spring%'
ORDER BY p.created_at DESC
LIMIT 10 OFFSET 0;

-- 검색 조건이 포함된 COUNT 쿼리
EXPLAIN FORMAT=JSON
SELECT COUNT(p.id) AS total
FROM post p
LEFT JOIN feed f ON p.feed_id = f.id
WHERE (f.feed_type = 'PUBLIC' OR p.user_id IS NOT NULL)
  AND p.title LIKE '%Spring%';
