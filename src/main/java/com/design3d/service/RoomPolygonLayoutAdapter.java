package com.design3d.service;

import com.design3d.model.layout.RoomLayout;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 将房间多边形 JSON 转换为经过拓扑规范化的 RoomLayout。
 */
@Component
public class RoomPolygonLayoutAdapter {

    private final double configuredScale;

    private final double configuredWallHeight;

    private final double configuredWallThickness;

    private final double snapEpsilon;

    private final double orthogonalAngleDegrees;

    private final double minSegmentLength;

    @Autowired
    public RoomPolygonLayoutAdapter(

            @Value(
                    "${app.layout.polygon-format.mm-per-coordinate:10}"
            )
            double configuredScale,

            @Value(
                    "${app.layout.default-wall-height:2800}"
            )
            double configuredWallHeight,

            @Value(
                    "${app.layout.polygon-format.wall-thickness:150}"
            )
            double configuredWallThickness,

            @Value(
                    "${app.layout.polygon-format.snap-epsilon:2.0}"
            )
            double snapEpsilon,

            @Value(
                    "${app.layout.polygon-format.orthogonal-angle-degrees:4.0}"
            )
            double orthogonalAngleDegrees,

            @Value(
                    "${app.layout.polygon-format.min-segment-length:1.0}"
            )
            double minSegmentLength) {

        require(
                configuredScale > 0,
                "房间多边形坐标换算比例必须大于 0"
        );

        require(
                configuredWallHeight >= 2000
                        && configuredWallHeight <= 6000,
                "默认墙高必须位于 2000~6000 mm"
        );

        require(
                configuredWallThickness >= 100
                        && configuredWallThickness <= 500,
                "房间多边形默认墙厚必须位于 100~500 mm"
        );

        require(
                snapEpsilon > 0,
                "墙线吸附阈值必须大于 0"
        );

        require(
                orthogonalAngleDegrees >= 0
                        && orthogonalAngleDegrees <= 15,
                "墙线正交化角度阈值必须位于 0~15 度"
        );

        require(
                minSegmentLength > 0,
                "最短墙线阈值必须大于 0"
        );

        this.configuredScale =
                configuredScale;

        this.configuredWallHeight =
                configuredWallHeight;

        this.configuredWallThickness =
                configuredWallThickness;

        this.snapEpsilon =
                snapEpsilon;

        this.orthogonalAngleDegrees =
                orthogonalAngleDegrees;

        this.minSegmentLength =
                minSegmentLength;
    }

    /**
     * 保留旧的三参数构造函数。
     *
     * 这样你当前已有的测试代码：
     *
     * new RoomPolygonLayoutAdapter(
     *     10,
     *     2800,
     *     240
     * )
     *
     * 不需要修改。
     */
    public RoomPolygonLayoutAdapter(
            double scale,
            double wallHeight,
            double wallThickness) {

        this(
                scale,
                wallHeight,
                wallThickness,
                2.0,
                4.0,
                1.0
        );
    }

    /**
     * 主转换入口。
     */
    public RoomLayout adapt(
            JsonNode source) {

        JsonNode roomNodes =
                source.isObject()
                        ? source.get(
                        "raw_room_data"
                )
                        : source;

        if (roomNodes == null
                ||
                !roomNodes.isArray()
                ||
                roomNodes.isEmpty()) {

            throw new IllegalArgumentException(
                    "房间多边形 JSON 必须是房间数组，"
                            + "或包含 raw_room_data 数组"
            );
        }

        /*
         * 1. 解析所有房间 polygon。
         */
        List<PolygonRoom> polygons =
                parsePolygons(
                        roomNodes
                );

        /*
         * 2. 计算全局源坐标范围。
         */
        Bounds bounds =
                boundsOf(
                        polygons
                );

        /*
         * 3. 读取可选物理尺寸信息。
         */
        JsonNode metadata =
                source.isObject()
                        ? source.get(
                        "layout_meta"
                )
                        : null;

        double wallHeight =
                metadataNumber(
                        metadata,
                        "wall_height_mm",
                        configuredWallHeight
                );

        double wallThickness =
                metadataNumber(
                        metadata,
                        "wall_thickness_mm",
                        configuredWallThickness
                );

        /*
         * 可以使用：
         *
         * mm_per_coordinate
         *
         * 或：
         *
         * width_mm
         * depth_mm
         *
         * 推导真实比例。
         */
        ScaleFactors scale =
                resolveScaleFactors(
                        metadata,
                        bounds,
                        wallThickness
                );

        validatePhysicalParameters(
                scale,
                wallHeight,
                wallThickness
        );

        /*
         * 墙中心线外围还需要留半墙厚。
         */
        double margin =
                wallThickness / 2.0;

        double width =
                Math.max(
                        1000,
                        bounds.width()
                                * scale.x()
                                + margin * 2.0
                );

        double depth =
                Math.max(
                        1000,
                        bounds.height()
                                * scale.y()
                                + margin * 2.0
                );

        if (width > 50000
                ||
                depth > 50000) {

            throw new IllegalArgumentException(
                    "房间多边形换算后超过 50000 mm，"
                            + "请检查比例尺或 layout_meta"
            );
        }

        /*
         * 4. polygon →
         *    原始候选墙线。
         */
        List<WallTopologyNormalizer.RawSegment>
                rawSegments =
                extractSegments(
                        polygons
                );

        /*
         * 5. 墙线规范化。
         */
        WallTopologyNormalizer normalizer =
                new WallTopologyNormalizer(
                        snapEpsilon,
                        orthogonalAngleDegrees,
                        minSegmentLength
                );

        List<WallTopologyNormalizer.WallSegment>
                canonicalWalls =
                normalizer.normalize(
                        rawSegments
                );

        if (canonicalWalls.isEmpty()) {

            throw new IllegalArgumentException(
                    "房间多边形中没有可用于建模的有效墙线"
            );
        }

        /*
         * 6. 一个规范化后的物理墙只建一次。
         *
         * 即使它同时属于两个房间，
         * 也不能在 GLB 中重复生成。
         */
        List<List<RoomLayout.Wall>>
                wallsByRoom =
                new ArrayList<>();

        for (int i = 0;
             i < polygons.size();
             i++) {

            wallsByRoom.add(
                    new ArrayList<>()
            );
        }

        int wallNumber = 1;

        for (WallTopologyNormalizer.WallSegment
                segment : canonicalWalls) {

            /*
             * 一堵共享墙可能来源于多个房间。
             *
             * 这里选择编号最小的房间
             * 作为该墙的几何 owner。
             *
             * 这样 GLB 中只会生成一次。
             */
            int ownerRoom =
                    segment.roomIndexes()
                            .stream()
                            .min(
                                    Integer::compareTo
                            )
                            .orElse(0);

            ownerRoom =
                    Math.max(
                            0,
                            Math.min(
                                    ownerRoom,
                                    polygons.size() - 1
                            )
                    );

            RoomLayout.Wall wall =
                    RoomLayout.Wall
                            .builder()
                            .id(
                                    "wall_"
                                            + wallNumber++
                            )
                            .start(
                                    transform(
                                            segment.start(),
                                            bounds,
                                            scale,
                                            margin
                                    )
                            )
                            .end(
                                    transform(
                                            segment.end(),
                                            bounds,
                                            scale,
                                            margin
                                    )
                            )
                            .height(
                                    wallHeight
                            )
                            .color(
                                    "#D9D9D9"
                            )
                            .build();

            wallsByRoom
                    .get(ownerRoom)
                    .add(wall);
        }

        /*
         * 7. 重新构建房间信息。
         */
        List<RoomLayout.Room> rooms =
                new ArrayList<>();

        for (int roomIndex = 0;
             roomIndex < polygons.size();
             roomIndex++) {

            PolygonRoom polygon =
                    polygons.get(
                            roomIndex
                    );

            Bounds roomBounds =
                    boundsOfPoints(
                            polygon.points()
                    );

            RoomLayout.Room room =
                    RoomLayout.Room
                            .builder()
                            .id(
                                    "room_"
                                            + (roomIndex + 1)
                            )
                            .name(
                                    polygon.name()
                            )
                            .type(
                                    roomType(
                                            polygon.name()
                                    )
                            )
                            .position(
                                    transform(
                                            new WallTopologyNormalizer.Point(
                                                    roomBounds.minX(),
                                                    roomBounds.maxY()
                                            ),
                                            bounds,
                                            scale,
                                            margin
                                    )
                            )
                            .size(
                                    new RoomLayout.Size2D(
                                            Math.max(
                                                    100,
                                                    roomBounds.width()
                                                            * scale.x()
                                            ),
                                            Math.max(
                                                    100,
                                                    roomBounds.height()
                                                            * scale.y()
                                            )
                                    )
                            )
                            .walls(
                                    wallsByRoom.get(
                                            roomIndex
                                    )
                            )
                            .build();

            rooms.add(room);
        }

        /*
         * 8. 返回统一 RoomLayout。
         */
        return RoomLayout
                .builder()
                .version(
                        "1.0"
                )
                .unit(
                        "mm"
                )
                .layout(
                        RoomLayout.LayoutInfo
                                .builder()
                                .width(
                                        width
                                )
                                .depth(
                                        depth
                                )
                                .height(
                                        wallHeight
                                )
                                .wallThickness(
                                        wallThickness
                                )
                                .build()
                )
                .rooms(
                        rooms
                )
                .build();
    }

    /**
     * 解析房间 polygon。
     */
    private List<PolygonRoom> parsePolygons(
            JsonNode roomNodes) {

        List<PolygonRoom> polygons =
                new ArrayList<>();

        for (int i = 0;
             i < roomNodes.size();
             i++) {

            JsonNode room =
                    roomNodes.get(i);

            JsonNode coordinates =
                    room.get(
                            "coordinates"
                    );

            if (!room.isObject()
                    ||
                    !room.path(
                            "name"
                    ).isTextual()
                    ||
                    coordinates == null
                    ||
                    !coordinates.isArray()
                    ||
                    coordinates.size() < 3) {

                throw new IllegalArgumentException(
                        "第 "
                                + i
                                + " 个房间必须包含 name "
                                + "和至少 3 个 coordinates 坐标"
                );
            }

            List<WallTopologyNormalizer.Point>
                    points =
                    new ArrayList<>();

            for (JsonNode coordinate
                    : coordinates) {

                WallTopologyNormalizer.Point point =
                        point(
                                coordinate,
                                i
                        );

                /*
                 * 清理连续重复点。
                 */
                if (points.isEmpty()
                        ||
                        points.get(
                                points.size() - 1
                        ).distance(point) > 0.1) {

                    points.add(point);
                }
            }

            /*
             * 如果 JSON 主动重复了首点，
             * 删除最后一个重复点。
             */
            if (points.size() > 1
                    &&
                    points.get(0)
                            .distance(
                                    points.get(
                                            points.size() - 1
                                    )
                            ) <= 0.1) {

                points.remove(
                        points.size() - 1
                );
            }

            if (points.size() < 3) {

                throw new IllegalArgumentException(
                        "第 "
                                + i
                                + " 个房间去除重复点后不足 3 个有效顶点"
                );
            }

            polygons.add(
                    new PolygonRoom(
                            room.get(
                                    "name"
                            ).asText(),
                            points
                    )
            );
        }

        return polygons;
    }

    /**
     * 房间 polygon →
     * 所有原始边。
     */
    private List<WallTopologyNormalizer.RawSegment>
    extractSegments(
            List<PolygonRoom> polygons) {

        List<WallTopologyNormalizer.RawSegment>
                segments =
                new ArrayList<>();

        for (int roomIndex = 0;
             roomIndex < polygons.size();
             roomIndex++) {

            List<WallTopologyNormalizer.Point>
                    points =
                    polygons
                            .get(roomIndex)
                            .points();

            for (int i = 0;
                 i < points.size();
                 i++) {

                WallTopologyNormalizer.Point start =
                        points.get(i);

                WallTopologyNormalizer.Point end =
                        points.get(
                                (i + 1)
                                        % points.size()
                        );

                /*
                 * 过滤极短噪声边。
                 */
                if (start.distance(end)
                        >= minSegmentLength) {

                    segments.add(
                            new WallTopologyNormalizer.RawSegment(
                                    start,
                                    end,
                                    roomIndex
                            )
                    );
                }
            }
        }

        return segments;
    }

    /**
     * JSON [x,y] →
     * Point。
     */
    private WallTopologyNormalizer.Point point(
            JsonNode node,
            int roomIndex) {

        if (!node.isArray()
                ||
                node.size() != 2
                ||
                !node.get(0).isNumber()
                ||
                !node.get(1).isNumber()) {

            throw new IllegalArgumentException(
                    "第 "
                            + roomIndex
                            + " 个房间包含无效坐标，"
                            + "应为 [x, y]"
            );
        }

        return new WallTopologyNormalizer.Point(
                node.get(0)
                        .asDouble(),
                node.get(1)
                        .asDouble()
        );
    }

    /**
     * 图像坐标 →
     * RoomLayout mm 坐标。
     *
     * 原始图像 Y 轴向下，
     * 3D 中 Z 方向需要翻转。
     */
    private RoomLayout.Position transform(
            WallTopologyNormalizer.Point point,
            Bounds bounds,
            ScaleFactors scale,
            double margin) {

        return new RoomLayout.Position(

                (bounds.maxX()
                        - point.x())
                        * scale.x()
                        + margin,

                (bounds.maxY()
                        - point.y())
                        * scale.y()
                        + margin
        );
    }

    /**
     * 解析真实比例尺。
     *
     * 优先级：
     *
     * 1. layout_meta.mm_per_coordinate
     * 2. layout_meta.width_mm / depth_mm
     * 3. application.yml 默认比例
     */
    private ScaleFactors resolveScaleFactors(
            JsonNode metadata,
            Bounds bounds,
            double wallThickness) {

        Double directScale =
                optionalPositiveNumber(
                        metadata,
                        "mm_per_coordinate"
                );

        /*
         * 显式比例尺优先。
         */
        if (directScale != null) {

            return new ScaleFactors(
                    directScale,
                    directScale
            );
        }

        Double widthMm =
                optionalPositiveNumber(
                        metadata,
                        "width_mm"
                );

        Double depthMm =
                optionalPositiveNumber(
                        metadata,
                        "depth_mm"
                );

        double scaleX =
                configuredScale;

        double scaleY =
                configuredScale;

        /*
         * width_mm / depth_mm
         * 表示模型最终外包尺寸。
         *
         * 所以要先扣掉一个墙厚，
         * 再计算墙中心线比例。
         */
        if (widthMm != null) {

            require(
                    widthMm > wallThickness,
                    "layout_meta.width_mm 必须大于墙厚"
            );

            require(
                    bounds.width() > 0.1,
                    "源多边形宽度过小，"
                            + "无法根据 width_mm 计算比例尺"
            );

            scaleX =
                    (widthMm
                            - wallThickness)
                            / bounds.width();
        }

        if (depthMm != null) {

            require(
                    depthMm > wallThickness,
                    "layout_meta.depth_mm 必须大于墙厚"
            );

            require(
                    bounds.height() > 0.1,
                    "源多边形深度过小，"
                            + "无法根据 depth_mm 计算比例尺"
            );

            scaleY =
                    (depthMm
                            - wallThickness)
                            / bounds.height();
        }

        /*
         * 只给一个真实尺寸时，
         * 保持 X/Y 等比例。
         */
        if (widthMm != null
                &&
                depthMm == null) {

            scaleY = scaleX;

        } else if (
                depthMm != null
                        &&
                        widthMm == null) {

            scaleX = scaleY;
        }

        return new ScaleFactors(
                scaleX,
                scaleY
        );
    }

    private double metadataNumber(
            JsonNode metadata,
            String field,
            double defaultValue) {

        Double value =
                optionalPositiveNumber(
                        metadata,
                        field
                );

        return value != null
                ? value
                : defaultValue;
    }

    private Double optionalPositiveNumber(
            JsonNode metadata,
            String field) {

        if (metadata == null
                ||
                metadata.isNull()
                ||
                !metadata.has(field)
                ||
                metadata.get(field).isNull()) {

            return null;
        }

        JsonNode value =
                metadata.get(field);

        if (!value.isNumber()
                ||
                value.asDouble() <= 0) {

            throw new IllegalArgumentException(
                    "layout_meta."
                            + field
                            + " 必须是正数"
            );
        }

        return value.asDouble();
    }

    /**
     * 保证输出符合 RoomLayout 验证规则。
     */
    private void validatePhysicalParameters(
            ScaleFactors scale,
            double wallHeight,
            double wallThickness) {

        require(
                scale.x() > 0
                        &&
                        scale.y() > 0,
                "坐标换算比例必须大于 0"
        );

        require(
                wallHeight >= 2000
                        &&
                        wallHeight <= 6000,
                "wall_height_mm 必须位于 2000~6000 mm"
        );

        require(
                wallThickness >= 100
                        &&
                        wallThickness <= 500,
                "wall_thickness_mm 必须位于 100~500 mm"
        );
    }

    private Bounds boundsOf(
            List<PolygonRoom> polygons) {

        List<WallTopologyNormalizer.Point>
                points =
                polygons.stream()
                        .flatMap(
                                room ->
                                        room.points()
                                                .stream()
                        )
                        .toList();

        return boundsOfPoints(
                points
        );
    }

    private Bounds boundsOfPoints(
            List<WallTopologyNormalizer.Point>
                    points) {

        double minX =
                points.stream()
                        .mapToDouble(
                                WallTopologyNormalizer.Point::x
                        )
                        .min()
                        .orElseThrow();

        double maxX =
                points.stream()
                        .mapToDouble(
                                WallTopologyNormalizer.Point::x
                        )
                        .max()
                        .orElseThrow();

        double minY =
                points.stream()
                        .mapToDouble(
                                WallTopologyNormalizer.Point::y
                        )
                        .min()
                        .orElseThrow();

        double maxY =
                points.stream()
                        .mapToDouble(
                                WallTopologyNormalizer.Point::y
                        )
                        .max()
                        .orElseThrow();

        return new Bounds(
                minX,
                maxX,
                minY,
                maxY
        );
    }

    /**
     * 房间名称 →
     * 标准类型。
     */
    private String roomType(
            String name) {

        String value =
                name.toLowerCase(
                        Locale.ROOT
                );

        if (value.contains("living")
                ||
                value.contains("客厅")) {

            return "living_room";
        }

        if (value.contains("bedroom")
                ||
                value.contains("卧室")) {

            return "bedroom";
        }

        if (value.contains("kitchen")
                ||
                value.contains("厨房")
                ||
                value.contains("后厨")) {

            return "kitchen";
        }

        if (value.contains("bathroom")
                ||
                value.contains("toilet")
                ||
                value.contains("卫生间")) {

            return "bathroom";
        }

        if (value.contains("dining")
                ||
                value.contains("餐厅")
                ||
                value.contains("大厅")) {

            return "dining_room";
        }

        if (value.contains("study")
                ||
                value.contains("书房")) {

            return "study";
        }

        if (value.contains("hallway")
                ||
                value.contains("corridor")
                ||
                value.contains("走廊")) {

            return "hallway";
        }

        if (value.contains("balcony")
                ||
                value.contains("阳台")) {

            return "balcony";
        }

        return "other";
    }

    private static void require(
            boolean condition,
            String message) {

        if (!condition) {

            throw new IllegalArgumentException(
                    message
            );
        }
    }

    private record PolygonRoom(
            String name,
            List<WallTopologyNormalizer.Point>
                    points) {
    }

    private record Bounds(
            double minX,
            double maxX,
            double minY,
            double maxY) {

        double width() {

            return maxX - minX;
        }

        double height() {

            return maxY - minY;
        }
    }

    private record ScaleFactors(
            double x,
            double y) {
    }
}