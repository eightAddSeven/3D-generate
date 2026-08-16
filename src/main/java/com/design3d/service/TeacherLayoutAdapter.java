package com.design3d.service;

import com.design3d.model.layout.RoomLayout;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Converts the teacher's compact recognition result into the internal mm-based layout model. */
@Component
public class TeacherLayoutAdapter {

    static final int DOOR = 1;
    static final int WALL = 2;
    static final int WINDOW = 3;

    private final double millimetresPerCoordinate;
    private final double wallHeight;

    public TeacherLayoutAdapter(
            @Value("${app.layout.teacher-format.mm-per-coordinate:10}") double millimetresPerCoordinate,
            @Value("${app.layout.default-wall-height:2800}") double wallHeight) {
        if (millimetresPerCoordinate <= 0) {
            throw new IllegalArgumentException("老师 JSON 坐标换算比例必须大于 0");
        }
        this.millimetresPerCoordinate = millimetresPerCoordinate;
        this.wallHeight = wallHeight;
    }

    public RoomLayout adapt(JsonNode root) {
        if (!root.isArray() || root.isEmpty()) {
            throw new IllegalArgumentException("老师 JSON 的根节点必须是非空数组");
        }

        List<LineFeature> walls = new ArrayList<>();
        List<LineFeature> windows = new ArrayList<>();
        List<DoorFeature> doors = new ArrayList<>();
        List<Double> detectedThicknesses = new ArrayList<>();

        for (int i = 0; i < root.size(); i++) {
            JsonNode item = root.get(i);
            require(item.isArray() && item.size() == 2 && item.get(1).canConvertToInt(),
                    "第 " + i + " 项应为 [几何数据, 类型编号]");
            JsonNode geometry = item.get(0);
            int type = item.get(1).asInt();
            if (type == WALL || type == WINDOW) {
                require(geometry.isArray() && geometry.size() == 3,
                        "第 " + i + " 项的墙/窗几何应为 [起点, 终点, 宽度]");
                Point start = point(geometry.get(0), i);
                Point end = point(geometry.get(1), i);
                double width = positiveNumber(geometry.get(2), i, "宽度");
                require(start.distance(end) > 0.1, "第 " + i + " 项的线段长度必须大于 0");
                LineFeature feature = new LineFeature(start, end, width);
                if (type == WALL) {
                    walls.add(feature);
                    detectedThicknesses.add(width);
                } else {
                    windows.add(feature);
                }
            } else if (type == DOOR) {
                require(geometry.isArray() && geometry.size() == 3,
                        "第 " + i + " 项的门几何应为 [基点, 方向向量1, 方向向量2]");
                Point origin = point(geometry.get(0), i);
                Point vector1 = point(geometry.get(1), i);
                Point vector2 = point(geometry.get(2), i);
                require(vector1.length() > 0.1 && vector2.length() > 0.1,
                        "第 " + i + " 项的门方向向量长度必须大于 0");
                doors.add(new DoorFeature(origin, vector1, vector2));
            } else {
                throw new IllegalArgumentException("第 " + i + " 项包含不支持的类型编号: " + type
                        + "（当前支持 1=门、2=墙、3=窗）");
            }
        }
        require(!walls.isEmpty(), "老师 JSON 中至少需要一条类型 2 的墙线");

        Bounds bounds = boundsOf(walls, windows, doors);
        double thickness = clamp(median(detectedThicknesses) * millimetresPerCoordinate, 100, 500);
        double margin = thickness / 2;
        CoordinateTransform transform = new CoordinateTransform(
                bounds.minX, bounds.maxY, millimetresPerCoordinate, margin);

        List<RoomLayout.Wall> modelWalls = new ArrayList<>();
        for (int i = 0; i < walls.size(); i++) {
            LineFeature source = walls.get(i);
            modelWalls.add(RoomLayout.Wall.builder()
                    .id("wall_" + (i + 1))
                    .start(transform.apply(source.start))
                    .end(transform.apply(source.end))
                    .height(wallHeight)
                    .color("#D9D9D9")
                    .build());
        }

        // A type-3 window either overlaps a detected wall or fills a gap between two wall segments.
        for (int windowIndex = 0; windowIndex < windows.size(); windowIndex++) {
            LineFeature window = windows.get(windowIndex);
            int wallIndex = closestParallelWall(window, walls);
            LineFeature wallSource = walls.get(wallIndex);
            double sourceCentre = projectedDistance(wallSource.start, wallSource.end, window.midpoint());
            double perpendicularDistance = pointToInfiniteLineDistance(
                    window.midpoint(), wallSource.start, wallSource.end);
            boolean overlapsDetectedWall = sourceCentre >= 0 && sourceCentre <= wallSource.length()
                    && perpendicularDistance <= Math.max(5, wallSource.width * 2);

            RoomLayout.Wall wall;
            double centre;
            double wallLength = wallSource.length() * millimetresPerCoordinate;
            if (overlapsDetectedWall) {
                wall = modelWalls.get(wallIndex);
                centre = sourceCentre * millimetresPerCoordinate;
            } else {
                wallLength = window.length() * millimetresPerCoordinate;
                centre = wallLength / 2;
                wall = RoomLayout.Wall.builder()
                        .id("window_wall_" + (windowIndex + 1))
                        .start(transform.apply(window.start))
                        .end(transform.apply(window.end))
                        .height(wallHeight)
                        .color("#D9D9D9")
                        .build();
                modelWalls.add(wall);
            }
            double openingWidth = clamp(window.length() * millimetresPerCoordinate,
                    400, Math.min(5000, wallLength));
            wall.getOpenings().add(RoomLayout.Opening.builder()
                    .type("window")
                    .position(clamp(centre, openingWidth / 2, wallLength - openingWidth / 2))
                    .width(openingWidth)
                    .height(1500)
                    .sillHeight(900d)
                    .build());
        }

        // Door positions are gaps between detected wall segments. Add only their lintel wall and cut the door out.
        for (int i = 0; i < doors.size(); i++) {
            LineFeature doorLine = chooseDoorLine(doors.get(i), walls);
            // Recognition points can be a few pixels short; keep the generated opening within protocol limits.
            double doorWidth = clamp(doorLine.length() * millimetresPerCoordinate, 400, 5000);
            RoomLayout.Wall doorWall = RoomLayout.Wall.builder()
                    .id("door_lintel_" + (i + 1))
                    .start(transform.apply(doorLine.start))
                    .end(transform.apply(doorLine.end))
                    .height(wallHeight)
                    .color("#D9D9D9")
                    .build();
            doorWall.getOpenings().add(RoomLayout.Opening.builder()
                    .type("door")
                    .position(doorWidth / 2)
                    .width(doorWidth)
                    .height(Math.min(2100, wallHeight))
                    .sillHeight(0d)
                    .build());
            modelWalls.add(doorWall);
        }

        double layoutWidth = Math.max(1000, bounds.width() * millimetresPerCoordinate + margin * 2);
        double layoutDepth = Math.max(1000, bounds.height() * millimetresPerCoordinate + margin * 2);
        require(layoutWidth <= 50000 && layoutDepth <= 50000,
                "换算后的图纸尺寸超过 50000 mm，请调小 app.layout.teacher-format.mm-per-coordinate");

        RoomLayout.Room room = RoomLayout.Room.builder()
                .id("recognized_plan")
                .name("识别图纸")
                .type("other")
                .position(new RoomLayout.Position(0, 0))
                .size(new RoomLayout.Size2D(layoutWidth, layoutDepth))
                .walls(modelWalls)
                .build();
        return RoomLayout.builder()
                .version("1.0")
                .unit("mm")
                .layout(RoomLayout.LayoutInfo.builder()
                        .width(layoutWidth)
                        .depth(layoutDepth)
                        .height(wallHeight)
                        .wallThickness(thickness)
                        .build())
                .rooms(new ArrayList<>(List.of(room)))
                .build();
    }

    private Point point(JsonNode node, int itemIndex) {
        require(node.isArray() && node.size() == 2 && node.get(0).isNumber() && node.get(1).isNumber(),
                "第 " + itemIndex + " 项包含无效坐标，坐标应为 [x, y]");
        return new Point(node.get(0).asDouble(), node.get(1).asDouble());
    }

    private double positiveNumber(JsonNode node, int itemIndex, String name) {
        require(node.isNumber() && node.asDouble() > 0,
                "第 " + itemIndex + " 项的" + name + "必须是正数");
        return node.asDouble();
    }

    private Bounds boundsOf(List<LineFeature> walls, List<LineFeature> windows, List<DoorFeature> doors) {
        List<Point> points = new ArrayList<>();
        for (LineFeature line : walls) { points.add(line.start); points.add(line.end); }
        for (LineFeature line : windows) { points.add(line.start); points.add(line.end); }
        for (DoorFeature door : doors) {
            points.add(door.origin);
            points.add(door.origin.add(door.vector1));
            points.add(door.origin.add(door.vector2));
        }
        double minX = points.stream().mapToDouble(Point::x).min().orElseThrow();
        double maxX = points.stream().mapToDouble(Point::x).max().orElseThrow();
        double minY = points.stream().mapToDouble(Point::y).min().orElseThrow();
        double maxY = points.stream().mapToDouble(Point::y).max().orElseThrow();
        return new Bounds(minX, maxX, minY, maxY);
    }

    private int closestParallelWall(LineFeature target, List<LineFeature> walls) {
        int bestIndex = 0;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < walls.size(); i++) {
            LineFeature wall = walls.get(i);
            double score = angleDifference(target, wall) * 1000
                    + pointToSegmentDistance(target.midpoint(), wall.start, wall.end);
            if (score < bestScore) { bestScore = score; bestIndex = i; }
        }
        return bestIndex;
    }

    private LineFeature chooseDoorLine(DoorFeature door, List<LineFeature> walls) {
        LineFeature first = new LineFeature(door.origin, door.origin.add(door.vector1), 0);
        LineFeature second = new LineFeature(door.origin, door.origin.add(door.vector2), 0);
        double firstScore = doorLineScore(first, walls);
        double secondScore = doorLineScore(second, walls);
        return firstScore <= secondScore ? first : second;
    }

    private double doorLineScore(LineFeature candidate, List<LineFeature> walls) {
        return walls.stream().mapToDouble(wall -> angleDifference(candidate, wall) * 1000
                        + pointToSegmentDistance(candidate.midpoint(), wall.start, wall.end))
                .min().orElse(Double.MAX_VALUE);
    }

    private double angleDifference(LineFeature a, LineFeature b) {
        double dot = Math.abs(a.dx() * b.dx() + a.dy() * b.dy());
        double cosine = clamp(dot / (a.length() * b.length()), 0, 1);
        return Math.acos(cosine);
    }

    private double pointToInfiniteLineDistance(Point point, Point start, Point end) {
        double length = start.distance(end);
        return Math.abs((end.x - start.x) * (start.y - point.y)
                - (start.x - point.x) * (end.y - start.y)) / length;
    }

    private double pointToSegmentDistance(Point point, Point start, Point end) {
        double length = start.distance(end);
        double projection = projectedDistance(start, end, point);
        if (projection <= 0) return point.distance(start);
        if (projection >= length) return point.distance(end);
        return pointToInfiniteLineDistance(point, start, end);
    }

    private double projectedDistance(Point start, Point end, Point point) {
        double dx = end.x - start.x, dy = end.y - start.y;
        return ((point.x - start.x) * dx + (point.y - start.y) * dy) / Math.hypot(dx, dy);
    }

    private double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static double clamp(double value, double min, double max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record Point(double x, double y) {
        Point add(Point other) { return new Point(x + other.x, y + other.y); }
        double length() { return Math.hypot(x, y); }
        double distance(Point other) { return Math.hypot(x - other.x, y - other.y); }
    }

    private record LineFeature(Point start, Point end, double width) {
        double dx() { return end.x - start.x; }
        double dy() { return end.y - start.y; }
        double length() { return start.distance(end); }
        Point midpoint() { return new Point((start.x + end.x) / 2, (start.y + end.y) / 2); }
    }

    private record DoorFeature(Point origin, Point vector1, Point vector2) {}
    private record Bounds(double minX, double maxX, double minY, double maxY) {
        double width() { return maxX - minX; }
        double height() { return maxY - minY; }
    }

    private record CoordinateTransform(double minX, double maxY, double scale, double margin) {
        RoomLayout.Position apply(Point point) {
            return new RoomLayout.Position((point.x - minX) * scale + margin,
                    (maxY - point.y) * scale + margin);
        }
    }
}
