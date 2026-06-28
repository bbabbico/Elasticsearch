package com.template.elastic.benchmark;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 성능 벤치마크 컨트롤러
 * <p>
 * 6가지 시나리오의 성능 테스트를 개별 또는 일괄 실행할 수 있는 API를 제공한다.
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/benchmark")
@Slf4j
public class BenchmarkController {

    private final PerformanceBenchmarkService benchmarkService;

    /**
     * 개별 시나리오 실행
     * <p>
     * 시나리오 번호(1~6)를 지정하여 해당 시나리오만 실행한다.
     * </p>
     *
     * @param number 시나리오 번호 (1~6)
     * @return 해당 시나리오의 벤치마크 결과 목록
     */
    @GetMapping("/scenario/{number}")
    public ResponseEntity<List<PerformanceBenchmarkService.BenchmarkResult>> runScenario(
            @PathVariable int number) {

        log.info("[벤치마크] 시나리오 {} 실행 요청", number);

        List<PerformanceBenchmarkService.BenchmarkResult> results = switch (number) {
            case 1 -> benchmarkService.scenario1_coldStart();
            case 2 -> benchmarkService.scenario2_warmRead();
            case 3 -> benchmarkService.scenario3_keywordSearch();
            case 4 -> benchmarkService.scenario4_mixedLoad();
            case 5 -> benchmarkService.scenario5_randomRead();
            case 6 -> benchmarkService.scenario6_complexFilter();
            default -> throw new IllegalArgumentException("유효하지 않은 시나리오 번호입니다: " + number + " (1~6 사이의 값을 입력하세요)");
        };

        return ResponseEntity.ok(results);
    }

    /**
     * 전체 시나리오 일괄 실행
     * <p>
     * 시나리오 1~6을 순차적으로 모두 실행하고 전체 결과를 반환한다.
     * 실행 시간이 오래 걸릴 수 있으므로 주의가 필요하다.
     * </p>
     *
     * @return 전체 시나리오의 벤치마크 결과
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, List<PerformanceBenchmarkService.BenchmarkResult>>> runAll() {
        log.info("[벤치마크] 전체 시나리오 일괄 실행 요청");
        return ResponseEntity.ok(benchmarkService.runAllScenarios());
    }
}
