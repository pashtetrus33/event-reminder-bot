package com.example.event_reminder_bot.service.impl;

import com.example.event_reminder_bot.config.NotificationScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicSchedulerService {

    private final TaskScheduler taskScheduler;
    private final NotificationScheduler notificationScheduler;

    private volatile ScheduledFuture<?> scheduledFuture;
    private volatile String cron = "0 30 8 * * *"; // cron по умолчанию (8:30 утра)

    public synchronized void scheduleTask(String newCron) {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            log.info("🛑 Старая задача отменена");
        }

        this.cron = newCron;

        CronTrigger trigger = new CronTrigger(cron);
        this.scheduledFuture = taskScheduler.schedule(
                notificationScheduler::sendEventNotifications,
                trigger
        );

        log.info("✅ Новое CRON-выражение установлено: {}", cron);
    }

    public String getCurrentCron() {
        return cron;
    }
}
