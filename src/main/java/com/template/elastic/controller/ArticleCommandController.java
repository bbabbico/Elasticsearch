package com.template.elastic.controller;

import com.template.elastic.dto.ArticleCreateRequest;
import com.template.elastic.dto.ArticleResponse;
import com.template.elastic.dto.ArticleUpdateRequest;
import com.template.elastic.service.ArticleCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 게시글 명령(CUD) 컨트롤러
 * <p>
 * 게시글의 생성, 수정, 삭제 API를 제공한다.
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/articles")
public class ArticleCommandController {

    private final ArticleCommandService commandService;

    /**
     * 게시글 생성
     *
     * @param request 게시글 생성 요청 DTO
     * @return 생성된 게시글 응답 (201 Created)
     */
    @PostMapping
    public ResponseEntity<ArticleResponse> create(@RequestBody @Valid ArticleCreateRequest request) {
        ArticleResponse response = commandService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 게시글 수정
     *
     * @param id      수정할 게시글 ID
     * @param request 게시글 수정 요청 DTO
     * @return 수정된 게시글 응답 (200 OK)
     */
    @PutMapping("/{id}")
    public ResponseEntity<ArticleResponse> update(@PathVariable Long id,
                                                   @RequestBody @Valid ArticleUpdateRequest request) {
        ArticleResponse response = commandService.update(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 게시글 삭제
     *
     * @param id 삭제할 게시글 ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        commandService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
