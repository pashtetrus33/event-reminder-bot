package com.example.event_reminder_bot.bot;

import com.example.event_reminder_bot.config.SchedulerProperties;
import com.example.event_reminder_bot.config.TelegramBotConfig;
import com.example.event_reminder_bot.model.entity.Event;
import com.example.event_reminder_bot.model.entity.TelegramUser;
import com.example.event_reminder_bot.service.EventService;
import com.example.event_reminder_bot.service.TelegramUserService;
import com.example.event_reminder_bot.service.impl.DynamicSchedulerService;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component("telegramBot")
@RequiredArgsConstructor
@Slf4j
public class EventReminderBot extends TelegramLongPollingBot {

    private final TelegramBotConfig config;
    private final TelegramUserService userService;
    private final EventService eventService;
    private final SchedulerProperties schedulerProperties;
    private final DynamicSchedulerService schedulerService;

    @Value("${bot.access.password}")
    private String correctPassword;

    private final Map<Long, Boolean> waitingForPassword = new HashMap<>(); // Хранение состояния пользователей

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) return;

        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String text = message.getText() != null ? message.getText().trim() : "";

        // 💾 Сохраняем пользователя в базу, если ещё не существует
        userService.findByTelegramId(chatId).ifPresentOrElse(
                user -> log.info("Пользователь уже существует: {}", chatId),
                () -> {
                    TelegramUser newUser = new TelegramUser();
                    newUser.setTelegramId(chatId);
                    newUser.setFirstName(message.getFrom().getFirstName());
                    newUser.setLastName(message.getFrom().getLastName());
                    newUser.setUsername(message.getFrom().getUserName());
                    newUser.setHasAccess(false);
                    userService.registerOrUpdateUser(newUser);
                    log.info("Создан новый пользователь: {}", chatId);
                }
        );

        try {
            // Проверка: если ожидаем пароль
            if (waitingForPassword.containsKey(chatId) && Boolean.TRUE.equals(waitingForPassword.get(chatId))) {
                if (text.equals(correctPassword)) {
                    sendMessage(chatId, "✅ Пароль принят. Добро пожаловать!");
                    waitingForPassword.put(chatId, false);
                    Optional<TelegramUser> byTelegramId = userService.findByTelegramId(chatId);
                    if (byTelegramId.isPresent()) {
                        byTelegramId.get().setHasAccess(true);
                        userService.registerOrUpdateUser(byTelegramId.get());
                    }

                } else {
                    sendMessage(chatId, "❌ Неверный пароль. Попробуйте снова.");
                }
                return;
            }

            // Обработка текстовых команд
            if (message.hasText()) {
                String[] parts = text.split("\\s+");
                String command = parts[0].toLowerCase();

                switch (command) {
                    case "/start":
                        sendMessage(chatId, "👋 Привет! Это бот для оповещений о предстоящих мероприятиях 📅\n🔐 Пожалуйста, введите пароль для продолжения.");
                        waitingForPassword.put(chatId, true);
                        Optional<TelegramUser> byTelegramId = userService.findByTelegramId(chatId);
                        if (byTelegramId.isPresent()) {
                            byTelegramId.get().setHasAccess(false);
                            userService.registerOrUpdateUser(byTelegramId.get());
                        }
                        break;

                    case "/help":
                        sendHelpMessage(chatId);
                        break;

                    case "/all":
                        if (!isUserAuthorized(chatId)) {
                            sendMessage(chatId, "🔒 Для доступа к этой команде, пожалуйста, введите правильный пароль.");
                            return;
                        }
                        showAllEvents(chatId);
                        break;

                    case "/current_days":
                        if (!isUserAuthorized(chatId)) {
                            sendMessage(chatId, "🔒 Для доступа к этой команде, пожалуйста, введите правильный пароль.");
                            return;
                        }
                        showCurrentDays(chatId);
                        break;

                    case "/current_time":
                        if (!isUserAuthorized(chatId)) {
                            sendMessage(chatId, "🔒 Для доступа к этой команде, пожалуйста, введите правильный пароль.");
                            return;
                        }
                        showCurrentNotificationTime(chatId);
                        break;

                    case "/days":
                        if (!isUserAuthorized(chatId)) {
                            sendMessage(chatId, "🔒 Для доступа к этой команде, пожалуйста, введите правильный пароль.");
                            return;
                        }
                        if (parts.length > 1) {
                            try {
                                int days = Integer.parseInt(parts[1]);
                                setNotificationDays(chatId, days);
                            } catch (NumberFormatException e) {
                                sendMessage(chatId, "⚠️ Укажите корректное число дней, например: /days 3");
                            }
                        } else {
                            sendMessage(chatId, "⚠️ Укажите количество дней, например: /days 3");
                        }
                        break;

                    case "/time": {
                        if (!isUserAuthorized(chatId)) {
                            sendMessage(chatId, "🔒 Доступ закрыт");
                            return;
                        }

                        if (parts.length == 3) {
                            try {
                                int hour = Integer.parseInt(parts[1]);
                                int minute = Integer.parseInt(parts[2]);

                                if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                                    sendMessage(chatId, "⚠️ Укажите часы 0-23 и минуты 0-59");
                                    return;
                                }

                                String cron = String.format("0 %d %d * * *", minute, hour);
                                schedulerService.scheduleTask(cron);

                                sendMessage(chatId, "⏰ Новое время оповещений: " + String.format("%02d:%02d", hour, minute));
                            } catch (NumberFormatException e) {
                                sendMessage(chatId, "⚠️ Укажите числа, например: /time 10 30");
                            }
                        } else {
                            sendMessage(chatId, "📌 Формат: /time <часы> <минуты>");
                        }
                    }
                    break;


                    default:
                        sendMessage(chatId, "❓ Неизвестная команда. Напишите /help для списка доступных.");
                }
            }

            // Обработка документов
            if (message.hasDocument()) {
                if (!isUserAuthorized(chatId)) {
                    sendMessage(chatId, "🔒 Для загрузки документов введите правильный пароль.");
                    return;
                }
                handleDocument(message.getDocument(), chatId);
            }

        } catch (Exception e) {
            log.error("Ошибка обработки сообщения от chatId: {}", chatId, e);
            sendMessage(chatId, "❗ Произошла ошибка при обработке вашего запроса.");
        }
    }


    private boolean isUserAuthorized(Long chatId) {
        return !waitingForPassword.containsKey(chatId) || !waitingForPassword.get(chatId);
    }

    private void showCurrentDays(Long chatId) {
        int days = schedulerProperties.getDays();
        sendMessage(chatId, "Сейчас установлено оповещение за " + days + " дня(ей)");
    }

    private void showCurrentNotificationTime(Long chatId) {
        String cron = schedulerService.getCurrentCron();
        String[] parts = cron.split(" ");

        if (parts.length >= 3) {
            try {
                int minute = Integer.parseInt(parts[1]);
                int hour = Integer.parseInt(parts[2]);
                sendMessage(chatId, "⏰ Текущее время оповещений установлено на " + String.format("%02d:%02d", hour, minute));
            } catch (NumberFormatException e) {
                sendMessage(chatId, "⚠️ Ошибка при чтении времени из cron-выражения.");
            }
        } else {
            sendMessage(chatId, "⚠️ Неверный формат cron-выражения.");
        }
    }


    private void setNotificationDays(Long chatId, int days) {
        if (days > 0 && days <= 60) {
            schedulerProperties.setDays(days);
            sendMessage(chatId, "Оповещение будет приходить за " + days + " " + pluralizeDays(days) + " до события. И в день события.");
        } else {
            sendMessage(chatId, "Значение должно быть больше 0 и меньше 60");
        }
    }

    private String pluralizeDays(int days) {
        if (days % 10 == 1 && days % 100 != 11) {
            return "день";
        } else if (days % 10 >= 2 && days % 10 <= 4 && (days % 100 < 10 || days % 100 >= 20)) {
            return "дня";
        } else {
            return "дней";
        }
    }


    private void showAllEvents(Long chatId) {
        List<Event> events = eventService.getAllEventsFromDate(LocalDate.now());

        if (events.isEmpty()) {
            sendMessage(chatId, "🎉 Нет запланированных мероприятий на сегодня и позже.");
            return;
        }

        StringBuilder response = new StringBuilder("📅 Список предстоящих мероприятий:\n\n");
        for (Event event : events) {
            response.append("🔹 ")
                    .append(event.getEventDate())
                    .append(" — ")
                    .append(event.getDescription())
                    .append("\n");
        }

        sendMessage(chatId, response.toString());
    }


    private void processUserAccess(TelegramUser user, Message message, Long chatId) {
        if (user.isHasAccess()) {
            sendMessage(chatId, user.getFirstName() + " Вы уже подписаны на рассылку оповещений.");
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

    public void handleDocument(Document document, Long chatId) {
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

    public void handleDocument(InputStream inputStream, String fileName, Long chatId) {
        if (!fileName.endsWith(".xlsx")) {
            sendMessage(chatId, "Пожалуйста, отправьте Excel файл с расширением .xlsx.");
            return;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            inputStream.transferTo(baos);
            byte[] excelBytes = baos.toByteArray();

            eventService.importEventsFromExcel(excelBytes);

            sendMessage(chatId, "Файл успешно обработан. Мероприятия добавлены.");
        } catch (Exception e) {
            log.error("Ошибка при обработке документа: {}", e.getMessage(), e);
            sendMessage(chatId, "Произошла ошибка при обработке файла.");
        }
    }


    public void sendMessage(Long chatId, String text) {
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

    private void sendHelpMessage(Long chatId) {
        String helpText = """
                🆘 *Доступные команды:*
                
                👉 /start — запустить бота и авторизоваться 🔐
                👉 /help — показать это сообщение ❓
                👉 /all — показать список всех мероприятий 📋
                👉 /days <число> — установить за сколько дней напоминать 🗓️ (например: /days 3)
                👉 /current_days — показать текущую настройку дней 📆
                👉 /current_time — показать текущее время оповещений ⏰
                👉 /time <часы> <минуты> — изменить время оповещений (например: /time 9 30) ⏲️
                
                📎 *Вы также можете загружать Excel файл с расширением .xlsx с мероприятиями после авторизации.*
                """;

        sendMessage(chatId, helpText);
    }
}