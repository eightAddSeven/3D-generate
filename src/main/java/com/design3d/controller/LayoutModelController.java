package com.design3d.controller;

import com.design3d.service.LayoutGenerationService;
import com.design3d.model.layout.RoomLayout;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Direct, stateless JSON-to-model Web API. */
@RestController
@RequestMapping("/api/v1/layouts")
@RequiredArgsConstructor
@Tag(name = "3DBuildingModelingEngine · 模型生成", description = "三维建模引擎：结构化方案图纸 JSON 直接生成三维模型")
public class LayoutModelController {

    private final LayoutGenerationService layoutGenerationService;

    @PostMapping(value = "/model.obj", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "model/obj")
    @Operation(summary = "三维建模引擎生成 OBJ 模型",
            description = "兼容标准 RoomLayout、老师识别数组、混合识别结果和房间多边形 JSON，无需外部 CAD 运行时")
    public ResponseEntity<byte[]> generateObj(
            @Parameter(description = "标准 RoomLayout 或老师识别结果 JSON 文件", required = true)
            @RequestPart("layoutJson")
            @Schema(type = "string", format = "binary") MultipartFile layoutJson) throws IOException {
        RoomLayout layout = layoutGenerationService.parseAndValidate(readJson(layoutJson));
        byte[] data = layoutGenerationService.generateObj(layout);
        return download(data, outputFilename(layoutJson, "obj"), "model/obj", statistics(layout));
    }

    @PostMapping(value = "/model.glb", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "model/gltf-binary")
    @Operation(summary = "三维建模引擎生成 GLB 模型",
            description = "兼容标准 RoomLayout、老师识别数组、混合识别结果和房间多边形 JSON，响应为可下载的 GLB 文件")
    public ResponseEntity<byte[]> generateGlb(
            @Parameter(description = "标准 RoomLayout 或老师识别结果 JSON 文件", required = true)
            @RequestPart("layoutJson")
            @Schema(type = "string", format = "binary") MultipartFile layoutJson) throws IOException {
        RoomLayout layout = layoutGenerationService.parseAndValidate(readJson(layoutJson));
        byte[] data = layoutGenerationService.generateGlb(layout);
        return download(data, outputFilename(layoutJson, "glb"), "model/gltf-binary", statistics(layout));
    }

    private String readJson(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择非空的 JSON 文件");
        }
        return new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private ResponseEntity<byte[]> download(byte[] data, String filename, String contentType, ModelStatistics stats) {
        String sha256 = sha256(data);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .header("X-Model-SHA256", sha256)
                .header("X-Model-Wall-Count", String.valueOf(stats.walls))
                .header("X-Model-Furniture-Count", String.valueOf(stats.furniture))
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(data.length)
                .body(data);
    }

    private ModelStatistics statistics(RoomLayout layout) {
        int walls = 0, furniture = layout.getFurniture() != null ? layout.getFurniture().size() : 0;
        if (layout.getRooms() != null) {
            for (RoomLayout.Room room : layout.getRooms()) {
                if (room.getWalls() != null) walls += room.getWalls().size();
                if (room.getFurniture() != null) furniture += room.getFurniture().size();
            }
        }
        return new ModelStatistics(walls, furniture);
    }

    private record ModelStatistics(int walls, int furniture) {}

    private String outputFilename(MultipartFile source, String extension) {
        String original = source.getOriginalFilename();
        if (original == null || original.isBlank()) return "model." + extension;
        String safe = java.nio.file.Path.of(original).getFileName().toString();
        int dot = safe.lastIndexOf('.');
        String base = dot > 0 ? safe.substring(0, dot) : safe;
        base = base.replaceAll("[^\\p{L}\\p{N}_.-]+", "_");
        return (base.isBlank() ? "model" : base) + "." + extension;
    }

    private String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", e);
        }
    }
}
