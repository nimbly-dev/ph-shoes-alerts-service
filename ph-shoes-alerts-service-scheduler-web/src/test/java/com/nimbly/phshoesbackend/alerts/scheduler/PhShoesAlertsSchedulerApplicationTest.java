package com.nimbly.phshoesbackend.alerts.scheduler;

import com.nimbly.phshoesbackend.alerts.core.config.props.SchedulerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhShoesAlertsSchedulerApplicationTest {

    @Test
    void applicationAnnotations_includeExpectedConfiguration() {
        // Arrange
        SpringBootApplication springBootApplication = PhShoesAlertsSchedulerApplication.class
                .getAnnotation(SpringBootApplication.class);
        EnableScheduling enableScheduling = PhShoesAlertsSchedulerApplication.class
                .getAnnotation(EnableScheduling.class);
        EnableConfigurationProperties enableConfigurationProperties = PhShoesAlertsSchedulerApplication.class
                .getAnnotation(EnableConfigurationProperties.class);
        ComponentScan componentScan = PhShoesAlertsSchedulerApplication.class
                .getAnnotation(ComponentScan.class);

        // Act
        List<Class<?>> configuredProps = enableConfigurationProperties == null
                ? List.of()
                : List.of(enableConfigurationProperties.value());
        List<String> scannedPackages = componentScan == null
                ? List.of()
                : List.of(componentScan.basePackages());

        // Assert
        assertNotNull(springBootApplication);
        assertNotNull(enableScheduling);
        assertTrue(configuredProps.contains(SchedulerProperties.class));
        assertTrue(scannedPackages.contains("com.nimbly.phshoesbackend.alerts.core"));
        assertTrue(scannedPackages.contains("com.nimbly.phshoesbackend.alerts.scheduler.web"));
        assertTrue(scannedPackages.contains("com.nimbly.phshoesbackend.alerts.scheduler.web.config"));
        assertTrue(scannedPackages.contains("com.nimbly.phshoesbackend.commons.core"));
        assertTrue(scannedPackages.contains("com.nimbly.phshoesbackend.notification.core"));
    }
}
