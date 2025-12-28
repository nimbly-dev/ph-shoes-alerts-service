package com.nimbly.phshoesbackend.alerts.scheduler.web;

import com.nimbly.phshoesbackend.alerts.api.AlertsSchedulerApi;
import com.nimbly.phshoesbackend.alerts.core.model.dto.AlertsSchedulerRunResponse;
import com.nimbly.phshoesbackend.alerts.core.model.SchedulerRunSummary;
import com.nimbly.phshoesbackend.alerts.core.service.AlertsSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class SchedulerController implements AlertsSchedulerApi {

    private final AlertsSchedulerService schedulerService;

    @Override
    public ResponseEntity<AlertsSchedulerRunResponse> runAlertsScheduler(@Nullable LocalDate date, @Nullable String email) {
        LocalDate target = date == null ? LocalDate.now() : date;
        String trimmedEmail = email == null ? null : email.trim();
        SchedulerRunSummary summary = schedulerService.run(target, trimmedEmail);
        AlertsSchedulerRunResponse response = new AlertsSchedulerRunResponse()
                .date(summary.getDate())
                .scrapedCount(summary.getScrapedCount())
                .dedupedCount(summary.getDedupedCount())
                .alertsChecked(summary.getAlertsChecked())
                .triggered(summary.getTriggered())
                .suppressed(summary.getSuppressed())
                .errors(summary.getErrors());
        return ResponseEntity.ok(response);
    }
}
