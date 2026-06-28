package com.template.elastic.controller;

import com.template.elastic.dto.ArticleResponse;
import com.template.elastic.service.query.RedisStrategyBService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redis 전략 B 컨트롤러 - 개별 엔티티 캐싱 (Look-Aside)
 * <p>
 * ID만 RDB에서 페이징 조회하고, 개별 엔티티 데이터는 Redis에서 조회한다.
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/redis/strategy-b/articles")
public class RedisStrategyBController {

    private final RedisStrategyBService strategyBService;

    /**
     * 최신순 목록 조회 (개별 엔티티 캐싱)
     *
     * @param pageable 페이징 정보 (기본: page=0, size=20)
     * @return 게시글 응답 페이지
     */
    @GetMapping
    public ResponseEntity<Page<ArticleResponse>> getArticles(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(strategyBService.getArticles(pageable));
    }
}
