package com.assignment.freepoints.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI freePointOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Free Point System API")
                        .version("v1")
                        .description("무료 포인트 적립, 취소, 사용, 사용취소 및 조회 API")
                        .contact(new Contact().name("Assignment Project"))
                        .license(new License().name("Internal Assignment")))
                .servers(List.of(new Server().url("/").description("Default Server")));
    }
}
