package com.design3d.model.layout;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 房间布局 — 统一的二维结构化数据协议
 * <p>
 * 作为 AI 输出、人工输入、3D 生成引擎之间的标准接口。
 * 所有尺寸单位为毫米 (mm)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "房间布局")
public class RoomLayout {

    @Schema(description = "协议版本", example = "1.0")
    @Builder.Default
    private String version = "1.0";

    @Schema(description = "尺寸单位", example = "mm")
    @Builder.Default
    @Pattern(regexp = "mm", message = "当前建模接口仅支持 mm 单位")
    private String unit = "mm";

    @Valid
    @NotNull
    @Schema(description = "整体空间约束")
    private LayoutInfo layout;

    @Valid
    @Schema(description = "房间列表")
    @Builder.Default
    private List<Room> rooms = new ArrayList<>();

    @Valid
    @Schema(description = "公共区域的家具（不属于特定房间）")
    @Builder.Default
    private List<Furniture> furniture = new ArrayList<>();

    // ---- 内嵌类 ----

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "整体空间信息")
    public static class LayoutInfo {

        @Min(1000) @Max(50000)
        @Schema(description = "整体空间宽度 (mm)", example = "6000")
        private double width;

        @Min(1000) @Max(50000)
        @Schema(description = "整体空间深度 (mm)", example = "5000")
        private double depth;

        @Min(2000) @Max(6000)
        @Schema(description = "天花板高度 (mm)，默认 2800", example = "2800")
        @Builder.Default
        private double height = 2800;

        @Min(100) @Max(500)
        @Schema(description = "墙体厚度 (mm)，默认 240", example = "240")
        @Builder.Default
        private double wallThickness = 240;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "房间信息")
    public static class Room {

        @NotBlank
        @Schema(description = "房间唯一标识", example = "living_1")
        private String id;

        @NotBlank
        @Schema(description = "房间名称", example = "客厅")
        private String name;

        @NotBlank
        @Schema(description = "房间类型", example = "living_room",
                allowableValues = {"living_room", "bedroom", "kitchen", "bathroom",
                        "dining_room", "study", "hallway", "balcony", "other"})
        private String type;

        @Valid
        @NotNull
        @Schema(description = "房间左下角坐标 (mm)")
        private Position position;

        @Valid
        @NotNull
        @Schema(description = "房间尺寸")
        private Size2D size;

        @Valid
        @Schema(description = "地面材质")
        private MaterialDef floor;

        @Valid
        @Schema(description = "天花板材质")
        private MaterialDef ceiling;

        @Valid
        @Schema(description = "墙体列表")
        @Builder.Default
        private List<Wall> walls = new ArrayList<>();

        @Valid
        @Schema(description = "房间内的家具")
        @Builder.Default
        private List<Furniture> furniture = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "墙体")
    public static class Wall {

        @NotBlank
        @Schema(description = "墙体标识", example = "wall_north")
        private String id;

        @Valid
        @NotNull
        @Schema(description = "起点坐标")
        private Position start;

        @Valid
        @NotNull
        @Schema(description = "终点坐标")
        private Position end;

        @Schema(description = "墙高 (mm)，默认取 layout.height", example = "2800")
        private Double height;

        @Schema(description = "材质名称", example = "paint_beige")
        private String material;

        @Schema(description = "颜色 (hex)", example = "#F5F0E8")
        private String color;

        @Valid
        @Schema(description = "门窗洞口")
        @Builder.Default
        private List<Opening> openings = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "门窗洞口")
    public static class Opening {

        @NotBlank
        @Schema(description = "洞口类型", example = "window",
                allowableValues = {"door", "window", "archway"})
        private String type;

        @NotNull
        @Schema(description = "沿墙方向的位置偏移 (mm)", example = "2000")
        private Double position;

        @Min(400) @Max(5000)
        @Schema(description = "洞口宽度 (mm)", example = "1800")
        private double width;

        @Min(1000) @Max(4000)
        @Schema(description = "洞口高度 (mm)", example = "1500")
        private double height;

        @Schema(description = "窗台高度 (mm)，仅 window 有效，默认 900", example = "900")
        private Double sillHeight;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "家具")
    public static class Furniture {

        @NotBlank
        @Schema(description = "家具唯一标识", example = "sofa_1")
        private String id;

        @NotBlank
        @Schema(description = "家具类型", example = "sofa")
        private String type;

        @Schema(description = "家具分类", example = "seating",
                allowableValues = {"seating", "table", "storage", "bed", "lighting",
                        "decoration", "appliance", "other"})
        private String category;

        @Valid
        @NotNull
        @Schema(description = "家具底部中心坐标")
        private Position position;

        @Schema(description = "绕 Y 轴旋转角度 (度)", example = "0")
        @Builder.Default
        private double rotation = 0;

        @Valid
        @NotNull
        @Schema(description = "家具三维尺寸")
        private Size3D size;

        @Schema(description = "材质名称", example = "fabric_gray")
        private String material;

        @Schema(description = "颜色 (hex)", example = "#808080")
        private String color;

        @Schema(description = "所属房间 ID（顶层 furniture 数组时使用）")
        private String roomId;
    }

    // ---- 基础类型 ----

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "二维坐标")
    public static class Position {
        @Schema(description = "X 坐标 (mm)")
        private double x;
        @Schema(description = "Z 坐标 (mm)")
        private double z;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "二维尺寸")
    public static class Size2D {
        @Min(100)
        @Schema(description = "宽度 (mm)")
        private double width;
        @Min(100)
        @Schema(description = "深度 (mm)")
        private double depth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "三维尺寸")
    public static class Size3D {
        @Min(10) private double width;
        @Min(10) private double depth;
        @Min(10) private double height;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "材质定义")
    public static class MaterialDef {
        @Schema(description = "材质名称", example = "wood_light")
        private String material;
        @Schema(description = "颜色 (hex)", example = "#D2B48C")
        private String color;
    }
}
