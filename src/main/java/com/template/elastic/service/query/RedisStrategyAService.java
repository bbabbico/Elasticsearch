package com.template.elastic.service.query;

import com.template.elastic.domain.article.repository.ArticleRepository;
import com.template.elastic.dto.ArticleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redis 전략 A 서비스 - Spring @Cacheable 기반 결과 캐싱
 * <p>
 * 동일한 페이징/검색 요청에 대해 Spring Cache 추상화를 활용하여
 * 결과 전체를 Redis에 캐싱한다.
 * cacheNames="articles"로 설정되며, key는 요청 파라미터 조합으로 자동 생성된다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RedisStrategyAService {

    private final ArticleRepository articleRepository;

    /**
     * 최신순 페이징 목록 조회 (캐시 적용)
     * <p>
     * 동일한 page/size 요청이 반복되면 Redis 캐시에서 직접 반환한다.
     * 캐시 키: "articles::list:{pageNumber}:{pageSize}"
     * </p>
     *
     * @param pageable 페이징 정보
     * @return 게시글 응답 페이지
     */
    @Cacheable(cacheNames = "articles", key = "'list:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<ArticleResponse> getArticles(Pageable pageable) {
        Page<com.template.elastic.domain.article.Article> page = articleRepository.findAllByOrderByCreatedAtDesc(pageable);
        return new com.template.elastic.dto.CustomPageImpl<>(
                page.getContent().stream().map(ArticleResponse::from).toList(),
                pageable,
                page.getTotalElements()
        );
    }

    /**
     * 키워드 검색 (캐시 적용)
     * <p>
     * 동일한 키워드 + 페이지 조합 요청이 반복되면 Redis 캐시에서 직접 반환한다.
     * 캐시 키: "articles::search:{keyword}:{pageNumber}"
     * </p>
     *
     * @param keyword  검색 키워드
     * @param pageable 페이징 정보
     * @return 검색된 게시글 응답 페이지
     */
    @Cacheable(cacheNames = "articles", key = "'search:' + #keyword + ':' + #pageable.pageNumber")
    public Page<ArticleResponse> searchByKeyword(String keyword, Pageable pageable) {
        Page<com.template.elastic.domain.article.Article> page = articleRepository.findByTitleContainingOrContentContaining(keyword, keyword, pageable);
        return new com.template.elastic.dto.CustomPageImpl<>(
                page.getContent().stream().map(ArticleResponse::from).toList(),
                pageable,
                page.getTotalElements()
        );
    }
}
