package com.design3d.service;

import com.design3d.model.layout.RoomLayout;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Converts named room polygons (or a response containing raw_room_data) into walls. */
@Component
public class RoomPolygonLayoutAdapter {

    private final double scale;
    private final double wallHeight;
    private final double wallThickness;

    public RoomPolygonLayoutAdapter(
            @Value("${app.layout.teacher-format.mm-per-coordinate:10}") double scale,
            @Value("${app.layout.default-wall-height:2800}") double wallHeight,
            @Value("${app.layout.default-wall-thickness:240}") double wallThickness) {
        this.scale = scale;
        this.wallHeight = wallHeight;
        this.wallThickness = wallThickness;
    }

    public RoomLayout adapt(JsonNode source) {
        JsonNode roomNodes = source.isObject() ? source.get("raw_room_data") : source;
        if (roomNodes == null || !roomNodes.isArray() || roomNodes.isEmpty()) {
            throw new IllegalArgumentException("房间多边形 JSON 必须是房间数组，或包含 raw_room_data 数组");
        }

        List<PolygonRoom> polygons = new ArrayList<>();
        for (int i = 0; i < roomNodes.size(); i++) {
            JsonNode room = roomNodes.get(i);
            JsonNode coordinates = room.get("coordinates");
            if (!room.isObject() || !room.path("name").isTextual()
                    || coordinates == null || !coordinates.isArray() || coordinates.size() < 3) {
                throw new IllegalArgumentException("第 " + i + " 个房间必须包含 name 和至少 3 个 coordinates 坐标");
            }
            List<Point> points = new ArrayList<>();
            for (JsonNode coordinate : coordinates) points.add(point(coordinate, i));
            polygons.add(new PolygonRoom(room.get("name").asText(), points));
        }

        double minX = polygons.stream().flatMap(p -> p.points.stream()).mapToDouble(Point::x).min().orElseThrow();
        double maxX = polygons.stream().flatMap(p -> p.points.stream()).mapToDouble(Point::x).max().orElseThrow();
        double minY = polygons.stream().flatMap(p -> p.points.stream()).mapToDouble(Point::y).min().orElseThrow();
        double maxY = polygons.stream().flatMap(p -> p.points.stream()).mapToDouble(Point::y).max().orElseThrow();
        double margin = wallThickness / 2;
        double width = Math.max(1000, (maxX - minX) * scale + margin * 2);
        double depth = Math.max(1000, (maxY - minY) * scale + margin * 2);
        if (width > 50000 || depth > 50000) {
            throw new IllegalArgumentException("房间多边形换算后超过 50000 mm，请调小坐标换算比例");
        }

        Set<String> seenEdges = new HashSet<>();
        List<RoomLayout.Room> rooms = new ArrayList<>();
        int wallNumber = 1;
        for (int roomIndex = 0; roomIndex < polygons.size(); roomIndex++) {
            PolygonRoom polygon = polygons.get(roomIndex);
            List<RoomLayout.Wall> walls = new ArrayList<>();
            for (int i = 0; i < polygon.points.size(); i++) {
                Point start = polygon.points.get(i);
                Point end = polygon.points.get((i + 1) % polygon.points.size());
                if (start.distance(end) <= 0.1 || !seenEdges.add(edgeKey(start, end))) continue;
                walls.add(RoomLayout.Wall.builder()
                        .id("wall_" + wallNumber++)
                        .start(transform(start, minX, maxY, margin))
                        .end(transform(end, minX, maxY, margin))
                        .height(wallHeight)
                        .color("#D9D9D9")
                        .build());
            }
            double roomMinX = polygon.points.stream().mapToDouble(Point::x).min().orElseThrow();
            double roomMaxX = polygon.points.stream().mapToDouble(Point::x).max().orElseThrow();
            double roomMinY = polygon.points.stream().mapToDouble(Point::y).min().orElseThrow();
            double roomMaxY = polygon.points.stream().mapToDouble(Point::y).max().orElseThrow();
            rooms.add(RoomLayout.Room.builder()
                    .id("room_" + (roomIndex + 1))
                    .name(polygon.name)
                    .type(roomType(polygon.name))
                    .position(transform(new Point(roomMinX, roomMaxY), minX, maxY, margin))
                    .size(new RoomLayout.Size2D(Math.max(100, (roomMaxX - roomMinX) * scale),
                            Math.max(100, (roomMaxY - roomMinY) * scale)))
                    .walls(walls)
                    .build());
        }

        return RoomLayout.builder()
                .version("1.0").unit("mm")
                .layout(RoomLayout.LayoutInfo.builder().width(width).depth(depth)
                        .height(wallHeight).wallThickness(wallThickness).build())
                .rooms(rooms)
                .build();
    }

    private Point point(JsonNode node, int roomIndex) {
        if (!node.isArray() || node.size() != 2 || !node.get(0).isNumber() || !node.get(1).isNumber()) {
            throw new IllegalArgumentException("第 " + roomIndex + " 个房间包含无效坐标，应为 [x, y]");
        }
        return new Point(node.get(0).asDouble(), node.get(1).asDouble());
    }

    private RoomLayout.Position transform(Point point, double minX, double maxY, double margin) {
        return new RoomLayout.Position((point.x - minX) * scale + margin,
                (maxY - point.y) * scale + margin);
    }

    private String edgeKey(Point first, Point second) {
        String a = String.format(Locale.ROOT, "%.6f,%.6f", first.x, first.y);
        String b = String.format(Locale.ROOT, "%.6f,%.6f", second.x, second.y);
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private String roomType(String name) {
        String value = name.toLowerCase(Locale.ROOT);
        if (value.contains("living")) return "living_room";
        if (value.contains("bedroom")) return "bedroom";
        if (value.contains("kitchen")) return "kitchen";
        if (value.contains("bathroom")) return "bathroom";
        if (value.contains("dining")) return "dining_room";
        if (value.contains("study")) return "study";
        if (value.contains("hallway")) return "hallway";
        if (value.contains("balcony")) return "balcony";
        return "other";
    }

    private record Point(double x, double y) {
        double distance(Point other) { return Math.hypot(x - other.x, y - other.y); }
    }
    private record PolygonRoom(String name, List<Point> points) {}
}
