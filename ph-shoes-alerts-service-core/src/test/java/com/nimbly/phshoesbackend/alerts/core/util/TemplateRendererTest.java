package com.nimbly.phshoesbackend.alerts.core.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void render_whenOriginalProvided_keepsOriginalSection() {
        // Arrange
        Map<String, String> placeholders = Map.of(
                "name", "Ada",
                "items", "ItemList",
                "original", "PHP 199.00"
        );

        // Act
        String output = renderer.render("templates/test-template.html", placeholders);

        // Assert
        assertNotNull(output);
        assertTrue(output.contains("Hello Ada"));
        assertTrue(output.contains("Items: ItemList"));
        assertTrue(output.contains("Original: PHP 199.00"));
        assertFalse(output.contains("{{"));
        assertFalse(output.contains("{%"));
    }

    @Test
    void render_whenOriginalMissing_removesOriginalSection() {
        // Arrange
        Map<String, String> placeholders = Map.of(
                "name", "Grace",
                "items", "ItemList"
        );

        // Act
        String output = renderer.render("templates/test-template.html", placeholders);

        // Assert
        assertNotNull(output);
        assertTrue(output.contains("Hello Grace"));
        assertFalse(output.contains("Original:"));
        assertFalse(output.contains("{{unused}}"));
    }

    @Test
    void render_whenTemplateMissing_throwsIllegalStateException() {
        // Arrange
        Map<String, String> placeholders = Map.of();

        // Act
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> renderer.render("templates/missing-template.html", placeholders)
        );

        // Assert
        assertTrue(exception.getMessage().contains("Failed to render template templates/missing-template.html"));
    }
}
