package com.template.elastic.controller;

import com.template.elastic.dto.ArticleResponse;
import com.template.elastic.service.query.ElasticQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Elasticsearch 조회 컨트롤러
 * <p>
 * Elasticsearch 기반 게시글 조회 및 Full-text 검색 API를 제공한다.
 * nori 형태소 분석기를 활용한 한국어 검색을 지원한다.
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/elastic/articles")
public class ElasticQueryController {

    private final ElasticQueryService elasticQueryService;

    /**
     * 최신순 목록 조회
     *
     * @param pageable 페이징 정보 (기본: page=0, size=20)
     * @return 게시글 응답 페이지
     */
    @GetMapping
    public ResponseEntity<Page<ArticleResponse>> getArticles(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(elasticQueryService.getArticles(pageable));
    }

    /**
     * 키워드 검색 (nori 형태소 분석 기반 Full-text 검색)
     *
     * @param keyword  검색 키워드
     * @param pageable 페이징 정보
     * @return 검색된 게시글 응답 페이지
     */
    @GetMapping("/search")
    public ResponseEntity<Page<ArticleResponse>> searchByKeyword(
            @RequestParam String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(elasticQueryService.searchByKeyword(keyword, pageable));
    }
}
