package com.template.elastic.domain.article;

import com.template.elastic.domain.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 게시글 엔티티
 * <p>
 * 게시글 정보를 관리하는 JPA 엔티티.
 * category와 createdAt 컬럼에 인덱스를 설정하여 조회 성능을 최적화한다.
 * tags는 콤마(,)로 구분된 문자열로 저장한다.
 * </p>
 */
@Entity
@Table(name = "articles", indexes = {
        @Index(name = "idx_article_category", columnList = "category"),
        @Index(name = "idx_article_created_at", columnList = "createdAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article {

    /**
     * 게시글 고유 식별자 (자동 생성)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 게시글 제목 (최대 200자)
     */
    @Column(nullable = false, length = 200)
    private String title;

    /**
     * 게시글 본문 (TEXT 타입)
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 카테고리 (최대 50자)
     */
    @Column(nullable = false, length = 50)
    private String category;

    /**
     * 태그 목록 (콤마 구분 문자열, 최대 500자)
     */
    @Column(length = 500)
    private String tags;

    /**
     * 조회수 (기본값 0)
     */
    @Column(nullable = false)
    private int viewCount = 0;

    /**
     * 작성자 (지연 로딩)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * 생성 일시
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * 수정 일시
     */
    private LocalDateTime updatedAt;

    @Builder
    private Article(String title, String content, String category, String tags, Member member) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.tags = tags;
        this.member = member;
    }

    /**
     * 엔티티 최초 저장 시 생성일시와 수정일시를 현재 시각으로 설정한다.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 엔티티 수정 시 수정일시를 현재 시각으로 갱신한다.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 게시글 정보를 수정한다.
     *
     * @param title    수정할 제목
     * @param content  수정할 본문
     * @param category 수정할 카테고리
     * @param tags     수정할 태그 (콤마 구분 문자열)
     */
    public void update(String title, String content, String category, String tags) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.tags = tags;
    }
}
