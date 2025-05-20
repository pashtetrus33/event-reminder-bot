package com.example.event_reminder_bot.config;

import com.example.event_reminder_bot.bot.EventReminderBot;
import com.example.event_reminder_bot.service.impl.GoogleSheetsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;


@Component
@RequiredArgsConstructor
public class DocumentProcessor {

    private final EventReminderBot bot;
    private final GoogleSheetsService googleSheetsService;

    public void processDocument() {
        Long ADMIN_CHAT_ID = 1293578282L;
        try {
            File xlsxFile = googleSheetsService.downloadXlsxToFile();
            try (InputStream inputStream = new FileInputStream(xlsxFile)) {
                bot.handleDocument(inputStream, "google-doc.xlsx", ADMIN_CHAT_ID);
            }
            bot.sendMessage(ADMIN_CHAT_ID, "📄 Документ из Google Docs успешно обработан.");
        } catch (Exception e) {
            bot.sendMessage(ADMIN_CHAT_ID, "❌ Ошибка при обработке документа: " + e.getMessage());
        }
    }
}