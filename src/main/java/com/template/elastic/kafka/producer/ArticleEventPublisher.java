package com.template.elastic.kafka.producer;

import com.template.elastic.config.KafkaConfig;
import com.template.elastic.kafka.event.ArticleEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 게시글 이벤트 Kafka Producer
 * <p>
 * ArticleEvent를 Kafka 토픽에 발행한다.
 * articleId를 파티션 키로 사용하여 동일 게시글에 대한 이벤트 순서를 보장한다.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArticleEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 게시글 이벤트를 Kafka 토픽으로 발행한다.
     *
     * @param event 발행할 게시글 이벤트
     */
    public void publish(ArticleEvent event) {
        log.info("[Kafka Producer] 이벤트 발행: type={}, articleId={}",
                event.getEventType(), event.getArticleId());

        kafkaTemplate.send(
                KafkaConfig.ARTICLE_TOPIC,
                String.valueOf(event.getArticleId()),
                event
        );
    }
}
