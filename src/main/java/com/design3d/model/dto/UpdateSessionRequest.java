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
@Schema(description = "更新会话请求")
public class UpdateSessionRequest {

    @Size(min = 1, max = 200)
    @Schema(description = "新的会话标题", example = "客厅方案设计v2")
    private String title;
}
