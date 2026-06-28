package com.template.elastic.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.elastic.config.KafkaConfig;
import com.template.elastic.dto.ArticleResponse;
import com.template.elastic.kafka.event.ArticleEvent;
import com.template.elastic.search.document.ArticleDocument;
import com.template.elastic.search.repository.ArticleSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.Set;

/**
 * 게시글 동기화 Kafka Consumer
 * <p>
 * Kafka로부터 게시글 이벤트를 수신하여 다음 저장소들을 동기화한다:
 * <ul>
 *     <li>Elasticsearch: 검색 인덱스 동기화</li>
 *     <li>Redis 전략 A: Spring Cache 무효화 (articles::* 패턴)</li>
 *     <li>Redis 전략 B: 개별 Entity 캐시 저장/삭제 (article:{id})</li>
 *     <li>Redis 전략 C: ZSet + Hash 원자적 업데이트 (MULTI/EXEC)</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArticleSyncConsumer {

    private final ArticleSearchRepository articleSearchRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, String> stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Kafka 이벤트를 수신하여 이벤트 타입에 따라 동기화를 수행한다.
     *
     * @param event 수신된 게시글 이벤트
     */
    @KafkaListener(topics = KafkaConfig.ARTICLE_TOPIC, groupId = "elastic-sync-group")
    public void consume(ArticleEvent event) {
        log.info("[Kafka Consumer] 이벤트 수신: type={}, articleId={}",
                event.getEventType(), event.getArticleId());

        switch (event.getEventType()) {
            case CREATE -> handleCreate(event);
            case UPDATE -> handleUpdate(event);
            case DELETE -> handleDelete(event);
        }
    }

    /**
     * CREATE 이벤트 처리: ES 저장 + 캐시 무효화 + 개별 캐시 저장 + 전략 C 동기화
     */
    private void handleCreate(ArticleEvent event) {
        syncToElasticsearch(event);
        evictStrategyCaches();
        saveEntityCache(event);
        syncStrategyC(event);
        log.info("[Kafka Consumer] CREATE 동기화 완료: articleId={}", event.getArticleId());
    }

    /**
     * UPDATE 이벤트 처리: ES 갱신 + 캐시 무효화 + 개별 캐시 갱신 + 전략 C 갱신
     */
    private void handleUpdate(ArticleEvent event) {
        syncToElasticsearch(event);
        evictStrategyCaches();
        saveEntityCache(event);
        updateStrategyC(event);
        log.info("[Kafka Consumer] UPDATE 동기화 완료: articleId={}", event.getArticleId());
    }

    /**
     * DELETE 이벤트 처리: ES 삭제 + 캐시 무효화 + 개별 캐시 삭제 + 전략 C 삭제
     */
    private void handleDelete(ArticleEvent event) {
        articleSearchRepository.deleteById(event.getArticleId());
        evictStrategyCaches();
        redisTemplate.delete("article:" + event.getArticleId());
        removeStrategyC(event.getArticleId());
        log.info("[Kafka Consumer] DELETE 동기화 완료: articleId={}", event.getArticleId());
    }

    // ==========================================================
    // 동기화 헬퍼 메서드
    // ==========================================================

    /**
     * Elasticsearch에 게시글 문서를 저장/갱신한다.
     *
     * @param event 게시글 이벤트
     */
    private void syncToElasticsearch(ArticleEvent event) {
        ArticleDocument doc = ArticleDocument.builder()
                .id(event.getArticleId())
                .title(event.getTitle())
                .content(event.getContent())
                .category(event.getCategory())
                .tags(event.getTags())
                .viewCount(event.getViewCount())
                .memberId(event.getMemberId())
                .memberNickname(event.getMemberNickname())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
        articleSearchRepository.save(doc);
    }

    /**
     * 전략 A: Spring Cache의 게시글 관련 캐시를 모두 무효화한다.
     * <p>
     * "articles::*" 패턴에 해당하는 모든 Redis 키를 삭제한다.
     * </p>
     */
    private void evictStrategyCaches() {
        Set<String> keys = redisTemplate.keys("articles::*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("[Redis 전략 A] 캐시 무효화 완료: {} 개 키 삭제", keys.size());
        }
    }

    /**
     * 전략 B: 개별 게시글을 Redis에 캐싱한다.
     * <p>
     * "article:{id}" 키로 ArticleResponse 객체를 저장한다.
     * </p>
     *
     * @param event 게시글 이벤트
     */
    private void saveEntityCache(ArticleEvent event) {
        String key = "article:" + event.getArticleId();
        ArticleResponse response = ArticleResponse.builder()
                .id(event.getArticleId())
                .title(event.getTitle())
                .content(event.getContent())
                .category(event.getCategory())
                .tags(event.getTags())
                .viewCount(event.getViewCount())
                .memberNickname(event.getMemberNickname())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
        redisTemplate.opsForValue().set(key, response);
        log.debug("[Redis 전략 B] 개별 캐시 저장 완료: key={}", key);
    }

    /**
     * 전략 C: Redis MULTI/EXEC 트랜잭션으로 ZSet과 Hash를 원자적으로 업데이트한다.
     * <p>
     * ZSet에는 articleId를 멤버, createdAt의 epoch second를 score로 저장하여
     * 시간순 정렬 조회를 지원한다.
     * Hash에는 ArticleResponse를 JSON으로 직렬화하여 저장한다.
     * </p>
     *
     * @param event 게시글 이벤트
     */
    @SuppressWarnings("unchecked")
    private void syncStrategyC(ArticleEvent event) {
        String zsetKey = "articles:zset";
        String hashKey = "articles:hash:" + event.getArticleId();
        double score = event.getCreatedAt().toEpochSecond(ZoneOffset.of("+09:00"));

        redisTemplate.execute(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.opsForZSet().add(zsetKey, String.valueOf(event.getArticleId()), score);

                try {
                    String json = objectMapper.writeValueAsString(
                            ArticleResponse.builder()
                                    .id(event.getArticleId())
                                    .title(event.getTitle())
                                    .content(event.getContent())
                                    .category(event.getCategory())
                                    .tags(event.getTags())
                                    .viewCount(event.getViewCount())
                                    .memberNickname(event.getMemberNickname())
                                    .createdAt(event.getCreatedAt())
                                    .updatedAt(event.getUpdatedAt())
                                    .build()
                    );
                    operations.opsForValue().set(hashKey, json);
                } catch (Exception e) {
                    log.error("[Redis 전략 C] 직렬화 실패: articleId={}", event.getArticleId(), e);
                }

                return operations.exec();
            }
        });
        log.debug("[Redis 전략 C] ZSet + Hash 동기화 완료: articleId={}", event.getArticleId());
    }

    /**
     * 전략 C: 업데이트 시 ZSet score는 유지하고 Hash 값만 갱신한다.
     * <p>
     * 업데이트 시에도 동일한 MULTI/EXEC 트랜잭션으로 원자성을 보장한다.
     * </p>
     *
     * @param event 게시글 이벤트
     */
    private void updateStrategyC(ArticleEvent event) {
        syncStrategyC(event);
    }

    /**
     * 전략 C: Redis MULTI/EXEC 트랜잭션으로 ZSet과 Hash를 원자적으로 삭제한다.
     *
     * @param articleId 삭제할 게시글 ID
     */
    @SuppressWarnings("unchecked")
    private void removeStrategyC(Long articleId) {
        String zsetKey = "articles:zset";
        String hashKey = "articles:hash:" + articleId;

        redisTemplate.execute(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.opsForZSet().remove(zsetKey, String.valueOf(articleId));
                operations.delete(hashKey);
                return operations.exec();
            }
        });
        log.debug("[Redis 전략 C] ZSet + Hash 삭제 완료: articleId={}", articleId);
    }
}
