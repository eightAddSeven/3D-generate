package com.design3d.generator;

import com.design3d.model.layout.RoomLayout;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * glTF 2.0 模型生成器 — 主控流程
 * <p>
 * 将 RoomLayout 转换为 .glb 二进制文件:
 * 1. 遍历房间和家具，构建所有 MeshData
 * 2. 将所有顶点数据写入统一二进制 Buffer
 * 3. 构建 glTF JSON 结构（bufferViews, accessors, meshes, nodes, materials）
 * 4. 打包为 .glb 文件
 */
@Slf4j
public class GltfGenerator {

    private final double defaultWallHeight;
    private final double defaultWallThickness;

    public GltfGenerator(double defaultWallHeight, double defaultWallThickness) {
        this.defaultWallHeight = defaultWallHeight;
        this.defaultWallThickness = defaultWallThickness;
    }

    /**
     * 从 RoomLayout 生成 .glb 文件
     *
     * @param layout 房间布局
     * @return .glb 文件字节数组
     */
    public byte[] generate(RoomLayout layout) throws IOException {
        log.info("开始生成 3D 模型: {} 个房间", layout.getRooms() != null ? layout.getRooms().size() : 0);

        MaterialHelper matHelper = new MaterialHelper();
        List<MeshData> allMeshes = new ArrayList<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> meshes = new ArrayList<>();

        double totalW = layout.getLayout().getWidth();
        double totalD = layout.getLayout().getDepth();
        double wallH = layout.getLayout().getHeight() > 0 ? layout.getLayout().getHeight() : defaultWallHeight;
        double wallT = layout.getLayout().getWallThickness() > 0 ? layout.getLayout().getWallThickness() : defaultWallThickness;

        // === 1) 构建地面 ===
        int floorMatIdx = matHelper.getOrCreateMaterial("wood_light", "#D2B48C");
        MeshData floor = FloorMeshBuilder.build(totalW, totalD, floorMatIdx);
        allMeshes.add(floor);
        nodes.add(Map.of("mesh", 0, "name", "floor"));
        meshes.add(buildGltfMesh(floor, 0));

        // === 2) 遍历房间 → 构建墙体 ===
        int meshIndex = 1;
        if (layout.getRooms() != null) {
            for (RoomLayout.Room room : layout.getRooms()) {
                if (room.getWalls() != null) {
                    for (RoomLayout.Wall wall : room.getWalls()) {
                        int matIdx = matHelper.getOrCreateMaterial(wall.getMaterial(), wall.getColor());
                        MeshData wallMesh = WallMeshBuilder.build(wall, wallT, matIdx);
                        allMeshes.add(wallMesh);
                        nodes.add(Map.of("mesh", meshIndex, "name", wall.getId()));
                        meshes.add(buildGltfMesh(wallMesh, meshIndex));
                        meshIndex++;

                    }
                }
            }
        }

        // === 3) 遍历家具 ===
        if (layout.getRooms() != null) {
            for (RoomLayout.Room room : layout.getRooms()) {
                if (room.getFurniture() != null) {
                    for (RoomLayout.Furniture furn : room.getFurniture()) {
                        meshIndex = addFurnitureNode(furn, matHelper, allMeshes, nodes, meshes, meshIndex);
                    }
                }
            }
        }
        // 公共家具
        if (layout.getFurniture() != null) {
            for (RoomLayout.Furniture furn : layout.getFurniture()) {
                meshIndex = addFurnitureNode(furn, matHelper, allMeshes, nodes, meshes, meshIndex);
            }
        }

        log.info("构建了 {} 个 mesh, {} 个节点, {} 种材质", allMeshes.size(), nodes.size(), matHelper.getMaterialCount());

        // === 4) 组装二进制数据 ===
        byte[] binData = buildBinaryBuffer(allMeshes);

        // === 5) 构建 glTF JSON ===
        String json = buildGltfJson(allMeshes, meshes, nodes, matHelper);

        // === 6) 打包 .glb ===
        return GltfWriter.writeGlb(json, binData);
    }

    private int addFurnitureNode(RoomLayout.Furniture furn, MaterialHelper matHelper,
                                  List<MeshData> allMeshes, List<Map<String, Object>> nodes,
                                  List<Map<String, Object>> meshes, int meshIndex) {
        int matIdx = matHelper.getOrCreateMaterial(furn.getMaterial(), furn.getColor());
        MeshData furnMesh = FurnitureMeshBuilder.build(furn, matIdx);
        allMeshes.add(furnMesh);

        // 家具的 glTF node — 设置 translation, rotation, scale
        float tx = (float) (furn.getPosition().getX() / 1000.0);
        float tz = (float) (furn.getPosition().getZ() / 1000.0);
        float rotY = (float) Math.toRadians(furn.getRotation());
        float sx = (float) (furn.getSize().getWidth() / 1000.0);
        float sy = (float) (furn.getSize().getHeight() / 1000.0);
        float sz = (float) (furn.getSize().getDepth() / 1000.0);

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("mesh", meshIndex);
        node.put("name", furn.getId() != null ? furn.getId() : furn.getType());
        node.put("translation", List.of(tx, 0.0, tz));
        node.put("rotation", List.of(0.0, Math.sin(rotY / 2), 0.0, Math.cos(rotY / 2)));
        node.put("scale", List.of(sx, sy, sz));
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("type", furn.getType());
        if (furn.getCategory() != null) extras.put("category", furn.getCategory());
        if (furn.getRoomId() != null) extras.put("roomId", furn.getRoomId());
        node.put("extras", extras);
        nodes.add(node);

        meshes.add(buildGltfMesh(furnMesh, meshIndex));
        return meshIndex + 1;
    }

    /**
     * 将所有 MeshData 的顶点数据写入统一的 ByteBuffer
     */
    private byte[] buildBinaryBuffer(List<MeshData> allMeshes) {
        // 计算总大小
        int totalVertSize = 0, totalNormSize = 0, totalIdxSize = 0, totalUvSize = 0;
        for (MeshData m : allMeshes) {
            totalVertSize += m.getVertexCount() * 3 * 4;  // float32 × 3
            totalNormSize += m.getVertexCount() * 3 * 4;
            totalIdxSize += m.getIndexCount() * 2;        // uint16
            totalUvSize += m.getVertexCount() * 2 * 4;    // float32 × 2
        }

        int totalSize = totalVertSize + totalNormSize + totalIdxSize + totalUvSize;
        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        for (MeshData m : allMeshes) {
            m.writeVertices(buf);
        }
        for (MeshData m : allMeshes) {
            m.writeNormals(buf);
        }
        for (MeshData m : allMeshes) {
            m.writeIndices(buf);
        }
        for (MeshData m : allMeshes) {
            m.writeUvs(buf);
        }

        return buf.array();
    }

    /**
     * 构建 glTF JSON 字符串（不含 binary chunk）
     */
    private String buildGltfJson(List<MeshData> allMeshes, List<Map<String, Object>> meshes,
                                  List<Map<String, Object>> nodes, MaterialHelper matHelper) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // asset
        sb.append("\"asset\":{\"version\":\"2.0\",\"generator\":\"3DBuildingModelingEngine\"},");
        // scene
        sb.append("\"scene\":0,");
        // scenes
        sb.append("\"scenes\":[{\"nodes\":[");
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(i);
        }
        sb.append("]}],");

        // nodes
        sb.append("\"nodes\":[");
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(nodes.get(i)));
        }
        sb.append("],");

        // meshes
        sb.append("\"meshes\":[");
        for (int i = 0; i < meshes.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(meshes.get(i)));
        }
        sb.append("],");

        // accessors
        sb.append("\"accessors\":[");
        buildAccessors(sb, allMeshes);
        sb.append("],");

        // bufferViews
        sb.append("\"bufferViews\":[");
        buildBufferViews(sb, allMeshes);
        sb.append("],");

        // buffers
        int totalByteLength = 0;
        for (MeshData m : allMeshes) {
            totalByteLength += m.getVertexCount() * 3 * 4;  // vertices
            totalByteLength += m.getVertexCount() * 3 * 4;  // normals
            totalByteLength += m.getIndexCount() * 2;       // indices
            totalByteLength += m.getVertexCount() * 2 * 4;  // uvs
        }
        sb.append("\"buffers\":[{\"byteLength\":").append(totalByteLength).append("}],");

        // materials
        sb.append("\"materials\":").append(matHelper.toGltfMaterialsJson());
        sb.append("}");

        return sb.toString();
    }

    /**
     * 构建 bufferViews JSON — 每类数据一个 bufferView
     */
    private void buildBufferViews(StringBuilder sb, List<MeshData> allMeshes) {
        int offset = 0;

        // bufferView 0: 所有顶点 (VEC3, FLOAT)
        int vertSize = 0;
        for (MeshData m : allMeshes) vertSize += m.getVertexCount() * 3 * 4;
        sb.append(String.format(Locale.US,
                "{\"buffer\":0,\"byteOffset\":%d,\"byteLength\":%d,\"target\":34962},", offset, vertSize));
        offset += vertSize;

        // bufferView 1: 所有法线 (VEC3, FLOAT)
        int normSize = 0;
        for (MeshData m : allMeshes) normSize += m.getVertexCount() * 3 * 4;
        sb.append(String.format(Locale.US,
                "{\"buffer\":0,\"byteOffset\":%d,\"byteLength\":%d},", offset, normSize));
        offset += normSize;

        // bufferView 2: 所有索引 (SCALAR, UNSIGNED_SHORT)
        int idxSize = 0;
        for (MeshData m : allMeshes) idxSize += m.getIndexCount() * 2;
        sb.append(String.format(Locale.US,
                "{\"buffer\":0,\"byteOffset\":%d,\"byteLength\":%d,\"target\":34963},", offset, idxSize));
        offset += idxSize;

        // bufferView 3: 所有 UV (VEC2, FLOAT)
        int uvSize = 0;
        for (MeshData m : allMeshes) uvSize += m.getVertexCount() * 2 * 4;
        sb.append(String.format(Locale.US,
                "{\"buffer\":0,\"byteOffset\":%d,\"byteLength\":%d}", offset, uvSize));
    }

    /**
     * 构建 accessors JSON
     */
    private void buildAccessors(StringBuilder sb, List<MeshData> allMeshes) {
        // 为每个 mesh 创建 4 个 accessor: POSITION, NORMAL, INDEX, TEXCOORD_0
        // 由于所有数据按类型集中存放，accessor 需要指定正确的偏移
        int vertByteOffset = 0, normByteOffset = 0, idxByteOffset = 0, uvByteOffset = 0;

        // 计算总偏移基础值
        int totalVertSize = 0, totalNormSize = 0, totalIdxSize = 0, totalUvSize = 0;
        for (MeshData m : allMeshes) {
            totalVertSize += m.getVertexCount() * 3 * 4;
            totalNormSize += m.getVertexCount() * 3 * 4;
            totalIdxSize += m.getIndexCount() * 2;
            totalUvSize += m.getVertexCount() * 2 * 4;
        }

        int normStart = totalVertSize;
        int idxStart = normStart + totalNormSize;
        int uvStart = idxStart + totalIdxSize;

        for (int i = 0; i < allMeshes.size(); i++) {
            MeshData m = allMeshes.get(i);
            float[] mm = m.getVertexMinMax();
            int vCount = m.getVertexCount();

            if (i > 0) sb.append(",");

            // POSITION accessor
            sb.append(String.format(Locale.US,
                    "{\"bufferView\":0,\"byteOffset\":%d,\"componentType\":5126,\"count\":%d,\"type\":\"VEC3\",\"max\":[%.3f,%.3f,%.3f],\"min\":[%.3f,%.3f,%.3f]},",
                    vertByteOffset, vCount, mm[3], mm[4], mm[5], mm[0], mm[1], mm[2]));
            vertByteOffset += vCount * 3 * 4;

            // NORMAL accessor
            sb.append(String.format(Locale.US,
                    "{\"bufferView\":1,\"byteOffset\":%d,\"componentType\":5126,\"count\":%d,\"type\":\"VEC3\"},",
                    normByteOffset, vCount));
            normByteOffset += vCount * 3 * 4;

            // INDEX accessor (SCALAR, UNSIGNED_SHORT)
            int iCount = m.getIndexCount();
            sb.append(String.format(Locale.US,
                    "{\"bufferView\":2,\"byteOffset\":%d,\"componentType\":5123,\"count\":%d,\"type\":\"SCALAR\"},",
                    idxByteOffset, iCount));
            idxByteOffset += iCount * 2;

            // TEXCOORD_0 accessor
            sb.append(String.format(Locale.US,
                    "{\"bufferView\":3,\"byteOffset\":%d,\"componentType\":5126,\"count\":%d,\"type\":\"VEC2\"}",
                    uvByteOffset, vCount));
            uvByteOffset += vCount * 2 * 4;
        }
    }

    /**
     * 构建单个 glTF mesh 定义
     */
    private Map<String, Object> buildGltfMesh(MeshData mesh, int meshIdx) {
        int accessorBase = meshIdx * 4;
        Map<String, Object> primitive = new LinkedHashMap<>();
        primitive.put("attributes", Map.of(
                "POSITION", accessorBase,
                "NORMAL", accessorBase + 1,
                "TEXCOORD_0", accessorBase + 3
        ));
        primitive.put("indices", accessorBase + 2);
        primitive.put("material", mesh.getMaterialIndex());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", mesh.getName());
        m.put("primitives", List.of(primitive));
        return m;
    }

    /**
     * 简单的 Map → JSON 序列化（不依赖 Jackson，避免复杂依赖）
     */
    @SuppressWarnings("unchecked")
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":");
            appendValue(sb, e.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append("\"").append(s).append("\"");
        } else if (value instanceof Number n) {
            if (n.doubleValue() == Math.floor(n.doubleValue()) && !Double.isInfinite(n.doubleValue())) {
                sb.append(n.longValue());
            } else {
                sb.append(String.format(Locale.US, "%.6f", n.doubleValue()));
            }
        } else if (value instanceof Boolean b) {
            sb.append(b);
        } else if (value instanceof List list) {
            sb.append("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                appendValue(sb, list.get(i));
            }
            sb.append("]");
        } else if (value instanceof Map map) {
            sb.append(toJson(map));
        }
    }
}
