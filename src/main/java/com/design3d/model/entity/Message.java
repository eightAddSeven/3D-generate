package com.design3d.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 消息实体 — 对话中的一条记录
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    /**
     * 角色: user / assistant / system
     */
    @Column(nullable = false, length = 20)
    private String role;

    /**
     * 消息类型: text / model / json_input
     */
    @Column(name = "msg_type", nullable = false, length = 20)
    private String msgType;

    /**
     * 消息正文:
     * - text: 文本内容
     * - json_input: JSON 原始字符串
     * - model: 生成描述文字
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * 扩展元数据 (JSON string):
     * - model 类型: {"modelId": "uuid", "modelUrl": "/api/v1/models/xxx/file.glb"}
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ModelFile modelFile;
}
