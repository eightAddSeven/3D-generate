package com.design3d.config;

import com.design3d.generator.GltfGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * glTF 生成器配置
 */
@Configuration
public class GltfGeneratorConfig {

    @Value("${app.layout.default-wall-height:2800}")
    private double defaultWallHeight;

    @Value("${app.layout.default-wall-thickness:240}")
    private double defaultWallThickness;

    @Bean
    public GltfGenerator gltfGenerator() {
        return new GltfGenerator(defaultWallHeight, defaultWallThickness);
    }
}
