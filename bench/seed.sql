-- Deboard 벤치 시드 SQL
--
-- 전제: 백엔드가 한 번 떠서 (1) ddl-auto=update로 스키마를 만들고
--       (2) DummyDataLoader가 users 3,000 / feed 50 / feed_subscription / post 500,000을
--       넣은 직후 상태. run.ps1 / run.sh 가 그 시점까지 기다렸다가 이 파일을 실행한다.
--
-- 하는 일은 두 가지뿐이다.
--   1) 500,000건의 created_at을 펼친다
--   2) 자기 자신을 복제해 목표 건수까지 증식한다

-- ────────────────────────────────────────────────────────────────
-- 1) created_at 펼치기
--
-- DummyDataLoader는 LocalDateTime now를 루프 밖에서 한 번만 만들어서
-- 500,000건 전부가 완전히 같은 시각을 갖는다.
-- 그대로 두면 keyset 커서의 (created_at, id) 비교가 항상 두 번째 갈래
-- (created_at = ? AND id < ?)로만 떨어져 실제 서비스와 다른 걸 재게 된다.
-- id 기준 1초 간격으로 흩뿌려 created_at이 실제로 정렬 키 역할을 하게 만든다.
-- ────────────────────────────────────────────────────────────────
UPDATE post SET created_at = TIMESTAMPADD(SECOND, id, '2010-01-01 00:00:00');

-- ────────────────────────────────────────────────────────────────
-- 2) 증식 프로시저
--
-- INSERT ... SELECT FROM post 로 한 라운드에 최대 2배씩 늘린다.
-- 500,000 → 1M → 2M → 4M → 8M → 10M, 다섯 라운드면 끝난다.
-- (docs/dummy-data.md의 기존 프로시저는 information_schema.columns에서 뽑는데
--  그 테이블 행 수가 5만보다 훨씬 적어, 진행률만 1000만이라 찍히고 실제로는
--  100만 건 남짓만 들어간다. 여기서는 그 방식을 쓰지 않는다.)
--
-- created_at은 원본 행의 id에 현재 건수(cur)를 더해 만든다.
-- 원본 id가 1..cur이므로 새 행의 시각은 cur+1..2*cur 구간에 들어가
-- 기존 행(1..cur)과 절대 겹치지 않는다. 즉 전 구간 유니크가 유지된다.
--
-- link는 unique 컬럼이라 건드리지 않는다(전부 NULL이면 유니크 제약에 걸리지 않음).
-- ────────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS bench_fill_posts;

DELIMITER $$
CREATE PROCEDURE bench_fill_posts(IN target BIGINT)
BEGIN
    DECLARE cur  BIGINT DEFAULT 0;
    DECLARE need BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO cur FROM post;

    IF cur = 0 THEN
        SELECT 'post 테이블이 비어 있습니다. 백엔드를 먼저 띄워 DummyDataLoader를 돌리세요.' AS error;
    END IF;

    WHILE cur > 0 AND cur < target DO
        -- 남은 양과 현재 건수 중 작은 쪽. 마지막 라운드에서 목표를 넘지 않게 잘라준다.
        SET need = LEAST(cur, target - cur);

        INSERT INTO post (like_count, created_at, updated_at, user_id, content, image, title, is_public)
        SELECT 0,
               TIMESTAMPADD(SECOND, id + cur, '2010-01-01 00:00:00'),
               TIMESTAMPADD(SECOND, id + cur, '2010-01-01 00:00:00'),
               user_id,
               content,
               NULL,
               title,
               is_public
        FROM post
        LIMIT need;

        SET cur = cur + ROW_COUNT();
        SELECT CONCAT('post ', cur, ' / ', target) AS progress;
    END WHILE;
END$$
DELIMITER ;
