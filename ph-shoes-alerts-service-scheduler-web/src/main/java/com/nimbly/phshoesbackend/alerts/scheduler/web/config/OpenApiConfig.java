package com.nimbly.phshoesbackend.alerts.scheduler.web.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "PH Shoes Alerts Scheduler",
                version = "v1",
                description = "Internal endpoints to observe/trigger the alerts scheduler."
        )
)
public class OpenApiConfig {
}
