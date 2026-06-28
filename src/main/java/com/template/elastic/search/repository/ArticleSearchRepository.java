package com.template.elastic.search.repository;

import com.template.elastic.search.document.ArticleDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 게시글 Elasticsearch 검색 레포지토리
 * <p>
 * ArticleDocument에 대한 Elasticsearch 기반 검색 기능을 제공한다.
 * nori 분석기를 활용한 한국어 형태소 분석 검색을 지원한다.
 * </p>
 */
public interface ArticleSearchRepository extends ElasticsearchRepository<ArticleDocument, Long> {

    /**
     * 전체 문서를 생성일시 역순으로 페이징 조회한다.
     *
     * @param pageable 페이징 정보
     * @return 생성일시 역순 정렬된 문서 페이지
     */
    Page<ArticleDocument> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 키워드를 기반으로 제목, 본문, 태그 필드를 대상으로 multi_match 검색을 수행한다.
     * <p>
     * best_fields 타입을 사용하여 가장 높은 점수를 가진 필드 기준으로 스코어링한다.
     * </p>
     *
     * @param keyword  검색 키워드
     * @param pageable 페이징 정보
     * @return 키워드와 매칭되는 문서 페이지
     */
    @Query("""
            {
              "multi_match": {
                "query": "?0",
                "fields": ["title^3", "content", "tags^2"],
                "type": "best_fields"
              }
            }
            """)
    Page<ArticleDocument> searchByKeyword(String keyword, Pageable pageable);
}
