package com.design3d.service;

import com.design3d.model.dto.ChatResponse;
import com.design3d.model.dto.MessageDTO;
import com.design3d.model.entity.Message;
import com.design3d.model.entity.ModelFile;
import com.design3d.model.entity.Session;
import com.design3d.model.layout.RoomLayout;
import com.design3d.model.repository.MessageRepository;
import com.design3d.model.repository.ModelFileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 对话处理编排服务 — 核心路由逻辑
 * <p>
 * 根据请求内容路由到不同的处理通道:
 * - 仅文本: 直接回复
 * - 含 JSON: JSON → 直接 3D 模型生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final SessionService sessionService;
    private final FileStorageService fileStorageService;
    private final LayoutGenerationService layoutGenerationService;
    private final MessageRepository messageRepository;
    private final ModelFileRepository modelFileRepository;
    private final ObjectMapper objectMapper;

    /**
     * 核心对话方法 — 根据输入路由到不同通道
     */
    @Transactional
    public ChatResponse processChat(String sessionId, String text, MultipartFile layoutJson) {
        long startTime = System.currentTimeMillis();

        Session session = sessionService.findById(sessionId);
        List<MessageDTO> newMessages = new ArrayList<>();

        try {
            // === 路由判断 ===
            boolean hasJson = layoutJson != null && !layoutJson.isEmpty();

            if (hasJson) {
                // 通道一: JSON → 直接生成 3D 模型
                newMessages.addAll(processJsonChannel(session, text, layoutJson));
            } else if (text != null && !text.isBlank()) {
                // 通道二: 纯文本对话
                newMessages.addAll(processTextOnly(session, text));
            } else {
                // 无效请求
                Message helpMsg = createMessage(session, "assistant", "text",
                        "请发送文字描述或上传布局 JSON 文件。\n\n**支持的输入方式：**\n- 📝 发送文字描述设计方案\n- 📄 上传房间布局 JSON 文件，直接生成 3D 模型",
                        null);
                newMessages.add(toDTO(helpMsg));
            }

            // 自动命名会话
            sessionService.autoRename(session);
            sessionService.touchSession(session);

        } catch (Exception e) {
            log.error("处理对话失败", e);
            Message errMsg = createMessage(session, "assistant", "text",
                    "处理失败: " + e.getMessage(), null);
            newMessages.add(toDTO(errMsg));
        }

        double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
        log.info("对话处理完成: session={}, 耗时={}s, 生成消息数={}", sessionId, elapsed, newMessages.size());

        return ChatResponse.builder()
                .sessionId(sessionId)
                .newMessages(newMessages)
                .processingTimeSeconds(elapsed)
                .build();
    }

    /**
     * 通道一: JSON 直接生成 3D 模型
     */
    private List<MessageDTO> processJsonChannel(Session session, String text, MultipartFile layoutJson) throws Exception {
        List<MessageDTO> results = new ArrayList<>();

        // 1) 读取 JSON 内容
        String jsonContent = new String(layoutJson.getBytes());
        String fileName = layoutJson.getOriginalFilename();

        // 2) 创建用户消息 (json_input 类型)
        Map<String, String> jsonMeta = Map.of("fileName", fileName);
        Message userMsg = createMessage(session, "user", "json_input",
                text != null ? text : "上传了布局 JSON",
                objectMapper.writeValueAsString(jsonMeta));
        // 将 JSON 内容存入 metadata
        userMsg.setMetadata(objectMapper.writeValueAsString(
                Map.of("fileName", fileName, "jsonContent", jsonContent)));
        results.add(toDTO(userMsg));

        // 3) 校验 JSON + 生成 3D 模型
        RoomLayout layout;
        try {
            layout = layoutGenerationService.parseAndValidate(jsonContent);
        } catch (Exception e) {
            Message errMsg = createMessage(session, "assistant", "text",
                    "JSON 格式校验失败: " + e.getMessage() + "\n\n请检查格式是否符合房间布局规范。", null);
            results.add(toDTO(errMsg));
            return results;
        }

        // 生成模型
        byte[] glbData = layoutGenerationService.generateGlb(layout);
        int wallCount = countWalls(layout);
        int furnitureCount = countFurniture(layout);
        String modelFileName = fileName != null ? fileName.replace(".json", "") + ".glb" : "layout.glb";
        FileStorageService.StoredFile modelStored = fileStorageService.storeModel(glbData, modelFileName);

        // 先创建消息，再关联模型文件
        Message modelMsg = createMessage(session, "assistant", "model",
                "3D 模型已生成：" + wallCount + " 面墙，" + furnitureCount
                        + " 件家具设备。可以在下方查看和交互。", null);
        ModelFile modelFile = createModelFile(modelMsg, modelStored.originalName(),
                modelStored.filePath().toString(), modelStored.size(), "glb");

        String modelUrl = "/api/v1/models/" + modelFile.getId() + "/file.glb";
        Map<String, String> modelMeta = Map.of(
                "modelId", modelFile.getId(),
                "modelUrl", modelUrl,
                "fileName", modelStored.originalName(),
                "wallCount", String.valueOf(wallCount),
                "furnitureCount", String.valueOf(furnitureCount)
        );
        modelMsg.setMetadata(objectMapper.writeValueAsString(modelMeta));
        messageRepository.save(modelMsg);
        results.add(toDTO(modelMsg));

        return results;
    }

    private int countWalls(RoomLayout layout) {
        if (layout.getRooms() == null) return 0;
        return layout.getRooms().stream()
                .mapToInt(room -> room.getWalls() != null ? room.getWalls().size() : 0).sum();
    }

    private int countFurniture(RoomLayout layout) {
        int count = layout.getFurniture() != null ? layout.getFurniture().size() : 0;
        if (layout.getRooms() != null) {
            count += layout.getRooms().stream()
                    .mapToInt(room -> room.getFurniture() != null ? room.getFurniture().size() : 0).sum();
        }
        return count;
    }

    /**
     * 通道二: 纯文本对话
     */
    private List<MessageDTO> processTextOnly(Session session, String text) {
        List<MessageDTO> results = new ArrayList<>();

        // 用户消息
        Message userMsg = createMessage(session, "user", "text", text, null);
        results.add(toDTO(userMsg));

        // 助手回复
        String reply = buildTextReply(text);
        Message assistantMsg = createMessage(session, "assistant", "text", reply, null);
        results.add(toDTO(assistantMsg));

        return results;
    }

    /**
     * 创建并保存消息
     */
    public Message createMessage(Session session, String role, String msgType, String content, String metadata) {
        Message message = Message.builder()
                .id(UUID.randomUUID().toString())
                .session(session)
                .role(role)
                .msgType(msgType)
                .content(content)
                .metadata(metadata)
                .build();
        return messageRepository.save(message);
    }

    /**
     * 创建模型关联记录
     */
    public ModelFile createModelFile(Message message, String fileName, String filePath, long fileSize, String format) {
        ModelFile modelFile = ModelFile.builder()
                .id(UUID.randomUUID().toString())
                .message(message)
                .fileName(fileName)
                .filePath(filePath)
                .fileSize(fileSize)
                .format(format)
                .build();
        return modelFileRepository.save(modelFile);
    }

    /**
     * 简单的文本自动回复
     */
    private String buildTextReply(String userText) {
        if (userText.contains("你好") || userText.contains("hi") || userText.contains("hello")) {
            return "你好！我是 3D 室内设计助手。\n\n我可以帮你：\n- 📄 **上传布局 JSON 文件**，直接生成 3D 模型\n- 💬 **描述你的设计想法**，我会给出建议\n\n请告诉我你需要什么帮助？";
        }
        if (userText.contains("帮助") || userText.contains("help") || userText.contains("功能")) {
            return "**3D 室内设计助手 使用说明**\n\n**📝 文本对话**\n直接输入文字描述你的设计想法，我会给出建议。\n\n**📄 JSON 直接生成**\n上传标准房间布局 JSON 文件，直接生成可交互的 3D 模型。\n\n**支持的房间类型**: 客厅、卧室、厨房、卫生间、餐厅、书房、走廊、阳台";
        }
        return "收到你的消息。目前我支持基础文本对话和 **布局 JSON** 直接建模。输入「帮助」了解更多。";
    }

    // ---- 工具方法 ----

    private MessageDTO toDTO(Message msg) {
        return MessageDTO.builder()
                .id(msg.getId())
                .role(msg.getRole())
                .msgType(msg.getMsgType())
                .content(msg.getContent())
                .metadata(msg.getMetadata())
                .createdAt(msg.getCreatedAt())
                .build();
    }
}
