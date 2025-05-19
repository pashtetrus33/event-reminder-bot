package com.example.event_reminder_bot.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

@Slf4j
@Service
public class GoogleSheetsService {

    @Value("${google.sheets.url}")
    private String sheetsUrl;

    public File downloadXlsxToFile() throws IOException {
        URL url = new URL(sheetsUrl); // загружается из application.yml
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");

        if (connection.getResponseCode() != 200) {
            log.error("Ошибка загрузки файла: HTTP {}", connection.getResponseCode());
            throw new IOException("Ошибка загрузки файла: HTTP " + connection.getResponseCode());
        }

        File tempFile = File.createTempFile("google_sheet_", ".xlsx");
        try (InputStream in = connection.getInputStream();
             OutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        return tempFile;
    }
}