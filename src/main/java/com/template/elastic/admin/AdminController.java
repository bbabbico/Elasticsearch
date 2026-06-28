package com.template.elastic.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 관리자 API 컨트롤러
 * <p>
 * 초기 데이터 적재(Bulk Load)를 위한 관리자 전용 엔드포인트를 제공한다.
 * 지정한 수의 더미 데이터를 MySQL, Elasticsearch, Redis에 순차적으로 적재할 수 있다.
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Slf4j
public class AdminController {

    private final BulkDataInitService bulkDataInitService;

    /**
     * MySQL에 회원 및 게시글 더미 데이터를 배치 삽입한다.
     *
     * @param members  생성할 회원 수 (기본값: 100)
     * @param articles 생성할 게시글 수 (기본값: 200,000)
     * @return 삽입 결과 메시지
     */
    @PostMapping("/init/mysql")
    public ResponseEntity<Map<String, Object>> initMysql(
            @RequestParam(defaultValue = "100") int members,
            @RequestParam(defaultValue = "200000") int articles) {

        log.info("[Admin] MySQL 초기화 요청: 회원={}명, 게시글={}건", members, articles);
        long start = System.currentTimeMillis();

        bulkDataInitService.initMysqlData(members, articles);

        long elapsed = System.currentTimeMillis() - start;
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "members", members,
                "articles", articles,
                "elapsedMs", elapsed
        ));
    }

    /**
     * MySQL 데이터를 기반으로 Elasticsearch에 벌크 인덱싱한다.
     * MySQL 초기화가 선행되어야 한다.
     *
     * @return 인덱싱 결과 메시지
     */
    @PostMapping("/init/elastic")
    public ResponseEntity<Map<String, Object>> initElasticsearch() {
        log.info("[Admin] Elasticsearch 초기화 요청");
        long start = System.currentTimeMillis();

        bulkDataInitService.initElasticsearchData();

        long elapsed = System.currentTimeMillis() - start;
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "target", "elasticsearch",
                "elapsedMs", elapsed
        ));
    }

    /**
     * MySQL 데이터를 기반으로 Redis에 Warm-up 데이터를 적재한다.
     * (전략 B: Entity 캐시, 전략 C: ZSet + Hash)
     * MySQL 초기화가 선행되어야 한다.
     *
     * @return 적재 결과 메시지
     */
    @PostMapping("/init/redis")
    public ResponseEntity<Map<String, Object>> initRedis() {
        log.info("[Admin] Redis 초기화 요청");
        long start = System.currentTimeMillis();

        bulkDataInitService.initRedisData();

        long elapsed = System.currentTimeMillis() - start;
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "target", "redis",
                "elapsedMs", elapsed
        ));
    }

    /**
     * MySQL → Elasticsearch → Redis 순서로 전체 초기화를 일괄 수행한다.
     *
     * @param members  생성할 회원 수 (기본값: 100)
     * @param articles 생성할 게시글 수 (기본값: 200,000)
     * @return 전체 초기화 결과 메시지
     */
    @PostMapping("/init/all")
    public ResponseEntity<Map<String, Object>> initAll(
            @RequestParam(defaultValue = "100") int members,
            @RequestParam(defaultValue = "200000") int articles) {

        log.info("[Admin] 전체 초기화 요청: 회원={}명, 게시글={}건", members, articles);
        long totalStart = System.currentTimeMillis();

        // Phase 1: MySQL
        long mysqlStart = System.currentTimeMillis();
        bulkDataInitService.initMysqlData(members, articles);
        long mysqlElapsed = System.currentTimeMillis() - mysqlStart;

        // Phase 2: Elasticsearch
        long esStart = System.currentTimeMillis();
        bulkDataInitService.initElasticsearchData();
        long esElapsed = System.currentTimeMillis() - esStart;

        // Phase 3: Redis
        long redisStart = System.currentTimeMillis();
        bulkDataInitService.initRedisData();
        long redisElapsed = System.currentTimeMillis() - redisStart;

        long totalElapsed = System.currentTimeMillis() - totalStart;

        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "members", members,
                "articles", articles,
                "mysqlElapsedMs", mysqlElapsed,
                "elasticsearchElapsedMs", esElapsed,
                "redisElapsedMs", redisElapsed,
                "totalElapsedMs", totalElapsed
        ));
    }
}
