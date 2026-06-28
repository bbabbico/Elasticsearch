package com.template.elastic.service.query;

import com.template.elastic.domain.article.repository.ArticleRepository;
import com.template.elastic.dto.ArticleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * RDB 직접 조회 서비스
 * <p>
 * MySQL(JPA)을 직접 조회하여 데이터를 반환한다.
 * 캐시나 검색 엔진 없이 순수 RDB 성능을 측정하기 위한 기준 서비스이다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RdbQueryService {

    private final ArticleRepository articleRepository;

    /**
     * 최신순 페이징 목록 조회
     * <p>
     * createdAt 역순으로 게시글을 페이징 조회한다.
     * </p>
     *
     * @param pageable 페이징 정보
     * @return 게시글 응답 페이지
     */
    public Page<ArticleResponse> getArticles(Pageable pageable) {
        return articleRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(ArticleResponse::from);
    }

    /**
     * 키워드 검색 (LIKE)
     * <p>
     * 제목 또는 본문에 키워드가 포함된 게시글을 페이징 조회한다.
     * SQL의 LIKE '%keyword%' 방식으로 검색하므로 대용량 데이터에서는 성능이 저하될 수 있다.
     * </p>
     *
     * @param keyword  검색 키워드
     * @param pageable 페이징 정보
     * @return 검색된 게시글 응답 페이지
     */
    public Page<ArticleResponse> searchByKeyword(String keyword, Pageable pageable) {
        return articleRepository.findByTitleContainingOrContentContaining(keyword, keyword, pageable)
                .map(ArticleResponse::from);
    }

    /**
     * 복합 조건 검색: 카테고리 + 기간 + 조회수 정렬
     * <p>
     * 특정 카테고리의 게시글을 기간 범위로 필터링하고, 조회수 역순으로 정렬하여 페이징 조회한다.
     * </p>
     *
     * @param category 카테고리
     * @param start    시작 일시
     * @param end      종료 일시
     * @param pageable 페이징 정보
     * @return 필터링된 게시글 응답 페이지
     */
    public Page<ArticleResponse> searchByFilter(String category, LocalDateTime start,
                                                 LocalDateTime end, Pageable pageable) {
        return articleRepository.findByCategoryAndCreatedAtBetweenOrderByViewCountDesc(
                        category, start, end, pageable)
                .map(ArticleResponse::from);
    }
}
