package com.template.elastic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 게시글 생성 요청 DTO
 * <p>
 * 게시글 작성 시 클라이언트로부터 전달받는 데이터를 담는 레코드.
 * Bean Validation을 통해 필수 값 검증을 수행한다.
 * </p>
 *
 * @param title    게시글 제목 (필수, 최대 200자)
 * @param content  게시글 본문 (필수)
 * @param category 카테고리 (필수, 최대 50자)
 * @param tags     태그 목록 (콤마 구분 문자열, 최대 500자)
 * @param memberId 작성자 회원 ID (필수)
 */
public record ArticleCreateRequest(
        @NotBlank(message = "제목은 필수 입력 항목입니다.")
        @Size(max = 200, message = "제목은 최대 200자까지 입력 가능합니다.")
        String title,

        @NotBlank(message = "본문은 필수 입력 항목입니다.")
        String content,

        @NotBlank(message = "카테고리는 필수 입력 항목입니다.")
        @Size(max = 50, message = "카테고리는 최대 50자까지 입력 가능합니다.")
        String category,

        @Size(max = 500, message = "태그는 최대 500자까지 입력 가능합니다.")
        String tags,

        @NotNull(message = "작성자 ID는 필수 입력 항목입니다.")
        Long memberId
) {
}
