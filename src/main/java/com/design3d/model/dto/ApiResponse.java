package com.design3d.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一的 API 响应包装
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一API响应")
public class ApiResponse<T> {

    @Schema(description = "状态码", example = "200")
    private int code;

    @Schema(description = "提示信息", example = "操作成功")
    private String message;

    @Schema(description = "响应数据")
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder().code(200).message("成功").data(data).build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder().code(200).message(message).data(data).build();
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder().code(code).message(message).build();
    }
}
