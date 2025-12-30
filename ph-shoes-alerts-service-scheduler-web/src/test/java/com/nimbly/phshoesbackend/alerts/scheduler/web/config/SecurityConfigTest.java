package com.nimbly.phshoesbackend.alerts.scheduler.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.SecurityFilterChain;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SecurityAutoConfiguration.class,
                    SecurityFilterAutoConfiguration.class
            ))
            .withUserConfiguration(SecurityConfig.class)
            .withBean(CorsConfigurationSource.class, SecurityConfigTest::corsConfigurationSource);

    @Test
    void schedulerSecurityFilterChain_isRegistered() {
        // Arrange
        // ApplicationContextRunner handles context setup.

        // Act
        contextRunner.run(context -> {
            // Assert
            assertTrue(context.containsBean("schedulerSecurityFilterChain"));
            SecurityFilterChain chain = context.getBean(SecurityFilterChain.class);
            assertNotNull(chain);
        });
    }

    private static CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOriginPattern("*");
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
