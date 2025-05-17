package com.example.event_reminder_bot.bot;

import com.example.event_reminder_bot.config.TelegramBotConfig;
import com.example.event_reminder_bot.model.entity.TelegramUser;
import com.example.event_reminder_bot.service.EventService;
import com.example.event_reminder_bot.service.TelegramUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;

@Component("telegramBot")
@RequiredArgsConstructor
@Slf4j
public class EventReminderBot extends TelegramLongPollingBot {

    private final TelegramBotConfig config;
    private final TelegramUserService userService;
    private final EventService eventService;

    @Value("${bot.access.password}")
    private String correctPassword;

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) {
            return; // Если нет сообщения - выходим
        }

        Message message = update.getMessage();
        Long chatId = message.getChatId();
        User telegramUser = message.getFrom();

        try {
            // 1. Проверка и обработка доступа пользователя
            userService.findByTelegramId(chatId).ifPresentOrElse(
                    user -> processUserAccess(user, message, chatId),
                    () -> handleNewUser(chatId, telegramUser) // Обработка нового пользователя
            );

            // 2. Обработка документов (вынесена в отдельный блок для ясности)
            if (message.hasDocument()) {
                handleDocument(message.getDocument(), chatId);
            }
        } catch (Exception e) {
            log.error("Ошибка обработки сообщения от chatId: {}", chatId, e);
            sendMessage(chatId, "Произошла ошибка при обработке вашего запроса.");
        }
    }

    private void processUserAccess(TelegramUser user, Message message, Long chatId) {
        if (user.isHasAccess()) {
            sendMessage(chatId, user.getFirstName() + " вы уже подписаны на рассылку оповещений.");
            return; // Пользователь уже имеет доступ
        }

        try {
            String text = message.getText();
            if (correctPassword.equals(text)) {
                user.setHasAccess(true);
                userService.registerOrUpdateUser(user);
                sendMessage(chatId, "Привет, " + user.getFirstName() +
                                    "! Вы подписаны на уведомления о мероприятиях.");
            } else {
                sendMessage(chatId, "Неверный пароль. Попробуйте еще раз.");
            }
        } catch (Exception e) {
            log.error("Ошибка предоставления доступа пользователю {}", chatId, e);
            sendMessage(chatId, "Ошибка при обработке пароля.");
        }
    }

    private void handleNewUser(Long chatId, User telegramUser) {
        // Логика для нового пользователя
        TelegramUser user = TelegramUser.builder()
                .telegramId(telegramUser.getId())
                .username(telegramUser.getUserName())
                .firstName(telegramUser.getFirstName())
                .lastName(telegramUser.getLastName())
                .isBot(telegramUser.getIsBot())
                .languageCode(telegramUser.getLanguageCode())
                .build();

        userService.registerOrUpdateUser(user);
        sendMessage(chatId, "Введите пароль для доступа:");
    }

    private void handleDocument(Document document, Long chatId) {
        if (!document.getFileName().endsWith(".xlsx")) {
            sendMessage(chatId, "Пожалуйста, отправьте Excel файл с расширением .xlsx.");
            return;
        }

        try {
            org.telegram.telegrambots.meta.api.objects.File file = execute(new GetFile(document.getFileId()));
            String fileUrl = "https://api.telegram.org/file/bot" + config.getBotToken() + "/" + file.getFilePath();

            try (InputStream inputStream = new URL(fileUrl).openStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                inputStream.transferTo(baos);
                byte[] excelBytes = baos.toByteArray();

                eventService.importEventsFromExcel(excelBytes);

                sendMessage(chatId, "Файл успешно обработан. Мероприятия добавлены.");
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке документа: {}", e.getMessage(), e);
            sendMessage(chatId, "Произошла ошибка при обработке файла.");
        }
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build();
        try {
            execute(message);
        } catch (Exception e) {
            log.error("Ошибка отправки сообщения: {}", e.getMessage(), e);
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }

    @Override
    public String getBotToken() {
        return config.getBotToken();
    }
}