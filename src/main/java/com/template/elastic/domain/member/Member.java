package com.template.elastic.domain.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원 엔티티
 * <p>
 * 사용자 계정 정보를 관리하는 JPA 엔티티.
 * username과 email은 고유값으로 설정되어 중복 가입을 방지한다.
 * </p>
 */
@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    /**
     * 회원 고유 식별자 (자동 생성)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 사용자 아이디 (고유값, 최대 50자)
     */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /**
     * 이메일 주소 (고유값, 최대 100자)
     */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /**
     * 닉네임 (최대 30자)
     */
    @Column(nullable = false, length = 30)
    private String nickname;

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
    private Member(String username, String email, String nickname) {
        this.username = username;
        this.email = email;
        this.nickname = nickname;
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
}
