package com.design3d.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "消息信息")
public class MessageDTO {

    @Schema(description = "消息ID")
    private String id;

    @Schema(description = "角色: user/assistant/system", example = "assistant")
    private String role;

    @Schema(description = "消息类型: text/model/json_input", example = "model")
    private String msgType;

    @Schema(description = "消息正文内容")
    private String content;

    @Schema(description = "扩展元数据 (JSON)")
    private String metadata;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
