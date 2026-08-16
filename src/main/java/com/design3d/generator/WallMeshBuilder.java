package com.design3d.generator;

import com.design3d.model.layout.RoomLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 墙体几何体构建器
 */
public class WallMeshBuilder {

    /**
     * 构建墙体长方体
     *
     * @param wall      墙体数据
     * @param thickness 墙体厚度 (mm)
     * @param materialIndex 材质索引
     * @return 墙体 MeshData
     */
    public static MeshData build(RoomLayout.Wall wall, double thickness, int materialIndex) {
        MeshData mesh = new MeshData();
        mesh.setName(wall.getId() != null ? wall.getId() : "wall");
        mesh.setMaterialIndex(materialIndex);

        double height = wall.getHeight() != null ? wall.getHeight() : 2800;

        double x1 = wall.getStart().getX();
        double z1 = wall.getStart().getZ();
        double x2 = wall.getEnd().getX();
        double z2 = wall.getEnd().getZ();

        double length = Math.hypot(x2 - x1, z2 - z1);
        if (length <= 0.01) return mesh;
        double angle = Math.atan2(z2 - z1, x2 - x1);

        List<OpeningRange> openings = openingRanges(wall, length, height);
        List<Double> horizontalCuts = new ArrayList<>(List.of(0d, length));
        for (OpeningRange opening : openings) {
            horizontalCuts.add(opening.start);
            horizontalCuts.add(opening.end);
        }
        horizontalCuts = horizontalCuts.stream().distinct().sorted().toList();
        for (int i = 0; i < horizontalCuts.size() - 1; i++) {
            double from = horizontalCuts.get(i), to = horizontalCuts.get(i + 1);
            if (to - from <= 0.01) continue;
            double horizontalMid = (from + to) / 2;
            List<OpeningRange> active = openings.stream()
                    .filter(op -> horizontalMid > op.start && horizontalMid < op.end).toList();
            List<Double> verticalCuts = new ArrayList<>(List.of(0d, height));
            for (OpeningRange opening : active) {
                verticalCuts.add(opening.bottom);
                verticalCuts.add(opening.top);
            }
            verticalCuts = verticalCuts.stream().distinct().sorted().toList();
            for (int j = 0; j < verticalCuts.size() - 1; j++) {
                double bottom = verticalCuts.get(j), top = verticalCuts.get(j + 1);
                double verticalMid = (bottom + top) / 2;
                boolean isHole = active.stream()
                        .anyMatch(op -> verticalMid > op.bottom && verticalMid < op.top);
                if (!isHole && top - bottom > 0.01) {
                    addWallBox(mesh, wall, from, to, bottom, top, thickness, angle);
                }
            }
        }
        return mesh;
    }

    private static List<OpeningRange> openingRanges(RoomLayout.Wall wall, double length, double height) {
        List<OpeningRange> ranges = new ArrayList<>();
        if (wall.getOpenings() == null) return ranges;
        for (RoomLayout.Opening opening : wall.getOpenings()) {
            double start = Math.max(0, opening.getPosition() - opening.getWidth() / 2);
            double end = Math.min(length, opening.getPosition() + opening.getWidth() / 2);
            double bottom = "window".equals(opening.getType())
                    ? (opening.getSillHeight() != null ? opening.getSillHeight() : 900) : 0;
            double top = Math.min(height, bottom + opening.getHeight());
            if (end > start && top > bottom) ranges.add(new OpeningRange(start, end, bottom, top));
        }
        ranges.sort(Comparator.comparingDouble(OpeningRange::start));
        return ranges;
    }

    private static void addWallBox(MeshData mesh, RoomLayout.Wall wall, double from, double to,
                                   double bottom, double top, double thickness, double angle) {
        double distance = (from + to) / 2;
        double cx = wall.getStart().getX() + Math.cos(angle) * distance;
        double cz = wall.getStart().getZ() + Math.sin(angle) * distance;
        mesh.addOrientedBox((float) (cx / 1000), (float) ((bottom + top) / 2000),
                (float) (cz / 1000), (float) ((to - from) / 1000),
                (float) ((top - bottom) / 1000), (float) (thickness / 1000), (float) angle);
    }

    private record OpeningRange(double start, double end, double bottom, double top) {}

}
