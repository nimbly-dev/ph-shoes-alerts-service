package com.nimbly.phshoesbackend.alerts.web;

import com.nimbly.phshoesbackend.useraccount.core.auth.JwtTokenProvider;
import com.nimbly.phshoesbackend.useraccount.core.config.props.AppAuthProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.nimbly.phshoesbackend.alerts.core",
        "com.nimbly.phshoesbackend.alerts.web",
        "com.nimbly.phshoesbackend.services.common.core"
})
@Import({JwtTokenProvider.class, AppAuthProps.class})
public class PhShoesAlertsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PhShoesAlertsServiceApplication.class, args);
    }
}
