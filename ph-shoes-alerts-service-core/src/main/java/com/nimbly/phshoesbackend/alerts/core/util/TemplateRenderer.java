package com.nimbly.phshoesbackend.alerts.core.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class TemplateRenderer {

    public String render(String classpathTemplate, Map<String, String> placeholders) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathTemplate);
            byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
            String template = new String(bytes, StandardCharsets.UTF_8);
            String output = template;
            for (var entry : placeholders.entrySet()) {
                String key = "{{" + entry.getKey() + "}}";
                output = output.replace(key, entry.getValue() == null ? "" : entry.getValue());
            }
            output = output.replaceAll("\\{\\{[^}]+}}", "");
            if (!placeholders.containsKey("original") || placeholders.get("original") == null || placeholders.get("original").isBlank()) {
                output = output.replaceAll("\\{% if original %}.*?\\{% endif %}", "");
            } else {
                output = output.replace("{% if original %}", "").replace("{% endif %}", "");
            }
            return output;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to render template " + classpathTemplate, ex);
        }
    }
}
