package com.template.elastic.domain.member.repository;

import com.template.elastic.domain.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원 JPA 레포지토리
 * <p>
 * Member 엔티티에 대한 기본 CRUD 연산을 제공한다.
 * </p>
 */
public interface MemberRepository extends JpaRepository<Member, Long> {
}
