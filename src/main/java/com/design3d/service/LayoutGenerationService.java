package com.design3d.service;

import com.design3d.generator.GltfGenerator;
import com.design3d.generator.ObjGenerator;
import com.design3d.model.layout.RoomLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Set;

/**
 * 布局 → 3D 模型生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LayoutGenerationService {

    private final GltfGenerator gltfGenerator;
    private final ObjGenerator objGenerator;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final TeacherLayoutAdapter teacherLayoutAdapter;
    private final MixedRecognitionLayoutAdapter mixedRecognitionLayoutAdapter;
    private final RoomPolygonLayoutAdapter roomPolygonLayoutAdapter;

    /**
     * 从 RoomLayout 生成 .glb 模型文件
     *
     * @param layout 房间布局
     * @return .glb 文件字节数组
     */
    public byte[] generateGlb(RoomLayout layout) throws IOException {
        log.info("开始生成 3D 模型: {} 个房间", layout.getRooms() != null ? layout.getRooms().size() : 0);
        return gltfGenerator.generate(layout);
    }

    /** Generate a grouped Wavefront OBJ directly from the layout. */
    public byte[] generateObj(RoomLayout layout) {
        log.info("开始生成 OBJ 模型: {} 个房间", layout.getRooms() != null ? layout.getRooms().size() : 0);
        return objGenerator.generate(layout);
    }

    /**
     * 从 JSON 字符串解析 RoomLayout 并生成模型
     *
     * @param json 房间布局 JSON 字符串
     * @return .glb 文件字节数组
     */
    public byte[] generateGlbFromJson(String json) throws IOException {
        return generateGlb(parseAndValidate(json));
    }

    /** Parse and validate JSON before producing a grouped OBJ. */
    public byte[] generateObjFromJson(String json) throws IOException {
        return generateObj(parseAndValidate(json));
    }

    public RoomLayout parseAndValidate(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        RoomLayout layout;
        if (isRoomPolygonFormat(root)) {
            layout = roomPolygonLayoutAdapter.adapt(root);
        } else if (isMixedRecognitionFormat(root)) {
            layout = mixedRecognitionLayoutAdapter.adapt(root);
        } else if (root.isArray()) {
            layout = teacherLayoutAdapter.adapt(root);
        } else {
            layout = objectMapper.treeToValue(root, RoomLayout.class);
        }
        Set<ConstraintViolation<RoomLayout>> violations = validator.validate(layout);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException("布局 JSON 校验失败", violations);
        }
        return layout;
    }

    private boolean isRoomPolygonFormat(JsonNode root) {
        JsonNode rooms = root.isObject() ? root.get("raw_room_data") : root;
        return rooms != null && rooms.isArray() && !rooms.isEmpty()
                && rooms.get(0).isObject() && rooms.get(0).has("coordinates");
    }

    private boolean isMixedRecognitionFormat(JsonNode root) {
        if (!root.isArray()) return false;
        for (JsonNode item : root) {
            if (item.isArray() && item.size() == 4 && item.get(3).canConvertToInt()) return true;
            if (item.isArray() && item.size() == 2 && item.get(1).isTextual()) return true;
        }
        return false;
    }
}
