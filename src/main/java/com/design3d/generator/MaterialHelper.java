package com.design3d.generator;

import java.util.*;

/**
 * 材质辅助工具 — 颜色 hex / 材质名 → glTF PBR Material
 */
public class MaterialHelper {

    // 预设材质 → PBR 参数
    private static final Map<String, float[]> PRESET_MATERIALS = new LinkedHashMap<>();

    static {
        PRESET_MATERIALS.put("paint_white",    new float[]{0.95f, 0.95f, 0.95f, 0.9f, 0.0f});
        PRESET_MATERIALS.put("paint_beige",    new float[]{0.96f, 0.94f, 0.88f, 0.9f, 0.0f});
        PRESET_MATERIALS.put("paint_gray",     new float[]{0.75f, 0.75f, 0.75f, 0.9f, 0.0f});
        PRESET_MATERIALS.put("wood_light",     new float[]{0.82f, 0.71f, 0.55f, 0.7f, 0.0f});
        PRESET_MATERIALS.put("wood_dark",      new float[]{0.36f, 0.25f, 0.20f, 0.7f, 0.0f});
        PRESET_MATERIALS.put("fabric_gray",    new float[]{0.50f, 0.50f, 0.50f, 1.0f, 0.0f});
        PRESET_MATERIALS.put("fabric_beige",   new float[]{0.82f, 0.78f, 0.70f, 1.0f, 0.0f});
        PRESET_MATERIALS.put("metal_silver",   new float[]{0.75f, 0.75f, 0.75f, 0.3f, 0.8f});
        PRESET_MATERIALS.put("plant_green",    new float[]{0.13f, 0.55f, 0.13f, 0.8f, 0.0f});
        PRESET_MATERIALS.put("glass",          new float[]{0.85f, 0.92f, 0.95f, 0.3f, 0.0f});
        PRESET_MATERIALS.put("default",        new float[]{0.80f, 0.80f, 0.80f, 0.9f, 0.0f});
    }

    private final List<float[]> materials = new ArrayList<>();
    private final Map<String, Integer> materialIndex = new HashMap<>();

    /**
     * 获取或创建材质索引
     */
    public int getOrCreateMaterial(String materialName, String hexColor) {
        String key = (materialName != null ? materialName : "") + "|" + (hexColor != null ? hexColor : "");
        if (materialIndex.containsKey(key)) {
            return materialIndex.get(key);
        }

        float[] pbr = resolveMaterial(materialName, hexColor);
        int index = materials.size();
        materials.add(pbr);
        materialIndex.put(key, index);
        return index;
    }

    /**
     * 生成 glTF materials JSON 数组
     */
    public String toGltfMaterialsJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < materials.size(); i++) {
            if (i > 0) sb.append(",");
            float[] m = materials.get(i);
            sb.append(String.format(Locale.US,
                    "{\"pbrMetallicRoughness\":{\"baseColorFactor\":[%.3f,%.3f,%.3f,1.0],\"roughnessFactor\":%.3f,\"metallicFactor\":%.3f}}",
                    m[0], m[1], m[2], m[3], m[4]));
        }
        sb.append("]");
        return sb.toString();
    }

    public int getMaterialCount() {
        return materials.size();
    }

    /**
     * 解析材质：优先用 hex 颜色，其次查预设表
     */
    private float[] resolveMaterial(String name, String hex) {
        // 如果提供了 hex 颜色，直接解析
        if (hex != null && !hex.isBlank()) {
            float[] rgb = hexToRgb(hex);
            if (rgb != null) {
                // 根据材质名推断 roughness
                float roughness = 0.9f;
                float metallic = 0.0f;
                if (name != null) {
                    if (name.contains("metal")) { roughness = 0.3f; metallic = 0.8f; }
                    else if (name.contains("wood")) { roughness = 0.7f; }
                    else if (name.contains("fabric")) { roughness = 1.0f; }
                    else if (name.contains("glass")) { roughness = 0.3f; }
                }
                return new float[]{rgb[0], rgb[1], rgb[2], roughness, metallic};
            }
        }

        // 查预设表
        float[] preset = PRESET_MATERIALS.getOrDefault(name, PRESET_MATERIALS.get("default"));
        return Arrays.copyOf(preset, 5);
    }

    /**
     * hex 颜色字符串转 RGB (0-1)
     */
    public static float[] hexToRgb(String hex) {
        if (hex == null) return null;
        hex = hex.replace("#", "").trim();
        if (hex.length() != 6) return null;
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new float[]{r / 255f, g / 255f, b / 255f};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
