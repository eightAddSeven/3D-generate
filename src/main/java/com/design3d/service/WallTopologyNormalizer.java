package com.design3d.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 房间多边形墙线拓扑规范化器。
 *
 * 主要完成：
 * 1. 相近坐标吸附；
 * 2. 近水平/垂直墙正交化；
 * 3. 共线、部分重叠墙去重合并；
 * 4. T 型、十字型连接位置统一；
 * 5. 斜墙近似去重。
 *
 * 注意：
 * 本类只处理原始图像坐标，不负责转换成 mm。
 */
public class WallTopologyNormalizer {

    private static final double NUMERIC_EPSILON = 1e-9;

    private final double snapEpsilon;
    private final double orthogonalAngleDegrees;
    private final double minSegmentLength;

    public WallTopologyNormalizer(double snapEpsilon,
                                  double orthogonalAngleDegrees,
                                  double minSegmentLength) {

        if (snapEpsilon <= 0) {
            throw new IllegalArgumentException("snapEpsilon 必须大于 0");
        }

        if (orthogonalAngleDegrees < 0 || orthogonalAngleDegrees > 15) {
            throw new IllegalArgumentException(
                    "orthogonalAngleDegrees 应位于 0~15 度之间");
        }

        if (minSegmentLength <= 0) {
            throw new IllegalArgumentException(
                    "minSegmentLength 必须大于 0");
        }

        this.snapEpsilon = snapEpsilon;
        this.orthogonalAngleDegrees = orthogonalAngleDegrees;
        this.minSegmentLength = minSegmentLength;
    }

    public List<WallSegment> normalize(List<RawSegment> rawSegments) {

        if (rawSegments == null || rawSegments.isEmpty()) {
            return List.of();
        }

        /*
         * 第 1 步：
         * 去掉明显无效的短线。
         */
        List<RawSegment> valid = rawSegments.stream()
                .filter(segment ->
                        segment != null
                                && segment.start() != null
                                && segment.end() != null
                                && segment.start()
                                .distance(segment.end())
                                >= minSegmentLength)
                .toList();

        if (valid.isEmpty()) {
            return List.of();
        }

        /*
         * 第 2 步：
         * 对所有 X / Y 坐标分别聚类。
         *
         * 例如：
         * 300
         * 301
         * 299
         *
         * 会统一吸附到一个中心值附近。
         */
        List<Double> xAnchors = clusterCenters(
                valid.stream()
                        .flatMap(segment ->
                                List.of(
                                        segment.start().x(),
                                        segment.end().x()
                                ).stream())
                        .toList()
        );

        List<Double> yAnchors = clusterCenters(
                valid.stream()
                        .flatMap(segment ->
                                List.of(
                                        segment.start().y(),
                                        segment.end().y()
                                ).stream())
                        .toList()
        );

        /*
         * 第 3 步：
         * 坐标吸附 + 正交化。
         */
        List<PreparedSegment> prepared = new ArrayList<>();

        for (RawSegment raw : valid) {

            Point start = new Point(
                    snapToAnchor(raw.start().x(), xAnchors),
                    snapToAnchor(raw.start().y(), yAnchors)
            );

            Point end = new Point(
                    snapToAnchor(raw.end().x(), xAnchors),
                    snapToAnchor(raw.end().y(), yAnchors)
            );

            PreparedSegment normalized =
                    orthogonalize(
                            start,
                            end,
                            raw.roomIndex()
                    );

            if (normalized.start()
                    .distance(normalized.end())
                    >= minSegmentLength) {

                prepared.add(normalized);
            }
        }

        if (prepared.isEmpty()) {
            return List.of();
        }

        /*
         * 第 4 步：
         * 水平、垂直、斜墙分开处理。
         */
        List<PreparedSegment> horizontals =
                prepared.stream()
                        .filter(segment ->
                                segment.orientation()
                                        == Orientation.HORIZONTAL)
                        .toList();

        List<PreparedSegment> verticals =
                prepared.stream()
                        .filter(segment ->
                                segment.orientation()
                                        == Orientation.VERTICAL)
                        .toList();

        List<PreparedSegment> diagonals =
                prepared.stream()
                        .filter(segment ->
                                segment.orientation()
                                        == Orientation.DIAGONAL)
                        .toList();

        /*
         * 第 5 步：
         * 相同 Y 的水平线归组；
         * 相同 X 的垂直线归组。
         */
        Map<Double, List<AxisSegment>> horizontalGroups =
                axisGroups(horizontals, true);

        Map<Double, List<AxisSegment>> verticalGroups =
                axisGroups(verticals, false);

        List<WallSegment> result = new ArrayList<>();

        /*
         * 第 6 步：
         * 对共线墙进行区间合并。
         */
        buildHorizontalSegments(
                horizontalGroups,
                verticalGroups,
                result
        );

        buildVerticalSegments(
                verticalGroups,
                horizontalGroups,
                result
        );

        /*
         * 第 7 步：
         * 对斜墙做近似去重。
         */
        buildDiagonalSegments(
                diagonals,
                result
        );

        /*
         * 保证输出顺序稳定。
         */
        result.sort(
                Comparator
                        .comparingDouble(
                                (WallSegment segment) ->
                                        Math.min(
                                                segment.start().y(),
                                                segment.end().y()
                                        )
                        )
                        .thenComparingDouble(
                                segment ->
                                        Math.min(
                                                segment.start().x(),
                                                segment.end().x()
                                        )
                        )
                        .thenComparingDouble(
                                segment ->
                                        Math.max(
                                                segment.start().y(),
                                                segment.end().y()
                                        )
                        )
                        .thenComparingDouble(
                                segment ->
                                        Math.max(
                                                segment.start().x(),
                                                segment.end().x()
                                        )
                        )
        );

        return result;
    }

    /**
     * 对近水平、近垂直墙进行正交化。
     */
    private PreparedSegment orthogonalize(
            Point start,
            Point end,
            int roomIndex) {

        double dx = end.x() - start.x();
        double dy = end.y() - start.y();

        double angle =
                Math.toDegrees(
                        Math.atan2(
                                Math.abs(dy),
                                Math.abs(dx)
                        )
                );

        /*
         * 接近 0°：
         * 当成水平墙。
         */
        if (angle <= orthogonalAngleDegrees) {

            double y =
                    (start.y() + end.y()) / 2.0;

            return new PreparedSegment(
                    new Point(start.x(), y),
                    new Point(end.x(), y),
                    roomIndex,
                    Orientation.HORIZONTAL
            );
        }

        /*
         * 接近 90°：
         * 当成垂直墙。
         */
        if (Math.abs(90.0 - angle)
                <= orthogonalAngleDegrees) {

            double x =
                    (start.x() + end.x()) / 2.0;

            return new PreparedSegment(
                    new Point(x, start.y()),
                    new Point(x, end.y()),
                    roomIndex,
                    Orientation.VERTICAL
            );
        }

        /*
         * 真正斜墙：
         * 保留原角度。
         */
        return new PreparedSegment(
                start,
                end,
                roomIndex,
                Orientation.DIAGONAL
        );
    }

    /**
     * 将水平或垂直墙按固定轴坐标进行归组。
     */
    private Map<Double, List<AxisSegment>> axisGroups(
            List<PreparedSegment> prepared,
            boolean horizontal) {

        if (prepared.isEmpty()) {
            return Map.of();
        }

        List<Double> fixedValues =
                prepared.stream()
                        .map(segment ->
                                horizontal
                                        ? segment.start().y()
                                        : segment.start().x())
                        .toList();

        /*
         * 对固定轴再次聚类。
         */
        List<Double> fixedAnchors =
                clusterCenters(fixedValues);

        Map<Double, List<AxisSegment>> groups =
                new LinkedHashMap<>();

        List<Double> orderedAnchors =
                new ArrayList<>(fixedAnchors);

        orderedAnchors.sort(Double::compareTo);

        for (double anchor : orderedAnchors) {
            groups.put(
                    anchor,
                    new ArrayList<>()
            );
        }

        for (PreparedSegment segment : prepared) {

            double fixed =
                    horizontal
                            ? segment.start().y()
                            : segment.start().x();

            double canonicalFixed =
                    nearestAnchor(
                            fixed,
                            fixedAnchors
                    );

            double a =
                    horizontal
                            ? segment.start().x()
                            : segment.start().y();

            double b =
                    horizontal
                            ? segment.end().x()
                            : segment.end().y();

            groups.get(canonicalFixed)
                    .add(
                            new AxisSegment(
                                    Math.min(a, b),
                                    Math.max(a, b),
                                    segment.roomIndex()
                            )
                    );
        }

        return groups;
    }

    /**
     * 构建规范化水平墙。
     */
    private void buildHorizontalSegments(
            Map<Double, List<AxisSegment>> horizontalGroups,
            Map<Double, List<AxisSegment>> verticalGroups,
            List<WallSegment> result) {

        for (Map.Entry<Double, List<AxisSegment>> entry
                : horizontalGroups.entrySet()) {

            double y = entry.getKey();

            List<AxisSegment> intervals =
                    entry.getValue();

            List<Double> cuts =
                    intervalEndpoints(intervals);

            /*
             * 加入所有垂直墙交点。
             *
             * 这样能够识别 T 型 / 十字连接。
             */
            for (Map.Entry<Double, List<AxisSegment>> vertical
                    : verticalGroups.entrySet()) {

                double x = vertical.getKey();

                if (covers(intervals, x)
                        && covers(
                        vertical.getValue(),
                        y)) {

                    cuts.add(x);
                }
            }

            for (IntervalSlice slice
                    : coveredSlices(
                    intervals,
                    cuts)) {

                result.add(
                        new WallSegment(
                                new Point(
                                        slice.from(),
                                        y
                                ),
                                new Point(
                                        slice.to(),
                                        y
                                ),
                                immutableOwners(
                                        slice.roomIndexes()
                                )
                        )
                );
            }
        }
    }

    /**
     * 构建规范化垂直墙。
     */
    private void buildVerticalSegments(
            Map<Double, List<AxisSegment>> verticalGroups,
            Map<Double, List<AxisSegment>> horizontalGroups,
            List<WallSegment> result) {

        for (Map.Entry<Double, List<AxisSegment>> entry
                : verticalGroups.entrySet()) {

            double x = entry.getKey();

            List<AxisSegment> intervals =
                    entry.getValue();

            List<Double> cuts =
                    intervalEndpoints(intervals);

            for (Map.Entry<Double, List<AxisSegment>> horizontal
                    : horizontalGroups.entrySet()) {

                double y = horizontal.getKey();

                if (covers(intervals, y)
                        && covers(
                        horizontal.getValue(),
                        x)) {

                    cuts.add(y);
                }
            }

            for (IntervalSlice slice
                    : coveredSlices(
                    intervals,
                    cuts)) {

                result.add(
                        new WallSegment(
                                new Point(
                                        x,
                                        slice.from()
                                ),
                                new Point(
                                        x,
                                        slice.to()
                                ),
                                immutableOwners(
                                        slice.roomIndexes()
                                )
                        )
                );
            }
        }
    }

    /**
     * 斜墙暂时采用近似端点去重。
     *
     * 当前 f1 全部属于水平/垂直墙，
     * 因此不会影响 f1。
     */
    private void buildDiagonalSegments(
            List<PreparedSegment> diagonals,
            List<WallSegment> result) {

        Map<String, MutableDiagonal> unique =
                new LinkedHashMap<>();

        for (PreparedSegment diagonal : diagonals) {

            Point first = diagonal.start();
            Point second = diagonal.end();

            /*
             * 固定起止点顺序：
             * A-B 与 B-A 视为同一条线。
             */
            if (comparePoint(first, second) > 0) {

                Point temporary = first;
                first = second;
                second = temporary;
            }

            String key =
                    approximatePointKey(first)
                            + "|"
                            + approximatePointKey(second);

            Point canonicalFirst = first;
            Point canonicalSecond = second;

            MutableDiagonal stored =
                    unique.computeIfAbsent(
                            key,
                            ignored ->
                                    new MutableDiagonal(
                                            canonicalFirst,
                                            canonicalSecond
                                    )
                    );

            stored.roomIndexes
                    .add(
                            diagonal.roomIndex()
                    );
        }

        for (MutableDiagonal diagonal
                : unique.values()) {

            result.add(
                    new WallSegment(
                            diagonal.start,
                            diagonal.end,
                            immutableOwners(
                                    diagonal.roomIndexes
                            )
                    )
            );
        }
    }

    /**
     * 把同一直线上的多个重叠区间切片，
     * 再合并连续覆盖区间。
     *
     * 例如：
     *
     * [156,302]
     * [156,242]
     * [156,346]
     *
     * 最终：
     *
     * [156,346]
     */
    private List<IntervalSlice> coveredSlices(
            List<AxisSegment> intervals,
            List<Double> rawCuts) {

        List<Double> cuts =
                uniqueSorted(rawCuts);

        List<IntervalSlice> slices =
                new ArrayList<>();

        for (int i = 0;
             i < cuts.size() - 1;
             i++) {

            double from = cuts.get(i);
            double to = cuts.get(i + 1);

            if (to - from
                    < minSegmentLength) {
                continue;
            }

            double middle =
                    (from + to) / 2.0;

            LinkedHashSet<Integer> owners =
                    new LinkedHashSet<>();

            for (AxisSegment interval : intervals) {

                if (middle
                        >= interval.from()
                        - NUMERIC_EPSILON
                        &&
                        middle
                                <= interval.to()
                                + NUMERIC_EPSILON) {

                    owners.add(
                            interval.roomIndex()
                    );
                }
            }

            if (!owners.isEmpty()) {

                slices.add(
                        new IntervalSlice(
                                from,
                                to,
                                owners
                        )
                );
            }
        }

        /*
         * 相邻且连续覆盖的区间继续合并。
         */
        return mergeAdjacentSlices(slices);
    }

    private List<IntervalSlice> mergeAdjacentSlices(
            List<IntervalSlice> slices) {

        if (slices.isEmpty()) {
            return slices;
        }

        List<IntervalSlice> merged =
                new ArrayList<>();

        IntervalSlice current =
                slices.get(0);

        for (int i = 1;
             i < slices.size();
             i++) {

            IntervalSlice next =
                    slices.get(i);

            if (Math.abs(
                    current.to()
                            - next.from()
            ) <= NUMERIC_EPSILON) {

                LinkedHashSet<Integer> owners =
                        new LinkedHashSet<>(
                                current.roomIndexes()
                        );

                owners.addAll(
                        next.roomIndexes()
                );

                current =
                        new IntervalSlice(
                                current.from(),
                                next.to(),
                                owners
                        );

            } else {

                merged.add(current);
                current = next;
            }
        }

        merged.add(current);

        return merged;
    }

    private boolean covers(
            List<AxisSegment> intervals,
            double coordinate) {

        for (AxisSegment interval : intervals) {

            if (coordinate
                    >= interval.from()
                    - NUMERIC_EPSILON
                    &&
                    coordinate
                            <= interval.to()
                            + NUMERIC_EPSILON) {

                return true;
            }
        }

        return false;
    }

    private List<Double> intervalEndpoints(
            List<AxisSegment> intervals) {

        List<Double> values =
                new ArrayList<>();

        for (AxisSegment interval : intervals) {

            values.add(
                    interval.from()
            );

            values.add(
                    interval.to()
            );
        }

        return values;
    }

    private List<Double> uniqueSorted(
            List<Double> values) {

        if (values.isEmpty()) {
            return List.of();
        }

        List<Double> sorted =
                new ArrayList<>(values);

        sorted.sort(
                Double::compareTo
        );

        List<Double> result =
                new ArrayList<>();

        for (double value : sorted) {

            if (result.isEmpty()
                    ||
                    Math.abs(
                            value
                                    - result.get(
                                    result.size() - 1
                            )
                    ) > NUMERIC_EPSILON) {

                result.add(value);
            }
        }

        return result;
    }

    /**
     * 对相近坐标聚类。
     */
    private List<Double> clusterCenters(
            List<Double> values) {

        if (values.isEmpty()) {
            return List.of();
        }

        List<Double> sorted =
                new ArrayList<>(values);

        sorted.sort(
                Double::compareTo
        );

        List<Double> centers =
                new ArrayList<>();

        double sum =
                sorted.get(0);

        int count = 1;

        double center =
                sorted.get(0);

        for (int i = 1;
             i < sorted.size();
             i++) {

            double value =
                    sorted.get(i);

            if (Math.abs(
                    value - center
            ) <= snapEpsilon) {

                sum += value;
                count++;

                center =
                        sum / count;

            } else {

                centers.add(center);

                sum = value;
                count = 1;
                center = value;
            }
        }

        centers.add(center);

        return centers;
    }

    private double snapToAnchor(
            double value,
            List<Double> anchors) {

        double nearest =
                nearestAnchor(
                        value,
                        anchors
                );

        return Math.abs(
                value - nearest
        ) <= snapEpsilon
                ? nearest
                : value;
    }

    private double nearestAnchor(
            double value,
            List<Double> anchors) {

        if (anchors.isEmpty()) {
            return value;
        }

        double nearest =
                anchors.get(0);

        double bestDistance =
                Math.abs(
                        value - nearest
                );

        for (int i = 1;
             i < anchors.size();
             i++) {

            double candidate =
                    anchors.get(i);

            double distance =
                    Math.abs(
                            value - candidate
                    );

            if (distance
                    < bestDistance) {

                nearest = candidate;
                bestDistance = distance;
            }
        }

        return nearest;
    }

    private String approximatePointKey(
            Point point) {

        long x =
                Math.round(
                        point.x()
                                / snapEpsilon
                );

        long y =
                Math.round(
                        point.y()
                                / snapEpsilon
                );

        return x + "," + y;
    }

    private int comparePoint(
            Point first,
            Point second) {

        int x =
                Double.compare(
                        first.x(),
                        second.x()
                );

        return x != 0
                ? x
                : Double.compare(
                first.y(),
                second.y()
        );
    }

    private Set<Integer> immutableOwners(
            Set<Integer> owners) {

        return Collections.unmodifiableSet(
                new LinkedHashSet<>(
                        owners
                )
        );
    }

    public record Point(
            double x,
            double y) {

        public double distance(
                Point other) {

            return Math.hypot(
                    x - other.x,
                    y - other.y
            );
        }
    }

    public record RawSegment(
            Point start,
            Point end,
            int roomIndex) {
    }

    public record WallSegment(
            Point start,
            Point end,
            Set<Integer> roomIndexes) {
    }

    private enum Orientation {

        HORIZONTAL,

        VERTICAL,

        DIAGONAL
    }

    private record PreparedSegment(
            Point start,
            Point end,
            int roomIndex,
            Orientation orientation) {
    }

    private record AxisSegment(
            double from,
            double to,
            int roomIndex) {
    }

    private record IntervalSlice(
            double from,
            double to,
            Set<Integer> roomIndexes) {
    }

    private static class MutableDiagonal {

        private final Point start;

        private final Point end;

        private final LinkedHashSet<Integer>
                roomIndexes =
                new LinkedHashSet<>();

        private MutableDiagonal(
                Point start,
                Point end) {

            this.start = start;
            this.end = end;
        }
    }
}