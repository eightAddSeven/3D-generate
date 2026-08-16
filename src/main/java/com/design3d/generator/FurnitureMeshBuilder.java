package com.design3d.generator;

import com.design3d.model.layout.RoomLayout;

/**
 * 家具几何体构建器
 * <p>
 * MVP 版本：所有家具用单位立方体 + glTF node 的 scale 变换实现不同尺寸。
 * 常见家具类型有简化变体（沙发加扶手、桌子加腿等）。
 */
public class FurnitureMeshBuilder {

    /**
     * 构建家具几何体
     * MVP: 返回单位立方体（1x1x1 米），实际尺寸通过 glTF node scale 设置
     *
     * @param furniture 家具数据
     * @param materialIndex 材质索引
     * @return 单位立方体 MeshData
     */
    public static MeshData build(RoomLayout.Furniture furniture, int materialIndex) {
        String type = furniture.getType() != null ? furniture.getType() : "other";

        return switch (type) {
            case "rug", "carpet" -> buildFlat(furniture, materialIndex);
            case "pendant_light" -> buildPendant(furniture, materialIndex);
            case "dining_table" -> buildTable(furniture, materialIndex);
            case "shelf" -> buildShelf(furniture, materialIndex);
            case "sink" -> buildSink(furniture, materialIndex);
            case "stove" -> buildStove(furniture, materialIndex);
            default -> buildUnitBox(furniture, materialIndex);
        };
    }

    /**
     * 标准单位立方体
     */
    private static MeshData buildUnitBox(RoomLayout.Furniture f, int materialIndex) {
        MeshData mesh = new MeshData();
        mesh.setName(f.getId() != null ? f.getId() : "furniture");
        mesh.setMaterialIndex(materialIndex);

        // 单位立方体：中心在原点，尺寸 1x1x1
        mesh.addBox(0, 0.5f, 0, 1, 1, 1);
        return mesh;
    }

    /**
     * 扁平物体（地毯）：薄片
     */
    private static MeshData buildFlat(RoomLayout.Furniture f, int materialIndex) {
        MeshData mesh = new MeshData();
        mesh.setName(f.getId() != null ? f.getId() : "flat");
        mesh.setMaterialIndex(materialIndex);

        // Y=0 平面
        mesh.addQuad(-0.5f, 0, -0.5f, 0.5f, 0, -0.5f, 0.5f, 0, 0.5f, -0.5f, 0, 0.5f, 0, 1, 0);
        return mesh;
    }

    /**
     * 吊灯：顶部的薄片
     */
    private static MeshData buildPendant(RoomLayout.Furniture f, int materialIndex) {
        MeshData mesh = new MeshData();
        mesh.setName(f.getId() != null ? f.getId() : "pendant");
        mesh.setMaterialIndex(materialIndex);

        // 在 Y=1（天花板）处的小平面
        mesh.addBox(0, 1f, 0, 0.3f, 0.1f, 0.3f);
        return mesh;
    }

    private static MeshData buildTable(RoomLayout.Furniture f, int materialIndex) {
        MeshData mesh = namedMesh(f, materialIndex);
        mesh.addBox(0, 0.88f, 0, 1, 0.12f, 1);
        for (float x : new float[]{-0.4f, 0.4f}) {
            for (float z : new float[]{-0.4f, 0.4f}) {
                mesh.addBox(x, 0.41f, z, 0.1f, 0.82f, 0.1f);
            }
        }
        return mesh;
    }

    private static MeshData buildShelf(RoomLayout.Furniture f, int materialIndex) {
        MeshData mesh = namedMesh(f, materialIndex);
        for (float y : new float[]{0.05f, 0.35f, 0.65f, 0.95f}) {
            mesh.addBox(0, y, 0, 1, 0.08f, 1);
        }
        for (float x : new float[]{-0.46f, 0.46f}) {
            for (float z : new float[]{-0.46f, 0.46f}) {
                mesh.addBox(x, 0.5f, z, 0.08f, 1, 0.08f);
            }
        }
        return mesh;
    }

    private static MeshData buildSink(RoomLayout.Furniture f, int materialIndex) {
        MeshData mesh = namedMesh(f, materialIndex);
        mesh.addBox(0, 0.42f, 0, 1, 0.84f, 1);
        mesh.addBox(0, 0.9f, 0, 1.04f, 0.12f, 1.04f);
        mesh.addBox(-0.25f, 0.98f, 0, 0.38f, 0.04f, 0.55f);
        mesh.addBox(0.25f, 0.98f, 0, 0.38f, 0.04f, 0.55f);
        return mesh;
    }

    private static MeshData buildStove(RoomLayout.Furniture f, int materialIndex) {
        MeshData mesh = namedMesh(f, materialIndex);
        mesh.addBox(0, 0.45f, 0, 1, 0.9f, 1);
        for (float x : new float[]{-0.25f, 0.25f}) {
            for (float z : new float[]{-0.25f, 0.25f}) {
                mesh.addBox(x, 0.94f, z, 0.22f, 0.04f, 0.22f);
            }
        }
        return mesh;
    }

    private static MeshData namedMesh(RoomLayout.Furniture f, int materialIndex) {
        MeshData mesh = new MeshData();
        mesh.setName(f.getId() != null ? f.getId() : "furniture");
        mesh.setMaterialIndex(materialIndex);
        return mesh;
    }
}
