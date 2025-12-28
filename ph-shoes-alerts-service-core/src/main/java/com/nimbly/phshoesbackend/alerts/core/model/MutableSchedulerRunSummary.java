package com.nimbly.phshoesbackend.alerts.core.model;


import lombok.Getter;

import java.time.LocalDate;

@Getter
public class MutableSchedulerRunSummary {
    private final LocalDate date;
    private final int scrapedCount;
    private final int dedupedCount;
    private int alertsChecked;
    private int triggered;
    private int emailsSent;
    private int suppressed;
    private int errors;

    public MutableSchedulerRunSummary(LocalDate date, int scrapedCount, int dedupedCount) {
        this.date = date;
        this.scrapedCount = scrapedCount;
        this.dedupedCount = dedupedCount;
    }

    public void incAlertsChecked() { alertsChecked++; }
    public void incTriggered() { triggered++; }
    public void applyEmailReport(EmailDeliveryReport report) {
        if (report == null) {
            return;
        }
        emailsSent += report.sent();
        suppressed += report.suppressed();
        errors += report.errors();
    }

    public SchedulerRunSummary asImmutable() {
        return new SchedulerRunSummary(date, scrapedCount, dedupedCount,
                alertsChecked, triggered, emailsSent, suppressed, errors);
    }

}
