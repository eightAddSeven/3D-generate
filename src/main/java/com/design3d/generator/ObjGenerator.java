package com.design3d.generator;

import com.design3d.model.layout.RoomLayout;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pure Java Wavefront OBJ generator.
 *
 * <p>This is the server-side counterpart of the useful CAD stage in the
 * reference project: structured walls, openings, slabs and objects are kept as
 * named OBJ groups. It deliberately has no FreeCAD or local
 * filesystem dependency, so a JSON request can be converted in-process.</p>
 */
@Component
public class ObjGenerator {

    private static final double MM_TO_M = 0.001;
    private final StringBuilder obj = new StringBuilder(16_384);
    private int nextVertex = 1;

    public synchronized byte[] generate(RoomLayout layout) {
        obj.setLength(0);
        nextVertex = 1;
        obj.append("# 3DBuildingModelingEngine JSON to OBJ\n")
                .append("# Units: metres\n")
                .append("o room_layout\n");

        addBox("floor", layout.getLayout().getWidth() / 2, -25,
                layout.getLayout().getDepth() / 2, layout.getLayout().getWidth(),
                50, layout.getLayout().getDepth(), 0);

        double defaultHeight = layout.getLayout().getHeight();
        double thickness = layout.getLayout().getWallThickness();
        if (layout.getRooms() != null) {
            for (RoomLayout.Room room : layout.getRooms()) {
                if (room.getWalls() != null) {
                    for (RoomLayout.Wall wall : room.getWalls()) {
                        addWall(wall, thickness, defaultHeight);
                    }
                }
                if (room.getFurniture() != null) {
                    room.getFurniture().forEach(this::addFurniture);
                }
            }
        }
        if (layout.getFurniture() != null) {
            layout.getFurniture().forEach(this::addFurniture);
        }
        return obj.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void addWall(RoomLayout.Wall wall, double thickness, double defaultHeight) {
        double dx = wall.getEnd().getX() - wall.getStart().getX();
        double dz = wall.getEnd().getZ() - wall.getStart().getZ();
        double length = Math.hypot(dx, dz);
        if (length <= 0) return;
        double height = wall.getHeight() != null ? wall.getHeight() : defaultHeight;
        double angle = Math.atan2(dz, dx);

        List<OpeningRange> openings = new ArrayList<>();
        if (wall.getOpenings() != null) {
            for (RoomLayout.Opening opening : wall.getOpenings()) {
                double start = Math.max(0, opening.getPosition() - opening.getWidth() / 2);
                double end = Math.min(length, opening.getPosition() + opening.getWidth() / 2);
                double sill = "window".equals(opening.getType())
                        ? (opening.getSillHeight() != null ? opening.getSillHeight() : 900) : 0;
                double top = Math.min(height, sill + opening.getHeight());
                if (end > start && top > sill) openings.add(new OpeningRange(start, end, sill, top));
            }
        }
        openings.sort(Comparator.comparingDouble(OpeningRange::start));

        if (openings.isEmpty()) {
            addWallBox(wall.getId(), wall, 0, length, 0, height, thickness, angle);
            return;
        }

        // Split the wall into cells. Unlike the old marker approach this leaves
        // actual empty geometry for doors and windows.
        List<Double> cuts = new ArrayList<>(List.of(0d, length));
        for (OpeningRange opening : openings) {
            cuts.add(opening.start);
            cuts.add(opening.end);
        }
        cuts = cuts.stream().distinct().sorted().toList();
        int part = 0;
        for (int i = 0; i < cuts.size() - 1; i++) {
            double from = cuts.get(i), to = cuts.get(i + 1);
            if (to - from <= 0.01) continue;
            double mid = (from + to) / 2;
            List<OpeningRange> active = new ArrayList<>();
            for (OpeningRange opening : openings) {
                if (mid > opening.start && mid < opening.end) active.add(opening);
            }
            if (active.isEmpty()) {
                addWallBox(wall.getId() + "_part_" + part++, wall, from, to, 0, height, thickness, angle);
                continue;
            }
            List<Double> verticalCuts = new ArrayList<>(List.of(0d, height));
            for (OpeningRange opening : active) {
                verticalCuts.add(opening.bottom);
                verticalCuts.add(opening.top);
            }
            verticalCuts = verticalCuts.stream().distinct().sorted().toList();
            for (int j = 0; j < verticalCuts.size() - 1; j++) {
                double bottom = verticalCuts.get(j), top = verticalCuts.get(j + 1);
                double yMid = (bottom + top) / 2;
                boolean hole = false;
                for (OpeningRange opening : active) {
                    if (yMid > opening.bottom && yMid < opening.top) {
                        hole = true;
                        break;
                    }
                }
                if (!hole && top - bottom > 0.01) {
                    addWallBox(wall.getId() + "_part_" + part++, wall, from, to,
                            bottom, top, thickness, angle);
                }
            }
        }
    }

    private void addWallBox(String name, RoomLayout.Wall wall, double from, double to,
                            double bottom, double top, double thickness, double angle) {
        double ux = Math.cos(angle), uz = Math.sin(angle);
        double distance = (from + to) / 2;
        double cx = wall.getStart().getX() + ux * distance;
        double cz = wall.getStart().getZ() + uz * distance;
        addBox(name, cx, (bottom + top) / 2, cz, to - from, top - bottom, thickness, angle);
    }

    private void addFurniture(RoomLayout.Furniture furniture) {
        if (furniture.getPosition() == null || furniture.getSize() == null) return;
        addBox(furniture.getId(), furniture.getPosition().getX(), furniture.getSize().getHeight() / 2,
                furniture.getPosition().getZ(), furniture.getSize().getWidth(),
                furniture.getSize().getHeight(), furniture.getSize().getDepth(),
                Math.toRadians(furniture.getRotation()));
    }

    private void addBox(String name, double cx, double cy, double cz,
                        double sx, double sy, double sz, double angle) {
        obj.append("g ").append(safeName(name)).append('\n');
        double hx = sx / 2, hy = sy / 2, hz = sz / 2;
        double[][] local = {
                {-hx, -hy, -hz}, {hx, -hy, -hz}, {hx, -hy, hz}, {-hx, -hy, hz},
                {-hx, hy, -hz}, {hx, hy, -hz}, {hx, hy, hz}, {-hx, hy, hz}
        };
        double cos = Math.cos(angle), sin = Math.sin(angle);
        for (double[] p : local) {
            double x = cx + p[0] * cos - p[2] * sin;
            double z = cz + p[0] * sin + p[2] * cos;
            obj.append(String.format(Locale.US, "v %.6f %.6f %.6f%n",
                    x * MM_TO_M, (cy + p[1]) * MM_TO_M, z * MM_TO_M));
        }
        int v = nextVertex;
        face(v, v + 3, v + 2, v + 1); // bottom
        face(v + 4, v + 5, v + 6, v + 7); // top
        face(v, v + 1, v + 5, v + 4);
        face(v + 1, v + 2, v + 6, v + 5);
        face(v + 2, v + 3, v + 7, v + 6);
        face(v + 3, v, v + 4, v + 7);
        nextVertex += 8;
    }

    private void face(int a, int b, int c, int d) {
        obj.append("f ").append(a).append(' ').append(b).append(' ')
                .append(c).append(' ').append(d).append('\n');
    }

    private String safeName(String value) {
        if (value == null || value.isBlank()) return "object";
        return value.replaceAll("[^\\p{L}\\p{N}_.-]+", "_");
    }

    private record OpeningRange(double start, double end, double bottom, double top) {}
}
