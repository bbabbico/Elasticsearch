package com.template.elastic.kafka.listener;

import com.template.elastic.kafka.event.ArticleEvent;
import com.template.elastic.kafka.producer.ArticleEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 게시글 이벤트 리스너
 * <p>
 * Spring의 @TransactionalEventListener를 사용하여
 * 트랜잭션 커밋 후(AFTER_COMMIT) ArticleEvent를 수신하고
 * Kafka Producer를 통해 메시지를 발행한다.
 * </p>
 * <p>
 * 이를 통해 DB 트랜잭션이 성공적으로 커밋된 경우에만
 * Kafka 이벤트가 발행되어 데이터 일관성을 보장한다.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArticleEventListener {

    private final ArticleEventPublisher publisher;

    /**
     * 트랜잭션 커밋 후 게시글 이벤트를 Kafka로 발행한다.
     *
     * @param event 게시글 변경 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleArticleEvent(ArticleEvent event) {
        log.info("[EventListener] 트랜잭션 커밋 후 이벤트 수신: type={}, articleId={}",
                event.getEventType(), event.getArticleId());

        publisher.publish(event);
    }
}
