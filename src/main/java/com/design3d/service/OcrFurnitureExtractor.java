package com.design3d.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts furniture semantics and dimensions from OCR records in mixed recognition JSON. */
@Component
public class OcrFurnitureExtractor {

    private static final Pattern THREE_DIMENSIONS = Pattern.compile(
            "(?<!\\d)(\\d{2,5}(?:\\.\\d+)?)\\s*[xX×*]\\s*(\\d{2,5}(?:\\.\\d+)?)\\s*[xX×*]\\s*(\\d{2,5}(?:\\.\\d+)?)(?!\\d)");
    private static final Pattern TABLE_DIAMETER_AND_SEATS = Pattern.compile(
            "(?<!\\d)(\\d{3,4})\\s*[xX×*]\\s*(\\d{1,2})(?!\\d)");
    private static final double MAX_DIMENSION_PAIR_DISTANCE = 180;

    public List<DetectedFurniture> extract(JsonNode root) {
        List<OcrEntry> entries = readOcrEntries(root);
        List<DimensionCandidate> dimensions = new ArrayList<>();
        for (OcrEntry entry : entries) {
            Size size = parseThreeDimensions(entry.text);
            if (size != null) dimensions.add(new DimensionCandidate(entry.index, entry.center, size, false));
            Matcher tableMatcher = TABLE_DIAMETER_AND_SEATS.matcher(entry.text);
            if (tableMatcher.find()) {
                double diameter = Double.parseDouble(tableMatcher.group(1));
                dimensions.add(new DimensionCandidate(entry.index, entry.center,
                        new Size(diameter, diameter, 750), true));
            }
        }

        Set<Integer> usedDimensionEntries = new HashSet<>();
        List<DetectedFurniture> result = new ArrayList<>();
        for (OcrEntry entry : entries) {
            FurnitureKind kind = classify(entry.text);
            if (kind == null) continue;

            Size size = parseThreeDimensions(entry.text);
            if (size == null) {
                DimensionCandidate candidate = closestDimension(entry, kind, dimensions, usedDimensionEntries);
                if (candidate != null) {
                    size = candidate.size;
                    usedDimensionEntries.add(candidate.entryIndex);
                }
            }
            if (size == null) size = kind.defaultSize;

            result.add(new DetectedFurniture(entry.text.strip(), kind.type, kind.category,
                    entry.center.x, entry.center.y, entry.rotationDegrees,
                    size.width, size.depth, size.height, kind.material, kind.color));
        }
        return result;
    }

    private List<OcrEntry> readOcrEntries(JsonNode root) {
        List<OcrEntry> entries = new ArrayList<>();
        if (!root.isArray()) return entries;
        for (int i = 0; i < root.size(); i++) {
            JsonNode item = root.get(i);
            if (!item.isArray() || item.size() != 2 || !item.get(1).isTextual()) continue;
            JsonNode quad = item.get(0);
            if (!quad.isArray() || quad.size() < 4) continue;
            List<Point> points = new ArrayList<>();
            boolean valid = true;
            for (JsonNode node : quad) {
                if (!isPoint(node)) { valid = false; break; }
                points.add(new Point(node.get(0).asDouble(), node.get(1).asDouble()));
            }
            if (!valid) continue;
            double centerX = points.stream().mapToDouble(Point::x).average().orElse(0);
            double centerY = points.stream().mapToDouble(Point::y).average().orElse(0);
            Point first = points.get(0), second = points.get(1);
            double rotation = -Math.toDegrees(Math.atan2(second.y - first.y, second.x - first.x));
            entries.add(new OcrEntry(i, item.get(1).asText(), new Point(centerX, centerY), rotation));
        }
        return entries;
    }

    private DimensionCandidate closestDimension(OcrEntry furniture, FurnitureKind kind,
                                                List<DimensionCandidate> dimensions,
                                                Set<Integer> usedEntries) {
        DimensionCandidate best = null;
        double bestDistance = Double.MAX_VALUE;
        for (DimensionCandidate candidate : dimensions) {
            if (usedEntries.contains(candidate.entryIndex)) continue;
            if (candidate.tableOnly && !"dining_table".equals(kind.type)) continue;
            double distance = furniture.center.distance(candidate.center);
            if (distance <= MAX_DIMENSION_PAIR_DISTANCE && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Size parseThreeDimensions(String text) {
        Matcher matcher = THREE_DIMENSIONS.matcher(text);
        if (!matcher.find()) return null;
        double width = Double.parseDouble(matcher.group(1));
        double depth = Double.parseDouble(matcher.group(2));
        double height = Double.parseDouble(matcher.group(3));
        if (!reasonable(width) || !reasonable(depth) || !reasonable(height)) return null;
        return new Size(width, depth, height);
    }

    private boolean reasonable(double value) {
        return value >= 100 && value <= 10000;
    }

    private FurnitureKind classify(String original) {
        String text = original.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (THREE_DIMENSIONS.matcher(text).matches()) return null;
        if (text.contains("水池") || text.contains("水槽") || text.contains("浸泡池")) return FurnitureKind.SINK;
        if (text.contains("烤箱")) return FurnitureKind.OVEN;
        if (text.contains("冰箱") || text.contains("雪柜") || text.contains("雪相") || text.contains("冷藏")) return FurnitureKind.REFRIGERATOR;
        if (text.contains("货架") || text.contains("层架")) return FurnitureKind.SHELF;
        if (text.contains("暖碟")) return FurnitureKind.WARMER;
        if (text.contains("洗碗机")) return FurnitureKind.DISHWASHER;
        if (text.contains("汤炉") || text.contains("炒炉") || text.contains("炒灶")
                || text.contains("灶") || text.contains("炉")) return FurnitureKind.STOVE;
        if (text.contains("操作台") || text.contains("工作台") || text.contains("吧台")) return FurnitureKind.COUNTER;
        if (text.matches(".*vip[-—_]?\\d+.*") || text.contains("餐桌") || text.contains("桌子")
                || text.equals("桌") || text.contains("(桌")) return FurnitureKind.DINING_TABLE;
        if (text.contains("柜")) return FurnitureKind.CABINET;
        return null;
    }

    private boolean isPoint(JsonNode node) {
        return node.isArray() && node.size() == 2 && node.get(0).isNumber() && node.get(1).isNumber();
    }

    public record DetectedFurniture(String sourceLabel, String type, String category,
                                    double sourceX, double sourceY, double rotation,
                                    double width, double depth, double height,
                                    String material, String color) {}

    private record OcrEntry(int index, String text, Point center, double rotationDegrees) {}
    private record DimensionCandidate(int entryIndex, Point center, Size size, boolean tableOnly) {}
    private record Size(double width, double depth, double height) {}
    private record Point(double x, double y) {
        double distance(Point other) { return Math.hypot(x - other.x, y - other.y); }
    }

    private enum FurnitureKind {
        SINK("sink", "appliance", new Size(1200, 750, 850), "stainless_steel", "#B0BEC5"),
        OVEN("oven", "appliance", new Size(900, 800, 1600), "dark_metal", "#555555"),
        REFRIGERATOR("refrigerator", "appliance", new Size(1200, 800, 1800), "metal", "#D9E2E8"),
        SHELF("shelf", "storage", new Size(1200, 500, 1800), "metal", "#9E9E9E"),
        WARMER("warmer", "appliance", new Size(1200, 700, 850), "stainless_steel", "#B0BEC5"),
        DISHWASHER("dishwasher", "appliance", new Size(700, 700, 850), "stainless_steel", "#B0BEC5"),
        STOVE("stove", "appliance", new Size(1200, 800, 850), "dark_metal", "#555555"),
        COUNTER("counter", "table", new Size(1500, 700, 850), "stainless_steel", "#B0BEC5"),
        DINING_TABLE("dining_table", "table", new Size(1400, 800, 750), "wood", "#8D6E63"),
        CABINET("cabinet", "storage", new Size(1200, 600, 1800), "metal", "#9E9E9E");

        final String type;
        final String category;
        final Size defaultSize;
        final String material;
        final String color;

        FurnitureKind(String type, String category, Size defaultSize, String material, String color) {
            this.type = type; this.category = category; this.defaultSize = defaultSize;
            this.material = material; this.color = color;
        }
    }
}
