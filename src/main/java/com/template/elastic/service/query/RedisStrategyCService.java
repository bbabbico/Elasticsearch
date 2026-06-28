package com.template.elastic.service.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.elastic.dto.ArticleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Redis 전략 C 서비스 - ZSet + Hash 기반 완전 Redis 조회
 * <p>
 * RDB 접근 없이 Redis만으로 데이터를 조회한다.
 * ZSet에서 ZREVRANGE로 ID를 페이징 조회하고,
 * Hash에서 각 ID에 대한 상세 JSON 데이터를 가져온다.
 * </p>
 * <ul>
 *     <li>ZSet 키: "articles:zset" (score = createdAt epoch millis)</li>
 *     <li>Hash 키: "articles:hash:{id}" (value = ArticleResponse JSON 문자열)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisStrategyCService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /** ZSet 키 */
    private static final String ZSET_KEY = "articles:zset";

    /** Hash 키 접두사 */
    private static final String HASH_KEY_PREFIX = "articles:hash:";

    /**
     * ZSet에서 ZREVRANGE로 ID 페이징 → Hash에서 상세 데이터 조회
     * <p>
     * RDB 접근을 완전히 차단하고, Redis에 사전 적재된 데이터만으로 결과를 반환한다.
     * </p>
     *
     * @param pageable 페이징 정보
     * @return 게시글 응답 페이지
     */
    public Page<ArticleResponse> getArticles(Pageable pageable) {
        long start = pageable.getOffset();
        long end = start + pageable.getPageSize() - 1;

        // 1. ZSet에서 해당 페이지의 article ID 목록 조회 (createdAt score 역순)
        Set<ZSetOperations.TypedTuple<Object>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(ZSET_KEY, start, end);

        if (tuples == null || tuples.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // 2. 각 ID에 대해 Hash에서 JSON 데이터 조회
        List<ArticleResponse> responses = new ArrayList<>();
        for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
            Object member = tuple.getValue();
            if (member == null) continue;

            String articleId = member.toString();
            String hashKey = HASH_KEY_PREFIX + articleId;

            // Hash에서 JSON 문자열 조회
            Object jsonData = redisTemplate.opsForValue().get(hashKey);
            if (jsonData != null) {
                try {
                    // 3. JSON → ArticleResponse 역직렬화
                    ArticleResponse response;
                    if (jsonData instanceof String jsonString) {
                        response = objectMapper.readValue(jsonString, ArticleResponse.class);
                    } else {
                        response = objectMapper.convertValue(jsonData, ArticleResponse.class);
                    }
                    responses.add(response);
                } catch (JsonProcessingException e) {
                    log.warn("[전략C] JSON 역직렬화 실패: hashKey={}", hashKey, e);
                }
            } else {
                log.warn("[전략C] Hash 데이터 없음: hashKey={}", hashKey);
            }
        }

        // 4. ZSet 전체 크기로 totalElements 계산
        Long totalElements = redisTemplate.opsForZSet().zCard(ZSET_KEY);
        long total = (totalElements != null) ? totalElements : 0L;

        return new PageImpl<>(responses, pageable, total);
    }
}
