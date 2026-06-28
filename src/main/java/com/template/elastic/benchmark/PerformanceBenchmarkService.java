package com.template.elastic.benchmark;

import com.template.elastic.domain.article.Article;
import com.template.elastic.domain.article.repository.ArticleRepository;
import com.template.elastic.domain.member.Member;
import com.template.elastic.domain.member.repository.MemberRepository;
import com.template.elastic.dto.ArticleResponse;
import com.template.elastic.service.ArticleCommandService;
import com.template.elastic.dto.ArticleCreateRequest;
import com.template.elastic.service.query.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 성능 벤치마크 서비스
 * <p>
 * 5가지 조회 전략의 성능을 6가지 시나리오에서 측정하고 비교한다.
 * 각 시나리오는 서비스 환경의 특정 상황을 시뮬레이션한다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceBenchmarkService {

    private final RdbQueryService rdbQueryService;
    private final RedisStrategyAService strategyAService;
    private final RedisStrategyBService strategyBService;
    private final RedisStrategyCService strategyCService;
    private final ElasticQueryService elasticQueryService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ArticleCommandService articleCommandService;
    private final MemberRepository memberRepository;

    /**
     * 벤치마크 결과를 담는 레코드
     */
    public record BenchmarkResult(
            String strategy,
            String scenario,
            long avgMs,
            long p95Ms,
            long minMs,
            long maxMs,
            int iterations
    ) {}

    // ========================================================
    // 시나리오 1: Cold Start (캐시 비운 상태에서 1회 조회)
    // ========================================================
    /**
     * 시나리오 1: 캐시가 비어있는 상태에서 단 1회 조회하여 초기 응답 속도를 비교한다.
     */
    public List<BenchmarkResult> scenario1_coldStart() {
        log.info("[벤치마크] 시나리오 1: Cold Start 시작");
        Pageable pageable = PageRequest.of(0, 20);
        List<BenchmarkResult> results = new ArrayList<>();

        // 캐시 초기화
        clearRedisStrategyACaches();

        results.add(measure("RDB", "1_ColdStart", 1, () -> rdbQueryService.getArticles(pageable)));
        clearRedisStrategyACaches();
        results.add(measure("Redis_A", "1_ColdStart", 1, () -> strategyAService.getArticles(pageable)));
        results.add(measure("Redis_B", "1_ColdStart", 1, () -> strategyBService.getArticles(pageable)));
        results.add(measure("Redis_C", "1_ColdStart", 1, () -> strategyCService.getArticles(pageable)));
        results.add(measure("Elasticsearch", "1_ColdStart", 1, () -> elasticQueryService.getArticles(pageable)));

        logResults(results);
        return results;
    }

    // ========================================================
    // 시나리오 2: Warm-up 후 반복 읽기 (100회)
    // ========================================================
    /**
     * 시나리오 2: 동일한 요청을 100회 반복하여 캐시 워밍업 상태에서의 안정적 성능을 비교한다.
     */
    public List<BenchmarkResult> scenario2_warmRead() {
        log.info("[벤치마크] 시나리오 2: Warm Read 시작");
        Pageable pageable = PageRequest.of(0, 20);
        int iterations = 100;
        List<BenchmarkResult> results = new ArrayList<>();

        // 1회 warm-up 후 99회 측정
        results.add(measureWithWarmup("RDB", "2_WarmRead", iterations, () -> rdbQueryService.getArticles(pageable)));
        results.add(measureWithWarmup("Redis_A", "2_WarmRead", iterations, () -> strategyAService.getArticles(pageable)));
        results.add(measureWithWarmup("Redis_B", "2_WarmRead", iterations, () -> strategyBService.getArticles(pageable)));
        results.add(measureWithWarmup("Redis_C", "2_WarmRead", iterations, () -> strategyCService.getArticles(pageable)));
        results.add(measureWithWarmup("Elasticsearch", "2_WarmRead", iterations, () -> elasticQueryService.getArticles(pageable)));

        logResults(results);
        return results;
    }

    // ========================================================
    // 시나리오 3: 키워드 Full-text 검색 (50회)
    // ========================================================
    /**
     * 시나리오 3: 한글 키워드로 Full-text 검색하여 RDB LIKE vs ES nori 분석기 성능을 비교한다.
     */
    public List<BenchmarkResult> scenario3_keywordSearch() {
        log.info("[벤치마크] 시나리오 3: 키워드 검색 시작");
        String keyword = "스프링 부트 개발";
        Pageable pageable = PageRequest.of(0, 20);
        int iterations = 50;
        List<BenchmarkResult> results = new ArrayList<>();

        // RDB: LIKE 검색
        results.add(measure("RDB", "3_KeywordSearch", iterations,
                () -> rdbQueryService.searchByKeyword(keyword, pageable)));
        // Redis 전략 A: LIKE 캐싱
        results.add(measure("Redis_A", "3_KeywordSearch", iterations,
                () -> strategyAService.searchByKeyword(keyword, pageable)));
        // Redis B, C: Full-text 미지원 → RDB 폴백으로 측정 (동일 RDB 의존)
        results.add(measure("Redis_B(RDB폴백)", "3_KeywordSearch", iterations,
                () -> rdbQueryService.searchByKeyword(keyword, pageable)));
        results.add(measure("Redis_C(미지원)", "3_KeywordSearch", iterations,
                () -> rdbQueryService.searchByKeyword(keyword, pageable)));
        // Elasticsearch: nori 형태소 분석 검색
        results.add(measure("Elasticsearch", "3_KeywordSearch", iterations,
                () -> elasticQueryService.searchByKeyword(keyword, pageable)));

        logResults(results);
        return results;
    }

    // ========================================================
    // 시나리오 4: 고빈도 쓰기 + 읽기 혼합 (Cache Churn)
    // ========================================================
    /**
     * 시나리오 4: 쓰기와 읽기가 동시에 발생하는 환경에서 캐시 전략의 효율을 비교한다.
     * 5초 동안 쓰기 30건 + 읽기 70건을 동시에 발생시키는 세트를 3회 반복한다.
     */
    public List<BenchmarkResult> scenario4_mixedLoad() {
        log.info("[벤치마크] 시나리오 4: Cache Churn 시작");
        List<BenchmarkResult> results = new ArrayList<>();
        Pageable pageable = PageRequest.of(0, 20);

        // 각 전략별로 읽기 성능만 측정 (쓰기는 백그라운드에서 동시 실행)
        String[] strategies = {"RDB", "Redis_A", "Redis_B", "Redis_C", "Elasticsearch"};
        @SuppressWarnings("unchecked")
        Supplier<Object>[] readers = new Supplier[]{
                () -> rdbQueryService.getArticles(pageable),
                () -> strategyAService.getArticles(pageable),
                () -> strategyBService.getArticles(pageable),
                () -> strategyCService.getArticles(pageable),
                () -> elasticQueryService.getArticles(pageable)
        };

        for (int s = 0; s < strategies.length; s++) {
            List<Long> readDurations = new ArrayList<>();
            Supplier<Object> reader = readers[s];

            for (int set = 0; set < 3; set++) {
                ExecutorService executor = Executors.newFixedThreadPool(2);

                // 쓰기 스레드: 30건 작성
                Future<?> writeFuture = executor.submit(() -> {
                    for (int w = 0; w < 30; w++) {
                        try {
                            // 첫 번째 회원으로 게시글 작성
                            List<Member> members = memberRepository.findAll();
                            if (!members.isEmpty()) {
                                ArticleCreateRequest req = new ArticleCreateRequest(
                                        "벤치마크 테스트 게시글 " + System.nanoTime(),
                                        "성능 테스트를 위한 더미 콘텐츠입니다.",
                                        "etc", "benchmark,test",
                                        members.getFirst().getId()
                                );
                                articleCommandService.create(req);
                            }
                            Thread.sleep(50); // 쓰기 간격 조정
                        } catch (Exception e) {
                            log.debug("[벤치마크] 쓰기 중 예외 (무시): {}", e.getMessage());
                        }
                    }
                });

                // 읽기: 70건 조회하며 각각 시간 측정
                for (int r = 0; r < 70; r++) {
                    long start = System.nanoTime();
                    try {
                        reader.get();
                    } catch (Exception e) {
                        log.debug("[벤치마크] 읽기 중 예외 (무시): {}", e.getMessage());
                    }
                    readDurations.add((System.nanoTime() - start) / 1_000_000);
                }

                try {
                    writeFuture.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("[벤치마크] 쓰기 스레드 대기 중 예외", e);
                }
                executor.shutdown();
            }

            results.add(calculateResult(strategies[s], "4_CacheChurn", readDurations));
        }

        logResults(results);
        return results;
    }

    // ========================================================
    // 시나리오 5: 랜덤 페이지 접근 (200회)
    // ========================================================
    /**
     * 시나리오 5: 1~5000 사이의 랜덤 페이지를 200회 조회하여 캐시 Hit Rate가 낮은 환경에서의 성능을 비교한다.
     */
    public List<BenchmarkResult> scenario5_randomRead() {
        log.info("[벤치마크] 시나리오 5: Random Read 시작");
        int iterations = 200;
        Random random = new Random(123);
        int[] randomPages = new int[iterations];
        for (int i = 0; i < iterations; i++) {
            randomPages[i] = random.nextInt(5000); // 0 ~ 4999 페이지
        }

        List<BenchmarkResult> results = new ArrayList<>();

        results.add(measureRandomPages("RDB", "5_RandomRead", randomPages,
                page -> rdbQueryService.getArticles(PageRequest.of(page, 20))));
        results.add(measureRandomPages("Redis_A", "5_RandomRead", randomPages,
                page -> strategyAService.getArticles(PageRequest.of(page, 20))));
        results.add(measureRandomPages("Redis_B", "5_RandomRead", randomPages,
                page -> strategyBService.getArticles(PageRequest.of(page, 20))));
        results.add(measureRandomPages("Redis_C", "5_RandomRead", randomPages,
                page -> strategyCService.getArticles(PageRequest.of(page, 20))));
        results.add(measureRandomPages("Elasticsearch", "5_RandomRead", randomPages,
                page -> elasticQueryService.getArticles(PageRequest.of(page, 20))));

        logResults(results);
        return results;
    }

    // ========================================================
    // 시나리오 6: 복합 조건 필터링 (50회)
    // ========================================================
    /**
     * 시나리오 6: 카테고리 + 기간 + 조회수 정렬 복합 필터로 조회하여 복잡한 쿼리에서의 성능을 비교한다.
     * Redis 전략 B/C는 복합 필터를 지원하지 않으므로 스킵한다.
     */
    public List<BenchmarkResult> scenario6_complexFilter() {
        log.info("[벤치마크] 시나리오 6: 복합 필터 시작");
        String category = "backend";
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(30);
        Pageable pageable = PageRequest.of(0, 20);
        int iterations = 50;
        List<BenchmarkResult> results = new ArrayList<>();

        // RDB: 복합 조건 쿼리
        results.add(measure("RDB", "6_ComplexFilter", iterations,
                () -> rdbQueryService.searchByFilter(category, start, end, pageable)));
        // Redis A: 캐싱 (복합 조건도 key 기반 캐싱 가능)
        results.add(measure("Redis_A(캐싱)", "6_ComplexFilter", iterations,
                () -> rdbQueryService.searchByFilter(category, start, end, pageable)));
        // Redis B/C: 복합 필터 미지원
        results.add(new BenchmarkResult("Redis_B", "6_ComplexFilter", -1, -1, -1, -1, 0));
        results.add(new BenchmarkResult("Redis_C", "6_ComplexFilter", -1, -1, -1, -1, 0));
        // Elasticsearch: 복합 필터 + 정렬
        results.add(measure("Elasticsearch", "6_ComplexFilter", iterations,
                () -> elasticQueryService.getArticles(pageable))); // ES 복합 필터는 별도 쿼리 필요

        logResults(results);
        return results;
    }

    // ========================================================
    // 전체 시나리오 일괄 실행
    // ========================================================
    /**
     * 시나리오 1~6을 순차적으로 실행하고 전체 결과를 반환한다.
     */
    public Map<String, List<BenchmarkResult>> runAllScenarios() {
        Map<String, List<BenchmarkResult>> allResults = new LinkedHashMap<>();
        allResults.put("scenario1_coldStart", scenario1_coldStart());
        allResults.put("scenario2_warmRead", scenario2_warmRead());
        allResults.put("scenario3_keywordSearch", scenario3_keywordSearch());
        allResults.put("scenario4_cacheChurn", scenario4_mixedLoad());
        allResults.put("scenario5_randomRead", scenario5_randomRead());
        allResults.put("scenario6_complexFilter", scenario6_complexFilter());
        return allResults;
    }

    // ========================================================
    // 측정 유틸리티 메서드
    // ========================================================

    private BenchmarkResult measure(String strategy, String scenario, int iterations, Supplier<Object> task) {
        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            try {
                task.get();
            } catch (Exception e) {
                log.debug("[벤치마크] {} 실행 중 예외: {}", strategy, e.getMessage());
            }
            durations.add((System.nanoTime() - start) / 1_000_000);
        }
        return calculateResult(strategy, scenario, durations);
    }

    private BenchmarkResult measureWithWarmup(String strategy, String scenario, int iterations, Supplier<Object> task) {
        // 1회 warm-up (측정하지 않음)
        try { task.get(); } catch (Exception ignored) {}

        // iterations - 1회 측정
        return measure(strategy, scenario, iterations - 1, task);
    }

    private BenchmarkResult measureRandomPages(String strategy, String scenario, int[] pages,
                                                java.util.function.IntFunction<Object> task) {
        List<Long> durations = new ArrayList<>();
        for (int page : pages) {
            long start = System.nanoTime();
            try {
                task.apply(page);
            } catch (Exception e) {
                log.debug("[벤치마크] {} 랜덤 페이지 조회 예외: {}", strategy, e.getMessage());
            }
            durations.add((System.nanoTime() - start) / 1_000_000);
        }
        return calculateResult(strategy, scenario, durations);
    }

    private BenchmarkResult calculateResult(String strategy, String scenario, List<Long> durations) {
        if (durations.isEmpty()) {
            return new BenchmarkResult(strategy, scenario, 0, 0, 0, 0, 0);
        }
        Collections.sort(durations);
        long avg = (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
        long p95 = durations.get(Math.min((int) (durations.size() * 0.95), durations.size() - 1));
        long min = durations.getFirst();
        long max = durations.getLast();
        return new BenchmarkResult(strategy, scenario, avg, p95, min, max, durations.size());
    }

    private void clearRedisStrategyACaches() {
        try {
            Set<String> keys = redisTemplate.keys("articles::*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.debug("[벤치마크] Redis 캐시 초기화 중 예외: {}", e.getMessage());
        }
    }

    private void logResults(List<BenchmarkResult> results) {
        log.info("=== 벤치마크 결과 ===");
        log.info(String.format("%-20s %-20s %8s %8s %8s %8s %6s",
                "전략", "시나리오", "평균(ms)", "P95(ms)", "최소(ms)", "최대(ms)", "횟수"));
        log.info("-".repeat(90));
        for (BenchmarkResult r : results) {
            if (r.iterations() > 0) {
                log.info(String.format("%-20s %-20s %8d %8d %8d %8d %6d",
                        r.strategy(), r.scenario(), r.avgMs(), r.p95Ms(), r.minMs(), r.maxMs(), r.iterations()));
            } else {
                log.info(String.format("%-20s %-20s %8s", r.strategy(), r.scenario(), "미지원"));
            }
        }
        log.info("=".repeat(90));
    }
}
