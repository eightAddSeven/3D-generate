package com.design3d.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@Tag(name = "3DBuildingModelingEngine · 系统", description = "三维建模引擎运行状态")
public class HealthController {

    @GetMapping("/api/v1/health")
    @Operation(summary = "检查三维建模引擎健康状态")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString(),
                "service", "3DBuildingModelingEngine",
                "version", "1.0.0"
        ));
    }
}
