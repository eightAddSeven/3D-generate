package com.design3d.service;

import com.design3d.model.layout.RoomLayout;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;

/** Adapts the newer array format where geometry detections and OCR boxes share one list. */
@Component
public class MixedRecognitionLayoutAdapter {

    private final TeacherLayoutAdapter teacherLayoutAdapter;
    private final OcrFurnitureExtractor furnitureExtractor;
    private final double millimetresPerCoordinate;

    public MixedRecognitionLayoutAdapter(TeacherLayoutAdapter teacherLayoutAdapter,
                                         OcrFurnitureExtractor furnitureExtractor,
                                         @Value("${app.layout.teacher-format.mm-per-coordinate:10}")
                                         double millimetresPerCoordinate) {
        this.teacherLayoutAdapter = teacherLayoutAdapter;
        this.furnitureExtractor = furnitureExtractor;
        this.millimetresPerCoordinate = millimetresPerCoordinate;
    }

    public RoomLayout adapt(JsonNode root) {
        if (!root.isArray() || root.isEmpty()) {
            throw new IllegalArgumentException("混合识别 JSON 的根节点必须是非空数组");
        }
        ArrayNode normalized = JsonNodeFactory.instance.arrayNode();
        int ignoredOcr = 0;
        for (int i = 0; i < root.size(); i++) {
            JsonNode item = root.get(i);
            if (isLine(item)) {
                double width = item.get(2).asDouble();
                double length = distance(item.get(0), item.get(1));
                // Zero-width and extremely short detections are drawing/OCR noise, not wall solids.
                if (width > 0 && length > 5) {
                    ArrayNode geometry = JsonNodeFactory.instance.arrayNode()
                            .add(item.get(0)).add(item.get(1)).add(width);
                    normalized.add(JsonNodeFactory.instance.arrayNode().add(geometry).add(TeacherLayoutAdapter.WALL));
                }
            } else if (isDoor(item)) {
                JsonNode origin = doorPoint(item, 0);
                ArrayNode vector1 = vector(origin, doorPoint(item, 1));
                ArrayNode vector2 = vector(origin, doorPoint(item, 2));
                ArrayNode geometry = JsonNodeFactory.instance.arrayNode()
                        .add(origin).add(vector1).add(vector2);
                normalized.add(JsonNodeFactory.instance.arrayNode().add(geometry).add(TeacherLayoutAdapter.DOOR));
            } else if (isOcr(item)) {
                ignoredOcr++;
            } else {
                throw new IllegalArgumentException("混合识别 JSON 第 " + i + " 项结构无效");
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("混合识别 JSON 中没有可建模的墙或门");
        }
        RoomLayout layout = teacherLayoutAdapter.adapt(normalized);
        List<OcrFurnitureExtractor.DetectedFurniture> detected = furnitureExtractor.extract(root);
        addFurniture(layout, detected, geometryBounds(root));
        if (!detected.isEmpty()) {
            layout.getRooms().get(0).setName("识别图纸（已提取 " + detected.size() + " 件家具设备）");
        } else if (ignoredOcr > 0) {
            layout.getRooms().get(0).setName("识别图纸（未识别到可建模家具）");
        }
        return layout;
    }

    private void addFurniture(RoomLayout layout,
                              List<OcrFurnitureExtractor.DetectedFurniture> detected,
                              Bounds bounds) {
        RoomLayout.Room room = layout.getRooms().get(0);
        double margin = layout.getLayout().getWallThickness() / 2;
        int number = 1;
        for (OcrFurnitureExtractor.DetectedFurniture item : detected) {
            double x = (item.sourceX() - bounds.minX) * millimetresPerCoordinate + margin;
            double z = (bounds.maxY - item.sourceY()) * millimetresPerCoordinate + margin;
            x = clampCenter(x, item.width(), layout.getLayout().getWidth());
            z = clampCenter(z, item.depth(), layout.getLayout().getDepth());
            room.getFurniture().add(RoomLayout.Furniture.builder()
                    .id("ocr_" + item.type() + "_" + number++)
                    .type(item.type())
                    .category(item.category())
                    .position(new RoomLayout.Position(x, z))
                    .rotation(item.rotation())
                    .size(new RoomLayout.Size3D(item.width(), item.depth(), item.height()))
                    .material(item.material())
                    .color(item.color())
                    .roomId(room.getId())
                    .build());
        }
    }

    private Bounds geometryBounds(JsonNode root) {
        List<double[]> points = new ArrayList<>();
        for (JsonNode item : root) {
            if (isLine(item) && item.get(2).asDouble() > 0 && distance(item.get(0), item.get(1)) > 5) {
                points.add(point(item.get(0))); points.add(point(item.get(1)));
            } else if (isDoor(item)) {
                points.add(point(doorPoint(item, 0)));
                points.add(point(doorPoint(item, 1)));
                points.add(point(doorPoint(item, 2)));
            }
        }
        double minX = points.stream().mapToDouble(p -> p[0]).min().orElse(0);
        double maxY = points.stream().mapToDouble(p -> p[1]).max().orElse(0);
        return new Bounds(minX, maxY);
    }

    private double[] point(JsonNode node) {
        return new double[]{node.get(0).asDouble(), node.get(1).asDouble()};
    }

    private double clampCenter(double value, double objectSize, double layoutSize) {
        double half = Math.min(objectSize, layoutSize) / 2;
        return Math.max(half, Math.min(layoutSize - half, value));
    }

    private record Bounds(double minX, double maxY) {}

    private boolean isLine(JsonNode item) {
        return item.isArray() && item.size() == 4 && isPoint(item.get(0)) && isPoint(item.get(1))
                && item.get(2).isNumber() && item.get(3).canConvertToInt()
                && (item.get(3).asInt() == 2 || item.get(3).asInt() == 3);
    }

    private boolean isDoor(JsonNode item) {
        boolean nested = item.isArray() && item.size() == 2 && item.get(1).canConvertToInt()
                && item.get(1).asInt() == 1 && item.get(0).isArray() && item.get(0).size() == 3
                && isPoint(item.get(0).get(0)) && isPoint(item.get(0).get(1)) && isPoint(item.get(0).get(2));
        boolean flat = item.isArray() && item.size() == 4 && item.get(3).canConvertToInt()
                && item.get(3).asInt() == 1 && isPoint(item.get(0)) && isPoint(item.get(1))
                && isPoint(item.get(2));
        return nested || flat;
    }

    private JsonNode doorPoint(JsonNode item, int pointIndex) {
        return item.size() == 2 ? item.get(0).get(pointIndex) : item.get(pointIndex);
    }

    private boolean isOcr(JsonNode item) {
        return item.isArray() && item.size() == 2 && item.get(1).isTextual();
    }

    private boolean isPoint(JsonNode node) {
        return node.isArray() && node.size() == 2 && node.get(0).isNumber() && node.get(1).isNumber();
    }

    private double distance(JsonNode first, JsonNode second) {
        return Math.hypot(first.get(0).asDouble() - second.get(0).asDouble(),
                first.get(1).asDouble() - second.get(1).asDouble());
    }

    private ArrayNode vector(JsonNode origin, JsonNode endpoint) {
        return JsonNodeFactory.instance.arrayNode()
                .add(endpoint.get(0).asDouble() - origin.get(0).asDouble())
                .add(endpoint.get(1).asDouble() - origin.get(1).asDouble());
    }
}
