package com.nimbly.phshoesbackend.alerts.scheduler.web;

import com.nimbly.phshoesbackend.alerts.scheduler.service.AlertsSchedulerService;
import com.nimbly.phshoesbackend.alerts.scheduler.service.AlertsSchedulerService.SchedulerRunSummary;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class SchedulerController {

    private final AlertsSchedulerService schedulerService;

    @Operation(summary = "Run scheduler for date", description = "Optional email limits execution to the account owner for dry/test runs.")
    @GetMapping("/api/v1/alerts-scheduler/run")
    public ResponseEntity<SchedulerRunSummary> run(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "email", required = false) String email) {
        LocalDate target = date == null ? LocalDate.now() : date;
        SchedulerRunSummary summary = schedulerService.run(target, email == null ? null : email.trim());
        return ResponseEntity.ok(summary);
    }
}
