package com.design3d.controller;

import com.design3d.model.entity.ModelFile;
import com.design3d.model.repository.ModelFileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RestController
@RequestMapping("/api/v1/models")
@RequiredArgsConstructor
@Tag(name = "3DBuildingModelingEngine · 模型文件", description = "三维建模引擎生成文件的获取与下载")
public class ModelFileController {

    private final ModelFileRepository modelFileRepository;

    @GetMapping("/{modelId}/file.glb")
    @Operation(summary = "下载三维建模引擎生成的 GLB 文件")
    public ResponseEntity<byte[]> downloadGlb(@PathVariable String modelId) throws IOException {
        ModelFile modelFile = modelFileRepository.findById(modelId)
                .orElse(null);
        if (modelFile == null) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = Paths.get(modelFile.getFilePath());
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        byte[] data = Files.readAllBytes(filePath);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + modelFile.getFileName() + "\"")
                .contentType(MediaType.parseMediaType("model/gltf-binary"))
                .contentLength(data.length)
                .body(data);
    }
}
