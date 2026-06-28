package com.template.elastic.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.elastic.domain.article.Article;
import com.template.elastic.domain.article.repository.ArticleRepository;
import com.template.elastic.domain.member.Member;
import com.template.elastic.domain.member.repository.MemberRepository;
import com.template.elastic.dto.ArticleResponse;
import com.template.elastic.search.document.ArticleDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 대량 초기 데이터 적재 서비스
 * <p>
 * 100만 건의 더미 데이터를 MySQL, Elasticsearch, Redis에 빠르게 적재한다.
 * Kafka 파이프라인을 거치지 않고 직접 삽입하여 적재 시간을 최소화한다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkDataInitService {

    private final JdbcTemplate jdbcTemplate;
    private final ArticleRepository articleRepository;
    private final MemberRepository memberRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /** 카테고리 목록 */
    private static final String[] CATEGORIES = {
            "backend", "frontend", "devops", "database", "mobile", "ai", "security", "etc"
    };

    /** 더미 제목 키워드 풀 */
    private static final String[] TITLE_WORDS = {
            "스프링 부트", "자바 개발", "리액트 활용", "쿠버네티스 배포", "MySQL 최적화",
            "Redis 캐싱 전략", "Elasticsearch 검색", "카프카 메시징", "도커 컨테이너",
            "JPA 성능 튜닝", "REST API 설계", "MSA 아키텍처", "CI/CD 파이프라인",
            "테스트 자동화", "클라우드 네이티브", "보안 인증 구현", "데이터 파이프라인",
            "모바일 앱 개발", "AI 모델 서빙", "GraphQL 도입"
    };

    /** 더미 본문 키워드 풀 */
    private static final String[] CONTENT_FRAGMENTS = {
            "이번 포스팅에서는 스프링 부트를 활용한 개발 방법에 대해 다루겠습니다. ",
            "대용량 데이터를 효율적으로 처리하기 위한 전략을 소개합니다. ",
            "성능 최적화를 위해 인덱스 설계가 매우 중요합니다. ",
            "마이크로서비스 아키텍처에서의 데이터 일관성 유지 방법을 알아봅니다. ",
            "실시간 데이터 처리를 위한 카프카 스트리밍 파이프라인 구축 사례입니다. ",
            "캐싱 전략을 올바르게 적용하면 응답 시간을 획기적으로 단축할 수 있습니다. ",
            "컨테이너 오케스트레이션 도구인 쿠버네티스의 핵심 개념을 정리합니다. ",
            "테스트 코드 작성은 안정적인 서비스 운영의 필수 요소입니다. ",
            "보안 취약점을 사전에 방지하기 위한 인증 및 인가 체계를 구축합니다. ",
            "데이터베이스 샤딩과 레플리케이션 전략에 대해 심층적으로 분석합니다. "
    };

    /** 더미 태그 풀 */
    private static final String[] TAGS = {
            "java", "spring", "jpa", "mysql", "redis", "elasticsearch", "kafka",
            "docker", "kubernetes", "react", "typescript", "python", "devops",
            "backend", "frontend", "database", "security", "ai", "cloud", "msa"
    };

    private final Random random = new Random(42); // 재현 가능한 시드

    /**
     * MySQL에 회원 및 게시글 더미 데이터를 배치 삽입한다.
     *
     * @param memberCount  생성할 회원 수
     * @param articleCount 생성할 게시글 수
     */
    public void initMysqlData(int memberCount, int articleCount) {
        log.info("[MySQL 초기화] 회원 {}명, 게시글 {}건 삽입 시작", memberCount, articleCount);
        long startTime = System.currentTimeMillis();

        // 기존 데이터 초기화
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0;");
        jdbcTemplate.execute("TRUNCATE TABLE articles;");
        jdbcTemplate.execute("TRUNCATE TABLE members;");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1;");

        // 1. 회원 데이터 삽입
        insertMembers(memberCount);
        log.info("[MySQL 초기화] 회원 {}명 삽입 완료", memberCount);

        // 2. 게시글 데이터 배치 삽입
        insertArticlesBatch(memberCount, articleCount);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[MySQL 초기화] 전체 완료! 소요 시간: {}ms ({}초)", elapsed, elapsed / 1000);
    }

    private void insertMembers(int count) {
        String sql = "INSERT INTO members (username, email, nickname, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                ps.setString(1, "user" + (i + 1));
                ps.setString(2, "user" + (i + 1) + "@example.com");
                ps.setString(3, "유저" + (i + 1));
                ps.setObject(4, now);
                ps.setObject(5, now);
            }

            @Override
            public int getBatchSize() {
                return count;
            }
        });
    }

    private void insertArticlesBatch(int memberCount, int articleCount) {
        String sql = "INSERT INTO articles (title, content, category, tags, view_count, member_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        int batchSize = 5000;
        AtomicLong inserted = new AtomicLong(0);

        for (int offset = 0; offset < articleCount; offset += batchSize) {
            int currentBatchSize = Math.min(batchSize, articleCount - offset);
            int finalOffset = offset;

            jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                    int idx = finalOffset + i;
                    String title = generateTitle(idx);
                    String content = generateContent(idx);
                    String category = CATEGORIES[idx % CATEGORIES.length];
                    String tags = generateTags(idx);
                    int viewCount = random.nextInt(10000);
                    long memberId = (idx % memberCount) + 1;
                    // 최근 1년 범위에서 랜덤 날짜 생성
                    LocalDateTime createdAt = LocalDateTime.now().minusDays(random.nextInt(365))
                            .minusHours(random.nextInt(24)).minusMinutes(random.nextInt(60));

                    ps.setString(1, title);
                    ps.setString(2, content);
                    ps.setString(3, category);
                    ps.setString(4, tags);
                    ps.setInt(5, viewCount);
                    ps.setLong(6, memberId);
                    ps.setObject(7, createdAt);
                    ps.setObject(8, createdAt);
                }

                @Override
                public int getBatchSize() {
                    return currentBatchSize;
                }
            });

            long total = inserted.addAndGet(currentBatchSize);
            if (total % 100000 == 0 || total == articleCount) {
                log.info("[MySQL 초기화] 게시글 삽입 진행: {}/{} ({}%)",
                        total, articleCount, (total * 100) / articleCount);
            }
        }
    }

    /**
     * MySQL의 게시글 데이터를 Elasticsearch에 벌크 인덱싱한다.
     */
    public void initElasticsearchData() {
        log.info("[ES 초기화] Elasticsearch 벌크 인덱싱 시작");
        long startTime = System.currentTimeMillis();

        // 기존 인덱스 초기화
        IndexOperations indexOps = elasticsearchOperations.indexOps(ArticleDocument.class);
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.create();
        indexOps.putMapping(indexOps.createMapping());

        Long maxId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM articles", Long.class);
        if (maxId == null) maxId = 0L;

        int chunkSize = 10000;
        long totalIndexed = 0;

        for (long startId = 1; startId <= maxId; startId += chunkSize) {
            long endId = startId + chunkSize - 1;
            List<Article> articles = articleRepository.findByIdBetween(startId, endId);
            if (articles.isEmpty()) continue;

            List<IndexQuery> queries = articles.stream()
                    .map(article -> {
                        ArticleDocument doc = ArticleDocument.from(article);
                        return new IndexQueryBuilder()
                                .withId(String.valueOf(doc.getId()))
                                .withObject(doc)
                                .build();
                    })
                    .toList();

            elasticsearchOperations.bulkIndex(queries, IndexCoordinates.of("articles"));
            totalIndexed += queries.size();

            if (totalIndexed % 100000 == 0 || startId + chunkSize > maxId) {
                log.info("[ES 초기화] 인덱싱 진행: {}/{}", totalIndexed, maxId);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[ES 초기화] 완료! 총 {}건, 소요 시간: {}ms ({}초)", totalIndexed, elapsed, elapsed / 1000);
    }

    /**
     * Redis에 전략 B(Entity 캐시) 및 전략 C(ZSet + Hash) 데이터를 사전 적재한다.
     */
    public void initRedisData() {
        log.info("[Redis 초기화] Redis Warm-up 시작");
        long startTime = System.currentTimeMillis();

        Long maxId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM articles", Long.class);
        if (maxId == null) maxId = 0L;

        int chunkSize = 10000;
        long totalLoaded = 0;

        for (long startId = 1; startId <= maxId; startId += chunkSize) {
            long endId = startId + chunkSize - 1;
            List<Article> articles = articleRepository.findByIdBetween(startId, endId);
            if (articles.isEmpty()) continue;

            // Redis Pipeline을 사용하여 대량 데이터를 효율적으로 적재
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                for (Article article : articles) {
                    try {
                        ArticleResponse response = ArticleResponse.from(article);
                        
                        // RedisTemplate의 직렬화 설정(@class 포함 등)을 그대로 사용하여 byte[] 변환
                        @SuppressWarnings("unchecked")
                        org.springframework.data.redis.serializer.RedisSerializer<Object> serializer =
                                (org.springframework.data.redis.serializer.RedisSerializer<Object>) redisTemplate.getValueSerializer();
                        byte[] serializedValue = serializer.serialize(response);

                        // 전략 B: 개별 Entity 캐시
                        byte[] entityKey = ("article:" + article.getId()).getBytes();
                        connection.stringCommands().set(entityKey, serializedValue);

                        // 전략 C: ZSet에 ID 추가 (score = createdAt epoch second)
                        double score = article.getCreatedAt().toEpochSecond(java.time.ZoneOffset.of("+09:00"));
                        byte[] zsetKey = "articles:zset".getBytes();
                        byte[] member = String.valueOf(article.getId()).getBytes();
                        connection.zSetCommands().zAdd(zsetKey, score, member);

                        // 전략 C: Hash에 직렬화된 데이터 저장
                        byte[] hashKey = ("articles:hash:" + article.getId()).getBytes();
                        connection.stringCommands().set(hashKey, serializedValue);

                    } catch (Exception e) {
                        log.warn("[Redis 초기화] 데이터 적재 실패: articleId={}", article.getId(), e);
                    }
                }
                return null;
            });

            totalLoaded += articles.size();
            if (totalLoaded % 100000 == 0 || startId + chunkSize > maxId) {
                log.info("[Redis 초기화] Warm-up 진행: {}/{}", totalLoaded, maxId);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[Redis 초기화] 완료! 총 {}건, 소요 시간: {}ms ({}초)", totalLoaded, elapsed, elapsed / 1000);
    }

    // === 더미 데이터 생성 헬퍼 메서드 ===

    private String generateTitle(int index) {
        String base = TITLE_WORDS[index % TITLE_WORDS.length];
        return base + " #" + (index + 1) + " - " + TITLE_WORDS[(index + 7) % TITLE_WORDS.length] + " 가이드";
    }

    private String generateContent(int index) {
        StringBuilder sb = new StringBuilder();
        int fragmentCount = 3 + random.nextInt(8); // 3~10개 문장
        for (int i = 0; i < fragmentCount; i++) {
            sb.append(CONTENT_FRAGMENTS[(index + i) % CONTENT_FRAGMENTS.length]);
        }
        return sb.toString();
    }

    private String generateTags(int index) {
        int tagCount = 2 + random.nextInt(4); // 2~5개 태그
        List<String> selectedTags = new ArrayList<>();
        for (int i = 0; i < tagCount; i++) {
            selectedTags.add(TAGS[(index + i * 3) % TAGS.length]);
        }
        return String.join(",", selectedTags);
    }
}
