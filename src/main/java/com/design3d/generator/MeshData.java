package com.design3d.generator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * 单个 Mesh 的顶点数据容器
 */
public class MeshData {

    private final List<Float> vertices = new ArrayList<>();
    private final List<Float> normals = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();
    private final List<Float> uvs = new ArrayList<>();

    private int materialIndex = 0;
    private String name = "mesh";

    // 顶点计数
    private int vertexCount = 0;

    /**
     * 添加一个三角形（3 个顶点）
     */
    public MeshData addTriangle(
            float x1, float y1, float z1, float nx, float ny, float nz,
            float x2, float y2, float z2,
            float x3, float y3, float z3) {
        int base = vertexCount;

        // 顶点 1
        vertices.add(x1); vertices.add(y1); vertices.add(z1);
        normals.add(nx); normals.add(ny); normals.add(nz);
        uvs.add(0f); uvs.add(0f);

        // 顶点 2
        vertices.add(x2); vertices.add(y2); vertices.add(z2);
        normals.add(nx); normals.add(ny); normals.add(nz);
        uvs.add(1f); uvs.add(0f);

        // 顶点 3
        vertices.add(x3); vertices.add(y3); vertices.add(z3);
        normals.add(nx); normals.add(ny); normals.add(nz);
        uvs.add(0f); uvs.add(1f);

        // 索引
        indices.add(base);
        indices.add(base + 1);
        indices.add(base + 2);

        vertexCount += 3;
        return this;
    }

    /**
     * 添加一个四边形（2 个三角形）
     */
    public MeshData addQuad(
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float nx, float ny, float nz) {
        // 三角形 1: v1, v2, v3
        // 三角形 2: v1, v3, v4
        int base = vertexCount;

        vertices.addAll(List.of(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4));
        for (int i = 0; i < 4; i++) {
            normals.addAll(List.of(nx, ny, nz));
        }
        uvs.addAll(List.of(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f));

        indices.addAll(List.of(base, base + 1, base + 2, base, base + 2, base + 3));
        vertexCount += 4;
        return this;
    }

    /**
     * 添加一个长方体（6 面 × 2 三角形 = 12 个三角形, 36 个顶点）
     */
    public MeshData addBox(float cx, float cy, float cz,
                            float sx, float sy, float sz) {
        float hx = sx / 2, hy = sy / 2, hz = sz / 2;
        float x0 = cx - hx, x1 = cx + hx;
        float y0 = cy - hy, y1 = cy + hy;
        float z0 = cz - hz, z1 = cz + hz;

        // +Y 顶面 (法线朝上)
        addQuad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0);
        // -Y 底面 (法线朝下)
        addQuad(x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0);
        // +X 右面
        addQuad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0);
        // -X 左面
        addQuad(x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0);
        // +Z 前面
        addQuad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);
        // -Z 后面
        addQuad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1);

        return this;
    }

    /** Add a box rotated around the vertical Y axis. */
    public MeshData addOrientedBox(float cx, float cy, float cz,
                                   float sx, float sy, float sz, float angle) {
        float hx = sx / 2, hy = sy / 2, hz = sz / 2;
        float[][] local = {
                {-hx, -hy, -hz}, {hx, -hy, -hz}, {hx, -hy, hz}, {-hx, -hy, hz},
                {-hx, hy, -hz}, {hx, hy, -hz}, {hx, hy, hz}, {-hx, hy, hz}
        };
        float[][] p = new float[8][3];
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        for (int i = 0; i < local.length; i++) {
            p[i][0] = cx + local[i][0] * cos - local[i][2] * sin;
            p[i][1] = cy + local[i][1];
            p[i][2] = cz + local[i][0] * sin + local[i][2] * cos;
        }
        orientedQuad(p, 4, 5, 6, 7, 0, 1, 0);
        orientedQuad(p, 3, 2, 1, 0, 0, -1, 0);
        orientedQuad(p, 1, 2, 6, 5, cos, 0, sin);
        orientedQuad(p, 3, 0, 4, 7, -cos, 0, -sin);
        orientedQuad(p, 2, 3, 7, 6, -sin, 0, cos);
        orientedQuad(p, 0, 1, 5, 4, sin, 0, -cos);
        return this;
    }

    private void orientedQuad(float[][] p, int a, int b, int c, int d,
                              float nx, float ny, float nz) {
        addQuad(p[a][0], p[a][1], p[a][2], p[b][0], p[b][1], p[b][2],
                p[c][0], p[c][1], p[c][2], p[d][0], p[d][1], p[d][2], nx, ny, nz);
    }

    /**
     * 将所有顶点数据写入 ByteBuffer，返回写入的字节数
     */
    public int writeVertices(ByteBuffer buf) {
        for (float v : vertices) buf.putFloat(v);
        return vertices.size() * 4;
    }

    public int writeNormals(ByteBuffer buf) {
        for (float n : normals) buf.putFloat(n);
        return normals.size() * 4;
    }

    public int writeIndices(ByteBuffer buf) {
        for (int i : indices) buf.putShort((short) i);
        return indices.size() * 2;
    }

    public int writeUvs(ByteBuffer buf) {
        for (float u : uvs) buf.putFloat(u);
        return uvs.size() * 4;
    }

    // ---- 访问器 ----

    public int getVertexCount() { return vertexCount; }
    public int getIndexCount() { return indices.size(); }

    public float[] getVertexMinMax() {
        if (vertices.isEmpty()) return new float[]{0, 0, 0, 0, 0, 0};
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < vertices.size(); i += 3) {
            float x = vertices.get(i), y = vertices.get(i + 1), z = vertices.get(i + 2);
            if (x < minX) minX = x; if (x > maxX) maxX = x;
            if (y < minY) minY = y; if (y > maxY) maxY = y;
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
        }
        return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    // ---- Getters/Setters ----

    public int getMaterialIndex() { return materialIndex; }
    public void setMaterialIndex(int idx) { this.materialIndex = idx; }

    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
}
