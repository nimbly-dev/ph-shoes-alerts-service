package com.nimbly.phshoesbackend.alerts.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
        basePackages = {
                "com.nimbly.phshoesbackend.alerts.core",
                "com.nimbly.phshoesbackend.alerts.web",
                "com.nimbly.phshoesbackend.commons.core"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.nimbly\\.phshoesbackend\\.alerts\\.core\\.scheduler\\..*")
)
public class PhShoesAlertsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PhShoesAlertsServiceApplication.class, args);
    }
}
