package com.design3d.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 模型文件实体 — 存储生成的 glTF/glb 文件引用
 */
@Entity
@Table(name = "model_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelFile {

    @Id
    @Column(length = 36)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    @Builder.Default
    private Long fileSize = 0L;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String format = "glb";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
