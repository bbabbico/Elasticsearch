package com.template.elastic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 게시글 수정 요청 DTO
 * <p>
 * 게시글 수정 시 클라이언트로부터 전달받는 데이터를 담는 레코드.
 * Bean Validation을 통해 필수 값 검증을 수행한다.
 * </p>
 *
 * @param title    수정할 제목 (필수, 최대 200자)
 * @param content  수정할 본문 (필수)
 * @param category 수정할 카테고리 (필수, 최대 50자)
 * @param tags     수정할 태그 목록 (콤마 구분 문자열, 최대 500자)
 */
public record ArticleUpdateRequest(
        @NotBlank(message = "제목은 필수 입력 항목입니다.")
        @Size(max = 200, message = "제목은 최대 200자까지 입력 가능합니다.")
        String title,

        @NotBlank(message = "본문은 필수 입력 항목입니다.")
        String content,

        @NotBlank(message = "카테고리는 필수 입력 항목입니다.")
        @Size(max = 50, message = "카테고리는 최대 50자까지 입력 가능합니다.")
        String category,

        @Size(max = 500, message = "태그는 최대 500자까지 입력 가능합니다.")
        String tags
) {
}
