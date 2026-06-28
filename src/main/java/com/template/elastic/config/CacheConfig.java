package com.template.elastic.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시 설정 클래스
 * <p>
 * Spring의 캐시 어노테이션(@Cacheable, @CacheEvict, @CachePut 등)을 활성화한다.
 * 실제 캐시 매니저 구성은 {@link RedisConfig}에서 담당한다.
 * </p>
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
