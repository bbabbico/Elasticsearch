package com.template.elastic.service.query;

import com.template.elastic.dto.ArticleResponse;
import com.template.elastic.search.repository.ArticleSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch 조회 서비스
 * <p>
 * Elasticsearch를 활용하여 게시글을 조회/검색한다.
 * nori 형태소 분석기를 사용한 한국어 Full-text 검색을 지원하며,
 * RDB 대비 대용량 데이터에서의 검색 성능 이점을 제공한다.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ElasticQueryService {

    private final ArticleSearchRepository articleSearchRepository;

    /**
     * 최신순 페이징 목록 조회
     * <p>
     * Elasticsearch에서 createdAt 역순으로 게시글 문서를 페이징 조회한다.
     * </p>
     *
     * @param pageable 페이징 정보
     * @return 게시글 응답 페이지
     */
    public Page<ArticleResponse> getArticles(Pageable pageable) {
        return articleSearchRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(ArticleResponse::from);
    }

    /**
     * Full-text 키워드 검색 (nori 형태소 분석)
     * <p>
     * multi_match 쿼리를 사용하여 제목(가중치 3배), 본문, 태그(가중치 2배) 필드에서 검색한다.
     * nori 분석기를 통해 한국어 형태소 분석 기반의 정밀한 검색을 수행한다.
     * </p>
     *
     * @param keyword  검색 키워드
     * @param pageable 페이징 정보
     * @return 검색된 게시글 응답 페이지
     */
    public Page<ArticleResponse> searchByKeyword(String keyword, Pageable pageable) {
        return articleSearchRepository.searchByKeyword(keyword, pageable)
                .map(ArticleResponse::from);
    }
}
