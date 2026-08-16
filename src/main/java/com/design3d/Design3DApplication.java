package com.design3d;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.nio.file.Paths;

@SpringBootApplication
public class Design3DApplication {

    public static void main(String[] args) {
        // 在 Spring 启动前解析 data 目录的绝对路径，设入系统属性
        // 这样 application.yml 中的 ${data.dir} 就能正确引用
        File dataDir = Paths.get("data").toAbsolutePath().normalize().toFile();
        dataDir.mkdirs();
        new File(dataDir, "h2").mkdirs();
        new File(dataDir, "models").mkdirs();
        new File(dataDir, "temp").mkdirs();
        System.setProperty("data.dir", dataDir.getAbsolutePath());

        System.out.println("========================================");
        System.out.println(" 数据根目录: " + dataDir.getAbsolutePath());
        System.out.println("========================================");

        SpringApplication.run(Design3DApplication.class, args);
    }
}
