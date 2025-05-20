package com.example.event_reminder_bot.config;

import com.example.event_reminder_bot.bot.EventReminderBot;
import com.example.event_reminder_bot.model.entity.Event;
import com.example.event_reminder_bot.model.entity.TelegramUser;
import com.example.event_reminder_bot.service.EventService;
import com.example.event_reminder_bot.service.TelegramUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final EventService eventService;
    private final TelegramUserService telegramUserService;
    private final DocumentProcessor documentProcessor;
    private EventReminderBot telegramBot;

    @Lazy
    @Autowired
    public void setTelegramBot(EventReminderBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final SchedulerProperties schedulerProperties;


    public void sendEventNotifications() {
        log.info("⏰ Запуск обработки файла...");
        documentProcessor.processDocument();


        log.info("⏰ Запуск планировщика уведомлений...");
        notifyUsers(schedulerProperties.getDays());
        notifyUsers(0); // сегодня
    }

    private void notifyUsers(int daysFromNow) {
        LocalDate targetDate = LocalDate.now().plusDays(daysFromNow);
        List<Event> events = eventService.getEventsForDate(targetDate);

        if (events.isEmpty()) {
            log.info("Нет мероприятий на {}", targetDate);
            return;
        }

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("🔔 <b>Напоминание о мероприятии</b>\n\n");

        if (daysFromNow == 0) {
            messageBuilder.append("📅 <b>Сегодня</b> состоится:\n\n");
        } else {
            messageBuilder.append(String.format("⏳ Через <b>%d</b> дня, <b>%s</b> состоится:\n\n",
                    daysFromNow, targetDate.format(FORMATTER)));
        }

        for (Event event : events) {
            messageBuilder.append("✅ <i>")
                    .append(event.getDescription())
                    .append("</i>\n");
        }

        List<TelegramUser> users = telegramUserService.findAll();
        for (TelegramUser user : users) {
            if (user.isHasAccess()) {
                SendMessage message = SendMessage.builder()
                        .chatId(user.getTelegramId().toString())
                        .text(messageBuilder.toString())
                        .parseMode("HTML")
                        .build();
                try {
                    telegramBot.execute(message);
                } catch (Exception e) {
                    log.error("Не удалось отправить уведомление пользователю {}: {}", user.getTelegramId(), e.getMessage());
                }
            }
        }
    }
}