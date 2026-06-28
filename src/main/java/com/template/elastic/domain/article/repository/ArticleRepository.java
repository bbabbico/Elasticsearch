package com.template.elastic.domain.article.repository;

import com.template.elastic.domain.article.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글 JPA 레포지토리
 * <p>
 * Article 엔티티에 대한 CRUD 및 다양한 조회 메서드를 제공한다.
 * </p>
 */
public interface ArticleRepository extends JpaRepository<Article, Long> {

    /**
     * 전체 게시글을 생성일시 역순으로 페이징 조회한다.
     *
     * @param pageable 페이징 정보
     * @return 생성일시 역순 정렬된 게시글 페이지
     */
    Page<Article> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 제목 또는 본문에 키워드가 포함된 게시글을 페이징 조회한다.
     *
     * @param titleKeyword   제목 검색 키워드
     * @param contentKeyword 본문 검색 키워드
     * @param pageable       페이징 정보
     * @return 키워드가 포함된 게시글 페이지
     */
    Page<Article> findByTitleContainingOrContentContaining(
            String titleKeyword,
            String contentKeyword,
            Pageable pageable
    );

    /**
     * 특정 카테고리의 게시글을 기간 범위로 필터링하고 조회수 역순으로 페이징 조회한다.
     *
     * @param category 카테고리
     * @param start    시작 일시
     * @param end      종료 일시
     * @param pageable 페이징 정보
     * @return 조건에 맞는 게시글 페이지 (조회수 역순)
     */
    Page<Article> findByCategoryAndCreatedAtBetweenOrderByViewCountDesc(
            String category,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    /**
     * 게시글 ID만 생성일시 역순으로 페이징 조회한다.
     * <p>
     * 커버링 인덱스 전략에 활용할 수 있도록 ID만 조회하여
     * 불필요한 컬럼 로딩을 방지한다.
     * </p>
     *
     * @param pageable 페이징 정보
     * @return 게시글 ID 페이지
     */
    @Query("SELECT a.id FROM Article a ORDER BY a.createdAt DESC")
    Page<Long> findArticleIds(Pageable pageable);

    /**
     * ID 범위로 엔티티를 조회한다. 대용량 데이터 배치 처리 시 OFFSET의 성능 저하를 피하기 위해 사용.
     *
     * @param startId 시작 ID (포함)
     * @param endId   종료 ID (포함)
     * @return 지정된 ID 범위의 게시글 목록
     */
    List<Article> findByIdBetween(Long startId, Long endId);
}
