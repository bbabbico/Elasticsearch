package com.template.elastic.dto;

import com.template.elastic.domain.article.Article;
import com.template.elastic.search.document.ArticleDocument;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 게시글 응답 DTO
 * <p>
 * 클라이언트에 반환할 게시글 정보를 담는 레코드.
 * Article JPA 엔티티와 ArticleDocument Elasticsearch 문서 모두로부터 변환 가능하다.
 * </p>
 *
 * @param id             게시글 ID
 * @param title          제목
 * @param content        본문
 * @param category       카테고리
 * @param tags           태그 (콤마 구분 문자열)
 * @param viewCount      조회수
 * @param memberNickname 작성자 닉네임
 * @param createdAt      생성 일시
 * @param updatedAt      수정 일시
 */
@Builder
public record ArticleResponse(
        Long id,
        String title,
        String content,
        String category,
        String tags,
        int viewCount,
        String memberNickname,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * Article JPA 엔티티로부터 응답 DTO를 생성한다.
     *
     * @param article 게시글 엔티티
     * @return 게시글 응답 DTO
     */
    public static ArticleResponse from(Article article) {
        return ArticleResponse.builder()
                .id(article.getId())
                .title(article.getTitle())
                .content(article.getContent())
                .category(article.getCategory())
                .tags(article.getTags())
                .viewCount(article.getViewCount())
                .memberNickname(article.getMember().getNickname())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }

    /**
     * ArticleDocument Elasticsearch 문서로부터 응답 DTO를 생성한다.
     *
     * @param document Elasticsearch 게시글 문서
     * @return 게시글 응답 DTO
     */
    public static ArticleResponse from(ArticleDocument document) {
        return ArticleResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .content(document.getContent())
                .category(document.getCategory())
                .tags(document.getTags())
                .viewCount(document.getViewCount())
                .memberNickname(document.getMemberNickname())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
