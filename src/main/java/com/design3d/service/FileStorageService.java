package com.design3d.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 通用文件存储服务
 */
@Slf4j
@Service
public class FileStorageService {

    @Value("${app.file-storage.model-dir:./data/models}")
    private String modelDir;

    /**
     * 存储生成的模型文件（二进制内容）
     */
    public StoredFile storeModel(byte[] data, String originalFileName) throws IOException {
        Path dir = Paths.get(modelDir);
        Files.createDirectories(dir);

        String extension = getExtension(originalFileName);
        if (extension.isEmpty()) extension = "glb";
        String storedName = UUID.randomUUID().toString() + "." + extension;
        Path filePath = dir.resolve(storedName);
        Files.write(filePath, data);

        return new StoredFile(originalFileName, storedName, filePath, (long) data.length);
    }

    public byte[] readFile(Path filePath) throws IOException {
        return Files.readAllBytes(filePath);
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * 存储文件的结果
     */
    public record StoredFile(
            String originalName,
            String storedName,
            Path filePath,
            long size
    ) {}
}
