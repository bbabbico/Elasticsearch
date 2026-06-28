package com.template.elastic.controller;

import com.template.elastic.dto.ArticleResponse;
import com.template.elastic.service.query.RedisStrategyCService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redis 전략 C 컨트롤러 - ZSet + Hash 기반 완전 Redis 조회
 * <p>
 * RDB 접근 없이 Redis의 ZSet/Hash 자료구조만으로 데이터를 조회한다.
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/redis/strategy-c/articles")
public class RedisStrategyCController {

    private final RedisStrategyCService strategyCService;

    /**
     * 최신순 목록 조회 (완전 Redis 기반)
     *
     * @param pageable 페이징 정보 (기본: page=0, size=20)
     * @return 게시글 응답 페이지
     */
    @GetMapping
    public ResponseEntity<Page<ArticleResponse>> getArticles(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(strategyCService.getArticles(pageable));
    }
}
