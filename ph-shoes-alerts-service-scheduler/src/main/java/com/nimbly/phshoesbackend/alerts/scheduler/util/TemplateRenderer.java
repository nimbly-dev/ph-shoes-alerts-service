package com.nimbly.phshoesbackend.alerts.scheduler.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class TemplateRenderer {

    public String render(String classpathTemplate, Map<String, String> placeholders) {
        try {
            ClassPathResource res = new ClassPathResource(classpathTemplate);
            byte[] bytes = FileCopyUtils.copyToByteArray(res.getInputStream());
            String tpl = new String(bytes, StandardCharsets.UTF_8);
            String out = tpl;
            for (var entry : placeholders.entrySet()) {
                String key = "{{" + entry.getKey() + "}}";
                out = out.replace(key, entry.getValue() == null ? "" : entry.getValue());
            }
            // strip any leftover placeholders
            out = out.replaceAll("\\{\\{[^}]+}}", "");
            // rudimentary conditional removal for {% if original %} blocks
            if (!placeholders.containsKey("original") || placeholders.get("original") == null || placeholders.get("original").isBlank()) {
                out = out.replaceAll("\\{% if original %}.*?\\{% endif %}", "");
            } else {
                out = out.replace("{% if original %}", "").replace("{% endif %}", "");
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to render template " + classpathTemplate, e);
        }
    }
}
