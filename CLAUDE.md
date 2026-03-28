# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew bootRun        # Run the application (requires MySQL + Redis)
./gradlew build          # Full build (compile + test)
./gradlew test           # Run all tests
./gradlew bootJar        # Build executable JAR
./gradlew bootBuildImage # Build Docker image
```

To run a single test class:
```bash
./gradlew test --tests "org.example.deboardv2.SomeTest"
```

API docs available at `/swagger-ui.html` when running.

## Architecture

**Package structure** under `org.example.deboardv2`:
- `user/` — authentication, JWT, OAuth2 (Google/Kakao/Naver)
- `post/` — core content feed with visibility/access control
- `comment/` — comments on posts
- `likes/` — likes/favorites
- `rss/` — external RSS feed aggregation and scheduling
- `search/` — search functionality
- `redis/` — Redis cache abstraction
- `system/` — cross-cutting: config, base entity, query monitoring

**Layered pattern:** Controller → Service (interface + `Impl/`) → Repository → Entity

**Repository pattern:**
- Spring Data JPA for standard CRUD
- `*CustomRepository` / `*CustomRepositoryImpl` — QueryDSL for complex queries with DTO projections (`@QueryProjection`)
- `*JdbcRepository` — direct JDBC for batch inserts (posts, external authors)

**Base entity:** `system/baseentity/BaseEntity` manages `createdAt`/`updatedAt` via JPA lifecycle callbacks (`@PrePersist`/`@PreUpdate`), not `@EntityListeners`.

## Key Configuration

**Active profile:** `local` (set in `application.yml`)
- `application-local.yml` — MySQL + Redis + OAuth2 credentials
- `application-docker.yml` — Docker environment
- `application-test.yml` — Test environment

**Prerequisites for local dev:**
- Java 21
- MySQL on `localhost:3306`, database `deboard`
- Redis on `localhost:6379`

**DDL:** `ddl-auto: create` — tables are auto-created on startup.

## Notable Patterns

**Virtual threads (Java 21):** RSS network fetching uses an unbounded virtual thread executor (`fetchRssExecutor`). DB batch operations use a semaphore-limited platform thread pool (`rssTaskExecutor`, max 40 concurrent DB connections).

**QueryDSL:** `QueryDSLConfig` exposes a `JPAQueryFactory` bean. Custom repository impls inject this for type-safe queries. Visibility/access control logic uses `BooleanExpression` composition.

**Batch inserts:** `rewriteBatchedStatements=true` on the JDBC URL. Hibernate `batch_size: 1000` with `order_inserts/order_updates: true`. Critical batch paths use JDBC repos directly to bypass JPA overhead.

**Query monitoring:** `DataSourceProxyConfig` wraps the DataSource to intercept and log all SQL (HQL, JDBC, batch) — useful for N+1 detection.

**Caching:** Redis (Lettuce) for feed data; Caffeine for local error-log sampling.

**Security:** JWT (access: 30min, refresh: 1h) + OAuth2. Most GET endpoints are open; mutations require authentication. JWT filter is registered in `SecurityConfig`.

**Logging:** Console at DEBUG; file output in `logs/` as JSON (Logstash encoder), WARN+, 30-day rotation.
