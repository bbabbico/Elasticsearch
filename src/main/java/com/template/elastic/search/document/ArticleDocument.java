package com.template.elastic.search.document;

import com.template.elastic.domain.article.Article;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;

/**
 * 게시글 Elasticsearch 문서
 * <p>
 * Elasticsearch에 인덱싱되는 게시글 문서 클래스.
 * nori 분석기를 사용하여 한국어 형태소 분석 기반 검색을 지원한다.
 * qwe
 * </p>
 */
@Document(indexName = "articles")
@Setting(settingPath = "/elasticsearch/settings.json")
@Getter
@NoArgsConstructor
public class ArticleDocument {

    /**
     * 문서 고유 식별자 (Article 엔티티의 ID와 동일)
     */
    @Id
    private Long id;

    /**
     * 게시글 제목 (nori 형태소 분석기 적용)
     */
    @Field(type = FieldType.Text, analyzer = "nori")
    private String title;

    /**
     * 게시글 본문 (nori 형태소 분석기 적용)
     */
    @Field(type = FieldType.Text, analyzer = "nori")
    private String content;

    /**
     * 카테고리 (Keyword 타입 - 완전 일치 검색용)
     */
    @Field(type = FieldType.Keyword)
    private String category;

    /**
     * 태그 목록 (nori 형태소 분석기 적용)
     */
    @Field(type = FieldType.Text, analyzer = "nori")
    private String tags;

    /**
     * 조회수
     */
    @Field(type = FieldType.Integer)
    private int viewCount;

    /**
     * 작성자 회원 ID
     */
    @Field(type = FieldType.Long)
    private Long memberId;

    /**
     * 작성자 닉네임 (Keyword 타입 - 완전 일치 검색용)
     */
    @Field(type = FieldType.Keyword)
    private String memberNickname;

    /**
     * 생성 일시
     */
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime createdAt;

    /**
     * 수정 일시
     */
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime updatedAt;

    @Builder
    private ArticleDocument(Long id, String title, String content, String category,
                            String tags, int viewCount, Long memberId,
                            String memberNickname, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.category = category;
        this.tags = tags;
        this.viewCount = viewCount;
        this.memberId = memberId;
        this.memberNickname = memberNickname;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Article JPA 엔티티를 ArticleDocument로 변환한다.
     *
     * @param article 변환할 게시글 엔티티
     * @return 변환된 Elasticsearch 문서
     */
    public static ArticleDocument from(Article article) {
        return ArticleDocument.builder()
                .id(article.getId())
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
}
