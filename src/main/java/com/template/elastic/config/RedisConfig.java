package com.template.elastic.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 설정 클래스
 * <p>
 * RedisTemplate과 CacheManager를 구성한다.
 * JSON 직렬화를 기본으로 사용하며, 캐시 TTL은 30분으로 설정한다.
 * </p>
 */
@Configuration
public class RedisConfig {

    /**
     * 범용 RedisTemplate을 생성한다.
     * <p>
     * Key는 StringRedisSerializer, Value는 GenericJackson2JsonRedisSerializer를 사용하여
     * 객체를 JSON 형태로 직렬화/역직렬화한다.
     * </p>
     *
     * @param connectionFactory Redis 연결 팩토리
     * @param springObjectMapper Spring Boot가 자동 구성한 ObjectMapper
     * @return 설정된 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, 
                                                       com.fasterxml.jackson.databind.ObjectMapper springObjectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Spring의 기본 ObjectMapper를 복사한 후 Redis 전용 타입 속성 추가
        com.fasterxml.jackson.databind.ObjectMapper mapper = springObjectMapper.copy();
        mapper.activateDefaultTyping(
                com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.EVERYTHING
        );

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(mapper);

        // Key 직렬화: 문자열
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 직렬화: JSON
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis 기반 캐시 매니저를 생성한다.
     * <p>
     * 기본 캐시 TTL은 30분이며, null 값은 캐시하지 않는다.
     * Value는 JSON 형태로 직렬화된다.
     * </p>
     *
     * @param connectionFactory Redis 연결 팩토리
     * @param springObjectMapper Spring Boot가 자동 구성한 ObjectMapper
     * @return 설정된 CacheManager
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory, 
                                     com.fasterxml.jackson.databind.ObjectMapper springObjectMapper) {
        
        com.fasterxml.jackson.databind.ObjectMapper mapper = springObjectMapper.copy();
        mapper.activateDefaultTyping(
                com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.EVERYTHING
        );

        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(mapper))
                );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfig)
                .build();
    }
}
