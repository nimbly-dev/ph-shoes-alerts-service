package com.nimbly.phshoesbackend.alerts.core.config.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "alerts.scheduler")
public class SchedulerProperties {
    /**
     * Cron used when running as a long-lived service.
     */
    private String cron = "0 30 23 * * *";

    /**
     * Time zone for cron evaluation (defaults to Manila to match scrape schedule).
     */
    private String zone = "Asia/Manila";

    /**
     * When true, skip persistence + outbound sends but keep evaluation + logging.
     */
    private boolean dryRun = false;

    /**
     * Optional shared-secret required for API access (header X-Scheduler-Key or query ?key=).
     * Blank disables the guard.
     */
    // deprecated; key guard removed
    private String key;

    /**
     * Optional: limit run to alerts owned by this email (exact match, normalized).
     */
    private String testEmail;
}
