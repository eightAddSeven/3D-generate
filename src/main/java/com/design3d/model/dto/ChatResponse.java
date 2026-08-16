package com.design3d.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "对话响应（包含本次请求产生的新消息列表）")
public class ChatResponse {

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "本次对话产生的新消息列表")
    private List<MessageDTO> newMessages;

    @Schema(description = "处理耗时（秒）")
    private double processingTimeSeconds;
}
