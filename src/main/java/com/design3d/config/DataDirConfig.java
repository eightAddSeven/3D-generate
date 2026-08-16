package com.design3d.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * 启动时确认数据目录位置（由 Design3DApplication.main 在启动前设好）
 */
@Slf4j
@Configuration
public class DataDirConfig {

    @PostConstruct
    public void init() {
        String dataDir = System.getProperty("data.dir", "未设置");
        log.info("数据目录: {}", dataDir);
    }
}
