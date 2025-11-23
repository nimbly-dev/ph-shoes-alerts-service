package com.nimbly.phshoesbackend.alerts.scheduler.service;

import com.nimbly.phshoesbackend.alerts.scheduler.config.SchedulerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertsSchedulerJobLauncher implements CommandLineRunner {

    private final AlertsSchedulerService schedulerService;
    private final SchedulerProperties props;

    @Override
    public void run(String... args) {
        if (!props.isRunOnStartup()) {
            log.info("scheduler.startup run skipped (alerts.scheduler.run-on-startup=false)");
            return;
        }
        LocalDate date = todayInZone();
        log.info("scheduler.startup run date={} zone={} testEmail={}", date, props.getZone(), props.getTestEmail());
        schedulerService.run(date);
    }

    @Scheduled(cron = "${alerts.scheduler.cron:0 30 23 * * *}", zone = "${alerts.scheduler.zone:Asia/Manila}")
    public void scheduledRun() {
        LocalDate date = todayInZone();
        log.info("scheduler.cron trigger date={} zone={}", date, props.getZone());
        schedulerService.run(date);
    }

    private LocalDate todayInZone() {
        ZoneId zoneId = ZoneId.of(props.getZone());
        return LocalDate.now(zoneId);
    }
}
