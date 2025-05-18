package com.example.event_reminder_bot.config;

import com.example.event_reminder_bot.service.impl.DynamicSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchedulerInitializer implements ApplicationRunner {

    private final DynamicSchedulerService schedulerService;

    @Override
    public void run(ApplicationArguments args) {
        schedulerService.scheduleTask(schedulerService.getCurrentCron());
    }
}