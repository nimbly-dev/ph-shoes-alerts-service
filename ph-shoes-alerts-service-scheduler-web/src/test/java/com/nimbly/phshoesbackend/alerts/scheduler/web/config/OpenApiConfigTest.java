package com.nimbly.phshoesbackend.alerts.scheduler.web.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenApiConfigTest {

    @Test
    void openApiDefinition_hasExpectedMetadata() {
        // Arrange
        OpenAPIDefinition definition = OpenApiConfig.class.getAnnotation(OpenAPIDefinition.class);

        // Act
        Info info = definition == null ? null : definition.info();

        // Assert
        assertNotNull(definition);
        assertNotNull(info);
        assertEquals("PH Shoes Alerts Scheduler", info.title());
        assertEquals("v1", info.version());
        assertEquals("Internal endpoints to observe/trigger the alerts scheduler.", info.description());
    }
}
