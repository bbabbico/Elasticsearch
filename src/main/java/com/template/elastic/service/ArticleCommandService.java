package com.template.elastic.service;

import com.template.elastic.domain.article.Article;
import com.template.elastic.domain.article.repository.ArticleRepository;
import com.template.elastic.domain.member.Member;
import com.template.elastic.domain.member.repository.MemberRepository;
import com.template.elastic.dto.ArticleCreateRequest;
import com.template.elastic.dto.ArticleResponse;
import com.template.elastic.dto.ArticleUpdateRequest;
import com.template.elastic.kafka.event.ArticleEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 쓰기(Command) 서비스
 * <p>
 * 게시글의 생성, 수정, 삭제 비즈니스 로직을 처리한다.
 * 각 CUD 작업 후 Spring ApplicationEvent를 발행하여,
 * 트랜잭션 커밋 후 @TransactionalEventListener를 통해
 * Kafka 이벤트가 발행되도록 한다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ArticleCommandService {

    private final ArticleRepository articleRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 게시글을 생성한다.
     * <p>
     * 회원 조회 → Article 엔티티 생성 → DB 저장 → Spring Event 발행 순으로 처리한다.
     * 트랜잭션 커밋 후 Kafka를 통해 ES/Redis 동기화가 수행된다.
     * </p>
     *
     * @param request 게시글 생성 요청 DTO
     * @return 생성된 게시글 응답 DTO
     * @throws IllegalArgumentException 회원을 찾을 수 없는 경우
     */
    public ArticleResponse create(ArticleCreateRequest request) {
        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "회원을 찾을 수 없습니다. id=" + request.memberId()));

        Article article = Article.builder()
                .title(request.title())
                .content(request.content())
                .category(request.category())
                .tags(request.tags())
                .member(member)
                .build();

        Article saved = articleRepository.save(article);

        // 트랜잭션 커밋 후 Kafka 이벤트 발행을 위해 Spring ApplicationEvent 발행
        eventPublisher.publishEvent(ArticleEvent.ofCreate(saved));

        log.info("[ArticleCommandService] 게시글 생성 완료: id={}", saved.getId());
        return ArticleResponse.from(saved);
    }

    /**
     * 게시글을 수정한다.
     * <p>
     * 게시글 조회 → 엔티티 수정(dirty checking) → Spring Event 발행 순으로 처리한다.
     * JPA dirty checking에 의해 트랜잭션 커밋 시 UPDATE 쿼리가 자동 실행된다.
     * </p>
     *
     * @param articleId 수정할 게시글 ID
     * @param request   게시글 수정 요청 DTO
     * @return 수정된 게시글 응답 DTO
     * @throws IllegalArgumentException 게시글을 찾을 수 없는 경우
     */
    public ArticleResponse update(Long articleId, ArticleUpdateRequest request) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "게시글을 찾을 수 없습니다. id=" + articleId));

        article.update(request.title(), request.content(), request.category(), request.tags());

        // 트랜잭션 커밋 후 Kafka 이벤트 발행을 위해 Spring ApplicationEvent 발행
        eventPublisher.publishEvent(ArticleEvent.ofUpdate(article));

        log.info("[ArticleCommandService] 게시글 수정 완료: id={}", articleId);
        return ArticleResponse.from(article);
    }

    /**
     * 게시글을 삭제한다.
     * <p>
     * 게시글 존재 여부 확인 → DB 삭제 → Spring Event 발행 순으로 처리한다.
     * 트랜잭션 커밋 후 Kafka를 통해 ES/Redis에서도 삭제가 수행된다.
     * </p>
     *
     * @param articleId 삭제할 게시글 ID
     * @throws IllegalArgumentException 게시글을 찾을 수 없는 경우
     */
    public void delete(Long articleId) {
        if (!articleRepository.existsById(articleId)) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다. id=" + articleId);
        }

        articleRepository.deleteById(articleId);

        // 트랜잭션 커밋 후 Kafka 이벤트 발행을 위해 Spring ApplicationEvent 발행
        eventPublisher.publishEvent(ArticleEvent.ofDelete(articleId));

        log.info("[ArticleCommandService] 게시글 삭제 완료: id={}", articleId);
    }
}
