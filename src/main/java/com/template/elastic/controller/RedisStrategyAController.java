package com.template.elastic.controller;

import com.template.elastic.dto.ArticleResponse;
import com.template.elastic.service.query.RedisStrategyAService;
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
 * Redis 전략 A 컨트롤러 - Spring @Cacheable 기반 결과 캐싱
 * <p>
 * @Cacheable 어노테이션을 활용하여 동일한 요청의 결과를 Redis에 캐싱한다.
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/redis/strategy-a/articles")
public class RedisStrategyAController {

    private final RedisStrategyAService strategyAService;

    /**
     * 최신순 목록 조회 (캐시 적용)
     *
     * @param pageable 페이징 정보 (기본: page=0, size=20)
     * @return 게시글 응답 페이지
     */
    @GetMapping
    public ResponseEntity<Page<ArticleResponse>> getArticles(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(strategyAService.getArticles(pageable));
    }

    /**
     * 키워드 검색 (캐시 적용)
     *
     * @param keyword  검색 키워드
     * @param pageable 페이징 정보
     * @return 검색된 게시글 응답 페이지
     */
    @GetMapping("/search")
    public ResponseEntity<Page<ArticleResponse>> searchByKeyword(
            @RequestParam String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(strategyAService.searchByKeyword(keyword, pageable));
    }
}
