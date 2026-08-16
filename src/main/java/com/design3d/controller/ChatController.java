package com.design3d.controller;

import com.design3d.model.dto.ApiResponse;
import com.design3d.model.dto.ChatResponse;
import com.design3d.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
@RequiredArgsConstructor
@Tag(name = "3DBuildingModelingEngine · 对话建模", description = "三维建模引擎的会话式模型生成接口")
public class ChatController {

    private final ChatService chatService;

    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "通过对话调用三维建模引擎",
            description = """
                    发送一条消息到指定会话。根据上传内容自动路由:
                    - 仅 text: 文本对话
                    - text + layoutJson: JSON 直接生成 3D 模型

                    响应返回本次对话产生的所有新消息。"""
    )
    public ApiResponse<ChatResponse> sendMessage(
            @PathVariable String sessionId,
            @Parameter(description = "文本消息（可选）")
            @RequestPart(name = "text", required = false) String text,
            @Parameter(description = "房间布局 JSON 文件（可选）")
            @RequestPart(name = "layoutJson", required = false)
            @Schema(type = "string", format = "binary")
            MultipartFile layoutJson) {

        ChatResponse response = chatService.processChat(sessionId, text, layoutJson);
        return ApiResponse.success(response);
    }
}
