package com.design3d.generator;

/**
 * 地面几何体构建器
 */
public class FloorMeshBuilder {

    /**
     * 构建地面平面
     *
     * @param width  整体空间宽度 (mm)
     * @param depth  整体空间深度 (mm)
     * @param materialIndex 材质索引
     * @return 地面 MeshData
     */
    public static MeshData build(double width, double depth, int materialIndex) {
        MeshData mesh = new MeshData();
        mesh.setName("floor");
        mesh.setMaterialIndex(materialIndex);

        float w = (float) (width / 1000.0);  // mm → m
        float d = (float) (depth / 1000.0);

        // 在 Y=0 处构建平面，法线朝上
        mesh.addQuad(0, 0, 0, w, 0, 0, w, 0, d, 0, 0, d, 0, 1, 0);

        return mesh;
    }
}
