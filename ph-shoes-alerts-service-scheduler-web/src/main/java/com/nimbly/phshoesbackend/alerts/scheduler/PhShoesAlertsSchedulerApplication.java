package com.nimbly.phshoesbackend.alerts.scheduler;

import com.nimbly.phshoesbackend.alerts.core.config.props.SchedulerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SchedulerProperties.class)
@ComponentScan(basePackages = {
        "com.nimbly.phshoesbackend.alerts.core",
        "com.nimbly.phshoesbackend.alerts.scheduler.web",
        "com.nimbly.phshoesbackend.alerts.scheduler.web.config",
        "com.nimbly.phshoesbackend.services.common.core",
        "com.nimbly.phshoesbackend.notification.core"
})
public class PhShoesAlertsSchedulerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PhShoesAlertsSchedulerApplication.class, args);
    }
}
