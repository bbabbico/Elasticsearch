# Elastic

RDB(MySQL), Redis, Elasticsearch를 같은 도메인 데이터에 적용해 조회 성능과 운영 트레이드오프를 비교하는 Spring Boot 기반 벤치마크 프로젝트입니다.

단순히 "어떤 기술이 빠른가"를 보는 프로젝트가 아니라, 게시글 목록 조회, 반복 조회, 키워드 검색, 쓰기와 읽기 혼합, 랜덤 페이지 접근, 복합 필터링 같은 상황별 시나리오에서 어떤 조회 아키텍처가 적합한지 판단 근거를 만드는 것이 목적입니다.

엘라스틱 서치 부분은 클로드 썼습니다
## 주요 기능

- 게시글 CUD API
- MySQL 직접 조회 기준 API
- Redis 기반 3가지 조회 전략 비교
- Elasticsearch 기반 Full-text 검색
- Kafka 이벤트 기반 Redis/Elasticsearch 비동기 동기화
- MySQL, Redis, Elasticsearch 초기 데이터 적재 API
- 6가지 성능 벤치마크 시나리오 API
- 브라우저에서 실행 가능한 간단한 벤치마크 대시보드

## 기술 스택

| 영역 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.0 |
| Build | Gradle |
| RDB | MySQL 8.0 |
| Cache | Redis 7 |
| Search | Elasticsearch 8.15.0, Nori Analyzer |
| Messaging | Kafka, Zookeeper |
| View | Thymeleaf, Chart.js |
| Infra | Docker Compose |

> `implementation_plan.md`에는 Spring Boot 4.1로 적혀 있지만, 실제 프로젝트 설정은 `build.gradle` 기준 Spring Boot 3.4.0입니다.

## 조회 전략

| 전략 | 엔드포인트 | 핵심 방식 | RDB 의존 |
|---|---|---|---|
| RDB 단독 | `/api/v1/rdb/articles` | MySQL/JPA 직접 조회 | 항상 |
| Redis 전략 A | `/api/v1/redis/strategy-a/articles` | 동일 요청 결과 전체를 Redis에 캐싱 | Cache miss 시 |
| Redis 전략 B | `/api/v1/redis/strategy-b/articles` | ID 목록은 RDB, 상세 데이터는 Redis MGET | ID 조회 시 |
| Redis 전략 C | `/api/v1/redis/strategy-c/articles` | Redis ZSet + value 저장소로 목록과 상세 조회 | 없음 |
| Elasticsearch | `/api/v1/elastic/articles` | 역인덱스 기반 검색 및 페이징 | 없음 |

### Redis 전략 차이

- 전략 A는 동일한 요청이 반복될 때 가장 단순하고 빠른 캐시 전략입니다. 대신 쓰기가 잦거나 페이지/검색 조건이 다양하면 캐시 적중률이 급격히 떨어집니다.
- 전략 B는 개별 게시글 캐시 단위로 무효화하기 쉬워 변경 대응이 비교적 단순합니다. 하지만 목록 ID 조회는 여전히 RDB에 의존합니다.
- 전략 C는 목록 정렬과 상세 조회를 모두 Redis에서 처리하므로 읽기 병목을 크게 줄일 수 있습니다. 대신 ZSet, 상세 데이터, 동기화 로직을 직접 관리해야 해서 구현과 운영 비용이 가장 큽니다.
- Elasticsearch는 키워드 검색, 복합 필터, 다양한 정렬 요구가 커질수록 유리합니다.

## 아키텍처

쓰기 요청은 MySQL을 우선 저장소로 사용하고, 트랜잭션 커밋 이후 Kafka 이벤트를 발행해 Redis와 Elasticsearch를 비동기 동기화합니다.

```text
Client
  -> Spring Boot
  -> MySQL 저장
  -> Transaction Commit
  -> @TransactionalEventListener(AFTER_COMMIT)
  -> Kafka article-events
  -> Kafka Consumer
     -> Elasticsearch upsert/delete
     -> Redis Strategy A cache eviction
     -> Redis Strategy B entity cache update/delete
     -> Redis Strategy C ZSet/value update/delete
```

Kafka Consumer는 실패 시 1초 간격으로 3회 재시도하고, 이후 Dead Letter Topic으로 메시지를 넘기도록 `DefaultErrorHandler`가 설정되어 있습니다.

## 실행 방법

### 1. 인프라 실행

```bash
docker compose up -d
```

실행되는 주요 서비스는 다음과 같습니다.

| 서비스 | 포트 |
|---|---:|
| MySQL | 3306 |
| Redis | 6379 |
| Elasticsearch | 9200 |
| Kibana | 5601 |
| Kafka | 9092 |
| Kafka UI | 8088 |

### 2. 애플리케이션 실행

Windows:

```bash
.\gradlew.bat bootRun
```

macOS/Linux:

```bash
./gradlew bootRun
```

애플리케이션 기본 포트는 `8090`입니다.

```text
http://localhost:8090
```

### 3. 데이터 초기화

전체 초기화:

```bash
curl -X POST "http://localhost:8090/api/admin/init/all?members=100&articles=200000"
```

단계별 초기화:

```bash
curl -X POST "http://localhost:8090/api/admin/init/mysql?members=100&articles=200000"
curl -X POST "http://localhost:8090/api/admin/init/elastic"
curl -X POST "http://localhost:8090/api/admin/init/redis"
```

기본 초기 데이터 수는 코드 기준 게시글 `200,000`건입니다. 계획서의 목표 규모는 `1,000,000`건이지만, 로컬 실행 편의성을 위해 API 기본값과 대시보드는 20만 건 기준으로 작성되어 있습니다. 100만 건을 테스트하려면 `articles=1000000`으로 직접 지정하면 됩니다.

## API

### 게시글 쓰기

```http
POST /api/v1/articles
PUT /api/v1/articles/{id}
DELETE /api/v1/articles/{id}
```

생성 요청 예시:

```json
{
  "title": "Spring Boot 검색 성능 테스트",
  "content": "MySQL, Redis, Elasticsearch 조회 성능을 비교합니다.",
  "category": "backend",
  "tags": "spring,redis,elasticsearch",
  "memberId": 1
}
```

### 조회 API

```http
GET /api/v1/rdb/articles?page=0&size=20
GET /api/v1/rdb/articles/search?keyword=스프링&page=0&size=20
GET /api/v1/rdb/articles/filter?category=backend&startDate=2026-01-01T00:00:00&endDate=2026-12-31T23:59:59&page=0&size=20

GET /api/v1/redis/strategy-a/articles?page=0&size=20
GET /api/v1/redis/strategy-a/articles/search?keyword=스프링&page=0&size=20

GET /api/v1/redis/strategy-b/articles?page=0&size=20
GET /api/v1/redis/strategy-c/articles?page=0&size=20

GET /api/v1/elastic/articles?page=0&size=20
GET /api/v1/elastic/articles/search?keyword=스프링&page=0&size=20
```

## 벤치마크

개별 시나리오 실행:

```bash
curl "http://localhost:8090/api/benchmark/scenario/1"
```

전체 시나리오 실행:

```bash
curl "http://localhost:8090/api/benchmark/all"
```

브라우저 대시보드:

```text
http://localhost:8090
```

### 시나리오 목록

| 번호 | 시나리오 | 의도 |
|---:|---|---|
| 1 | Cold Start | 캐시가 비어 있는 최초 조회 성능 비교 |
| 2 | Warm Read | 동일 요청 반복 시 캐시/인덱스 활용 효과 비교 |
| 3 | Keyword Search | RDB LIKE와 Elasticsearch Full-text 검색 비교 |
| 4 | Cache Churn | 쓰기와 읽기가 섞일 때 캐시 전략별 영향 비교 |
| 5 | Random Read | 다양한 페이지에 무작위 접근할 때 캐시 적중률 영향 비교 |
| 6 | Complex Filter | 카테고리, 기간, 정렬 같은 복합 조건 처리 비교 |

벤치마크 결과는 평균 응답 시간, P95, 최소/최대 응답 시간, 반복 횟수를 반환합니다.

## 현재 구현 상태와 주의점

- Elasticsearch는 목록 조회와 키워드 검색 API가 구현되어 있습니다.
- 복합 필터 시나리오에서 Elasticsearch 전용 복합 필터 쿼리는 아직 별도 구현이 필요합니다. 현재 벤치마크 코드는 ES 목록 조회를 대체 측정으로 사용합니다.
- Redis 전략 C는 Redis에 데이터가 warm-up되어 있어야 의미 있는 결과가 나옵니다. `/api/admin/init/redis` 또는 `/api/admin/init/all`을 먼저 실행해야 합니다.
- 전략 A는 `articles::*` 패턴 키 삭제로 캐시를 무효화합니다. 운영 환경에서는 `KEYS` 사용이 위험할 수 있으므로 Scan 기반 삭제나 명시적 키 관리가 더 적절합니다.
- 로컬 Docker 환경에서 20만 건 이상 데이터를 넣으면 장비 성능에 따라 초기화 시간이 길어질 수 있습니다.

## 프로젝트 구조

```text
src/main/java/com/template/elastic
├── admin          # 초기 데이터 적재 API와 서비스
├── benchmark      # 벤치마크 시나리오 실행
├── config         # Redis, Kafka, Cache 설정
├── controller     # 게시글 조회/쓰기 API, View Controller
├── domain         # JPA Entity, Repository
├── dto            # 요청/응답 DTO
├── kafka          # 이벤트, Producer, Consumer
├── search         # Elasticsearch Document, Repository
└── service        # Command 서비스와 조회 전략 서비스
```

## 선택 가이드

| 서비스 상황 | 추천 전략 |
|---|---|
| 소규모 서비스, 단순 목록 조회 | RDB 단독 |
| 동일 목록/검색 요청이 반복됨 | Redis 전략 A |
| 상세 데이터 캐시가 중요하고 변경 단위가 작음 | Redis 전략 B |
| 최신순 피드처럼 읽기가 많고 RDB 부하를 줄여야 함 | Redis 전략 C |
| 키워드 검색이 핵심 기능임 | Elasticsearch |
| 복합 필터와 다양한 정렬이 자주 필요함 | Elasticsearch |

핵심은 Redis와 Elasticsearch를 "RDB보다 빠른 도구"로 뭉뚱그려 보는 것이 아니라, 데이터 접근 패턴과 정합성 요구에 따라 다르게 선택하는 것입니다.
