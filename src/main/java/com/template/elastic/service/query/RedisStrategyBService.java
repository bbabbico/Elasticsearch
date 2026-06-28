package com.template.elastic.service.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.elastic.domain.article.Article;
import com.template.elastic.domain.article.repository.ArticleRepository;
import com.template.elastic.dto.ArticleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Redis 전략 B 서비스 - 개별 엔티티 캐싱 (Look-Aside)
 * <p>
 * ID 목록은 RDB에서 페이징 조회하고, 개별 엔티티 데이터는 Redis에서 조회한다.
 * Cache Miss인 ID에 대해서만 RDB를 조회하고, 결과를 Redis에 적재한다.
 * 키 패턴: "article:{id}" → ArticleResponse JSON
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RedisStrategyBService {

    private final ArticleRepository articleRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /** Redis 캐시 키 접두사 */
    private static final String CACHE_KEY_PREFIX = "article:";

    /** 캐시 TTL (분) */
    private static final long CACHE_TTL_MINUTES = 30;

    /**
     * ID만 RDB에서 페이징 조회 → Redis에서 개별 엔티티 캐시 조회(MGET) → 조립
     * <p>
     * 1. articleRepository.findArticleIds(pageable)로 ID 목록 조회<br>
     * 2. Redis에서 "article:{id}" 키로 multiGet<br>
     * 3. Cache Miss인 ID만 articleRepository.findAllById(missedIds)로 조회<br>
     * 4. 조회된 결과를 Redis에 캐시하고 합쳐서 반환
     * </p>
     *
     * @param pageable 페이징 정보
     * @return 게시글 응답 페이지
     */
    public Page<ArticleResponse> getArticles(Pageable pageable) {
        // 1. ID 목록 페이징 조회 (커버링 인덱스 활용)
        Page<Long> idPage = articleRepository.findArticleIds(pageable);
        List<Long> ids = idPage.getContent();

        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
        }

        // 2. Redis에서 MGET으로 일괄 조회
        List<String> keys = ids.stream()
                .map(id -> CACHE_KEY_PREFIX + id)
                .toList();
        List<Object> cachedValues = redisTemplate.opsForValue().multiGet(keys);

        // 3. 캐시 Hit/Miss 분류
        List<ArticleResponse> results = new ArrayList<>(ids.size());
        List<Long> missedIds = new ArrayList<>();

        for (int i = 0; i < ids.size(); i++) {
            Object cached = (cachedValues != null) ? cachedValues.get(i) : null;
            if (cached != null) {
                // 캐시 Hit: JSON → ArticleResponse 변환
                try {
                    ArticleResponse response = objectMapper.convertValue(cached, ArticleResponse.class);
                    results.add(response);
                } catch (IllegalArgumentException e) {
                    log.warn("[전략B] 캐시 역직렬화 실패, DB 조회로 대체: id={}", ids.get(i), e);
                    missedIds.add(ids.get(i));
                    results.add(null); // 자리 확보용
                }
            } else {
                // 캐시 Miss
                missedIds.add(ids.get(i));
                results.add(null); // 자리 확보용
            }
        }

        // 4. Cache Miss인 ID들만 RDB에서 조회
        if (!missedIds.isEmpty()) {
            List<Article> articles = articleRepository.findAllById(missedIds);

            for (Article article : articles) {
                ArticleResponse response = ArticleResponse.from(article);

                // Redis에 캐시 적재
                String cacheKey = CACHE_KEY_PREFIX + article.getId();
                redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

                // 결과 리스트에서 해당 위치에 삽입
                int index = ids.indexOf(article.getId());
                if (index >= 0) {
                    results.set(index, response);
                }
            }
        }

        // null 제거 (삭제된 데이터 등으로 인해 발생 가능)
        List<ArticleResponse> finalResults = results.stream()
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(finalResults, pageable, idPage.getTotalElements());
    }
}
