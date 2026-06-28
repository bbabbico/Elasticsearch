package com.template.elastic.kafka.event;

import com.template.elastic.domain.article.Article;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 게시글 Kafka 이벤트 모델
 * <p>
 * Article 엔티티의 생성/수정/삭제 변경사항을 Kafka 메시지로 전달하기 위한 이벤트 객체.
 * Spring ApplicationEvent로 발행된 후, @TransactionalEventListener를 통해
 * 트랜잭션 커밋 후 Kafka로 전송된다.
 * </p>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleEvent {

    /**
     * 이벤트 타입 (CREATE, UPDATE, DELETE)
     */
    private EventType eventType;

    /**
     * 게시글 ID
     */
    private Long articleId;

    /**
     * 게시글 제목
     */
    private String title;

    /**
     * 게시글 본문
     */
    private String content;

    /**
     * 카테고리
     */
    private String category;

    /**
     * 태그 (콤마 구분 문자열)
     */
    private String tags;

    /**
     * 조회수
     */
    private int viewCount;

    /**
     * 작성자 회원 ID
     */
    private Long memberId;

    /**
     * 작성자 닉네임
     */
    private String memberNickname;

    /**
     * 생성 일시
     */
    private LocalDateTime createdAt;

    /**
     * 수정 일시
     */
    private LocalDateTime updatedAt;

    /**
     * 이벤트 타입 열거형
     */
    public enum EventType {
        CREATE, UPDATE, DELETE
    }

    /**
     * Article 엔티티로부터 CREATE 이벤트를 생성한다.
     *
     * @param article 생성된 게시글 엔티티
     * @return CREATE 타입의 ArticleEvent
     */
    public static ArticleEvent ofCreate(Article article) {
        return ArticleEvent.builder()
                .eventType(EventType.CREATE)
                .articleId(article.getId())
                .title(article.getTitle())
                .content(article.getContent())
                .category(article.getCategory())
                .tags(article.getTags())
                .viewCount(article.getViewCount())
                .memberId(article.getMember().getId())
                .memberNickname(article.getMember().getNickname())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }

    /**
     * Article 엔티티로부터 UPDATE 이벤트를 생성한다.
     *
     * @param article 수정된 게시글 엔티티
     * @return UPDATE 타입의 ArticleEvent
     */
    public static ArticleEvent ofUpdate(Article article) {
        return ArticleEvent.builder()
                .eventType(EventType.UPDATE)
                .articleId(article.getId())
                .title(article.getTitle())
                .content(article.getContent())
                .category(article.getCategory())
                .tags(article.getTags())
                .viewCount(article.getViewCount())
                .memberId(article.getMember().getId())
                .memberNickname(article.getMember().getNickname())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }

    /**
     * 게시글 ID로부터 DELETE 이벤트를 생성한다.
     * <p>
     * 삭제 이벤트는 게시글 ID만 필요하며, 나머지 필드는 null/기본값이다.
     * </p>
     *
     * @param articleId 삭제할 게시글 ID
     * @return DELETE 타입의 ArticleEvent
     */
    public static ArticleEvent ofDelete(Long articleId) {
        return ArticleEvent.builder()
                .eventType(EventType.DELETE)
                .articleId(articleId)
                .build();
    }
}
