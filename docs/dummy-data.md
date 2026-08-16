# 더미 데이터 삽입 가이드

> DummyDataLoader(Spring CommandLineRunner)는 대량 삽입 시 HikariCP 커넥션 타임아웃 문제가 발생한다.
> 1000만 건 이상은 RDS에 직접 SQL 프로시저로 삽입한다.

---

## 준비

EC2에서 mysql 클라이언트 설치:

```bash
sudo apt install mysql-client-core-8.0 -y
```

---

## SQL 파일 작성

```bash
vi /tmp/insert_posts.sql
```

아래 내용 전체 붙여넣고 저장 (`i` → 붙여넣기 → `Esc` → `:wq`):

```sql
DELIMITER $$

CREATE PROCEDURE insert_dummy_posts()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE batch_size INT DEFAULT 50000;
    DECLARE total INT DEFAULT 10000000;

    WHILE i <= total DO
        INSERT INTO post (like_count, created_at, updated_at, user_id, content, image, title, is_public)
        SELECT 
            0,
            DATE_ADD('2010-01-01 00:00:00', INTERVAL FLOOR(RAND() * 5000) HOUR),
            DATE_ADD('2010-01-01 00:00:00', INTERVAL FLOOR(RAND() * 5000) HOUR),
            1,
            CONCAT('이번 포스팅에서는 ', ELT(FLOOR(RAND() * 20) + 1, 
                '스프링부트','자바','AWS','도커','쿠버네티스','리액트','타입스크립트','파이썬',
                'MySQL','Redis','Kafka','MSA','CI/CD','Git','리눅스','JPA','인덱스','트랜잭션','캐싱','보안'
            ), ' 관련 내용을 정리했습니다. 실무에서 직접 겪은 사례를 바탕으로 작성했습니다.'),
            NULL,
            ELT(FLOOR(RAND() * 20) + 1,
                'Spring Boot 3.x 마이그레이션 삽질기','Java 21 Virtual Thread와 HikariCP',
                'MySQL EXPLAIN으로 쿼리 10배 개선','AWS EC2 OOM 원인과 JVM 튜닝',
                'Docker Compose 로컬 환경 구성','Redis SWR 패턴으로 캐시 stampede 해결',
                'JPA N+1 완전 정복','Kafka Consumer 장애 대응',
                'React Query 서버 상태 관리','TypeScript 제네릭 고급 활용',
                'Kubernetes CrashLoopBackOff 분석','GitHub Actions CI/CD 구축',
                'PostgreSQL vs MySQL 1000만 건 비교','Python asyncio 크롤러 20배 개선',
                'MSA 전환 6개월 회고','Nginx SSL 자동 갱신 설정',
                '클린 아키텍처 실무 적용기','REST API 설계 원칙',
                'JVM GC 튜닝 ZGC 전환','Spring Security JWT 구현'
            ),
            1
        FROM information_schema.columns
        LIMIT batch_size;

        SET i = i + batch_size;

        SELECT CONCAT(i, ' / ', total, ' 완료') AS progress;
    END WHILE;
END$$

DELIMITER ;

CALL insert_dummy_posts();
```

---

## 실행

```bash
mysql -h deboard-test.cjouqsq0ufj5.ap-northeast-2.rds.amazonaws.com -u myh7754 -p deboard < /tmp/insert_posts.sql
```

비밀번호 입력 후 progress가 출력되면서 진행된다.

---

## 완료 후

프로시저 삭제:

```sql
DROP PROCEDURE IF EXISTS insert_dummy_posts;
```

FULLTEXT INDEX 추가:

```sql
ALTER TABLE post ADD FULLTEXT INDEX ft_post_title (title) WITH PARSER ngram;
```

---

## 데이터 확인

```sql
SELECT COUNT(*) FROM post;
SHOW INDEX FROM post;
```
