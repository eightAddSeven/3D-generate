package com.design3d.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会话信息")
public class SessionDTO {

    @Schema(description = "会话ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String id;

    @Schema(description = "会话标题", example = "客厅方案设计")
    private String title;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "消息数量")
    private int messageCount;

    @Schema(description = "消息列表（仅详情接口返回）")
    private List<MessageDTO> messages;
}
