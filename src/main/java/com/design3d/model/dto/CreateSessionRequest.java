package com.design3d.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建会话请求")
public class CreateSessionRequest {

    @Size(max = 200)
    @Schema(description = "会话标题（可选，默认'新对话'）", example = "客厅方案设计")
    private String title;
}
