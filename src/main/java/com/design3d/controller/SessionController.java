package com.design3d.controller;

import com.design3d.model.dto.*;
import com.design3d.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Tag(name = "3DBuildingModelingEngine · 会话管理", description = "三维建模引擎的对话会话管理")
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建新会话")
    public ApiResponse<SessionDTO> createSession(@Valid @RequestBody(required = false) CreateSessionRequest request) {
        String title = request != null ? request.getTitle() : null;
        SessionDTO session = sessionService.createSession(title);
        return ApiResponse.success("会话已创建", session);
    }

    @GetMapping
    @Operation(summary = "获取会话列表（分页）")
    public ApiResponse<Page<SessionDTO>> listSessions(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            @Parameter(hidden = true) Pageable pageable) {
        Page<SessionDTO> sessions = sessionService.listSessions(pageable);
        return ApiResponse.success(sessions);
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "获取会话详情（含消息列表）")
    public ApiResponse<SessionDTO> getSession(
            @PathVariable String sessionId) {
        SessionDTO session = sessionService.getSession(sessionId);
        return ApiResponse.success(session);
    }

    @PatchMapping("/{sessionId}")
    @Operation(summary = "更新会话标题")
    public ApiResponse<SessionDTO> updateSession(
            @PathVariable String sessionId,
            @Valid @RequestBody UpdateSessionRequest request) {
        SessionDTO session = sessionService.updateSession(sessionId, request.getTitle());
        return ApiResponse.success("标题已更新", session);
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除会话")
    public void deleteSession(@PathVariable String sessionId) {
        sessionService.deleteSession(sessionId);
    }
}
