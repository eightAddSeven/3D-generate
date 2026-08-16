package com.design3d.service;

import com.design3d.exception.BusinessException;
import com.design3d.model.dto.MessageDTO;
import com.design3d.model.dto.SessionDTO;
import com.design3d.model.entity.Message;
import com.design3d.model.entity.Session;
import com.design3d.model.repository.MessageRepository;
import com.design3d.model.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;

    /**
     * 创建新会话
     */
    @Transactional
    public SessionDTO createSession(String title) {
        Session session = Session.builder()
                .id(UUID.randomUUID().toString())
                .title(title != null && !title.isBlank() ? title : "新对话")
                .build();
        session = sessionRepository.save(session);
        log.info("创建会话: {} ({})", session.getId(), session.getTitle());
        return toDTO(session, false);
    }

    /**
     * 获取会话列表（分页，不包含消息详情）
     */
    @Transactional(readOnly = true)
    public Page<SessionDTO> listSessions(Pageable pageable) {
        return sessionRepository.findAllByOrderByUpdatedAtDesc(pageable)
                .map(s -> toDTO(s, false));
    }

    /**
     * 获取会话详情（含全部消息）
     */
    @Transactional(readOnly = true)
    public SessionDTO getSession(String sessionId) {
        Session session = findById(sessionId);
        return toDTO(session, true);
    }

    /**
     * 更新会话标题
     */
    @Transactional
    public SessionDTO updateSession(String sessionId, String title) {
        Session session = findById(sessionId);
        session.setTitle(title);
        session.setUpdatedAt(LocalDateTime.now());
        session = sessionRepository.save(session);
        return toDTO(session, false);
    }

    /**
     * 删除会话
     */
    @Transactional
    public void deleteSession(String sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw BusinessException.notFound("会话", sessionId);
        }
        sessionRepository.deleteById(sessionId);
        log.info("删除会话: {}", sessionId);
    }

    public Session findById(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> BusinessException.notFound("会话", sessionId));
    }

    /**
     * 自动命名：根据消息内容智能生成标题
     */
    @Transactional
    public void autoRename(Session session) {
        if (!"新对话".equals(session.getTitle())) return;

        // 从数据库查第一条用户消息，避免懒加载问题
        List<Message> userMessages = messageRepository
                .findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .filter(m -> "user".equals(m.getRole()) && m.getContent() != null)
                .collect(Collectors.toList());

        if (userMessages.isEmpty()) return;

        Message firstMsg = userMessages.get(0);
        String title;

        switch (firstMsg.getMsgType()) {
            case "json_input" -> title = generateJsonTitle(firstMsg);
            default -> title = generateTextTitle(firstMsg);
        }

        session.setTitle(title);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    private String generateJsonTitle(Message msg) {
        String fileName = extractFileName(msg.getMetadata());
        if (fileName != null && !fileName.isBlank()) {
            String name = fileName.replace(".json", "").replace(".JSON", "");
            return "布局方案: " + truncate(name, 15);
        }
        return "布局方案";
    }

    private String generateTextTitle(Message msg) {
        String content = msg.getContent();
        if (content == null || content.isBlank()) return "新对话";
        // 去掉换行，取前 20 字
        String cleaned = content.replace("\n", " ").replace("\r", "").trim();
        return truncate(cleaned, 20);
    }

    private String extractFileName(String metadata) {
        if (metadata == null) return null;
        try {
            // metadata 是 JSON: {"fileName": "xxx.png"}
            int start = metadata.indexOf("\"fileName\":\"");
            if (start < 0) return null;
            start += 12;
            int end = metadata.indexOf("\"", start);
            if (end < 0) return null;
            return metadata.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    /**
     * 更新会话时间
     */
    @Transactional
    public void touchSession(Session session) {
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    // ---- DTO 转换 ----

    private SessionDTO toDTO(Session session, boolean includeMessages) {
        SessionDTO.SessionDTOBuilder builder = SessionDTO.builder()
                .id(session.getId())
                .title(session.getTitle())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .messageCount(includeMessages ? session.getMessages().size() : 0);

        if (includeMessages) {
            builder.messages(session.getMessages().stream()
                    .map(this::toMessageDTO)
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    private MessageDTO toMessageDTO(Message msg) {
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
