package com.template.elastic.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 설정 클래스
 * <p>
 * Kafka 토픽 자동 생성, Consumer 에러 핸들링(3회 재시도 + DLQ),
 * KafkaListenerContainerFactory 설정을 관리한다.
 * </p>
 */
@Configuration
public class KafkaConfig {

    /**
     * 게시글 이벤트 토픽명
     */
    public static final String ARTICLE_TOPIC = "article-events";

    /**
     * 게시글 이벤트 토픽을 자동 생성한다.
     * <p>
     * 파티션 3개, 레플리카 1개로 구성한다.
     * 파티션 수는 Consumer 병렬 처리 수준을 결정한다.
     * </p>
     *
     * @return 게시글 이벤트 토픽
     */
    @Bean
    public NewTopic articleTopic() {
        return TopicBuilder.name(ARTICLE_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Consumer 에러 핸들러를 생성한다.
     * <p>
     * 메시지 처리 실패 시 1초 간격으로 최대 3회 재시도하며,
     * 모든 재시도 실패 후 DLQ 토픽(article-events.DLT)으로 메시지를 전송한다.
     * </p>
     *
     * @param kafkaTemplate DLQ 전송에 사용할 KafkaTemplate
     * @return 설정된 DefaultErrorHandler
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // DLQ 토픽으로 실패 메시지를 전송하는 Recoverer
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        // 1000ms 간격으로 최대 3회 재시도 (총 시도 횟수 = 초기 1회 + 재시도 3회 = 4회)
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);

        return new DefaultErrorHandler(recoverer, backOff);
    }

    /**
     * Kafka Consumer 리스너 컨테이너 팩토리를 생성한다.
     * <p>
     * 에러 핸들러를 적용하여 재시도 및 DLQ 기능을 활성화한다.
     * </p>
     *
     * @param consumerFactory Consumer 팩토리
     * @param errorHandler    에러 핸들러
     * @return 설정된 KafkaListenerContainerFactory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
