package com.design3d.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 (Swagger) 配置
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI buildingModelingEngineOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("3DBuildingModelingEngine（三维建模引擎）API")
                        .description("3DBuildingModelingEngine 接收结构化方案图纸 JSON，转换为可交互的三维建筑模型。\n\n"
                                + "## 输入\n"
                                + "- **JSON 通道**: 上传结构化布局 JSON → 直接生成 3D 模型\n\n"
                                + "## 消息类型\n"
                                + "- `text`: 纯文本消息\n"
                                + "- `json_input`: 用户上传的结构化 JSON\n"
                                + "- `model`: AI 生成的 3D 模型（内嵌 glTF 查看器）")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("3DBuildingModelingEngine Team"))
                        .license(new License()
                                .name("MIT")));
    }
}
