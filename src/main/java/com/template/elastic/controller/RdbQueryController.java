package com.template.elastic.controller;

import com.template.elastic.dto.ArticleResponse;
import com.template.elastic.service.query.RdbQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * RDB 직접 조회 컨트롤러
 * <p>
 * MySQL(JPA) 직접 조회 API를 제공한다.
 * 캐시 없이 순수 RDB 성능을 측정하기 위한 기준 엔드포인트이다.
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rdb/articles")
public class RdbQueryController {

    private final RdbQueryService rdbQueryService;

    /**
     * 최신순 목록 조회
     *
     * @param pageable 페이징 정보 (기본: page=0, size=20)
     * @return 게시글 응답 페이지
     */
    @GetMapping
    public ResponseEntity<Page<ArticleResponse>> getArticles(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(rdbQueryService.getArticles(pageable));
    }

    /**
     * 키워드 검색 (LIKE)
     *
     * @param keyword  검색 키워드
     * @param pageable 페이징 정보
     * @return 검색된 게시글 응답 페이지
     */
    @GetMapping("/search")
    public ResponseEntity<Page<ArticleResponse>> searchByKeyword(
            @RequestParam String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(rdbQueryService.searchByKeyword(keyword, pageable));
    }

    /**
     * 복합 필터 검색 (카테고리 + 기간 + 조회수 정렬)
     *
     * @param category  카테고리
     * @param startDate 시작 일시 (ISO 형식)
     * @param endDate   종료 일시 (ISO 형식)
     * @param pageable  페이징 정보
     * @return 필터링된 게시글 응답 페이지
     */
    @GetMapping("/filter")
    public ResponseEntity<Page<ArticleResponse>> searchByFilter(
            @RequestParam String category,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(rdbQueryService.searchByFilter(category, startDate, endDate, pageable));
    }
}
