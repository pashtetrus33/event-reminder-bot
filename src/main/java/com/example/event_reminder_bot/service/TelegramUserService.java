package com.example.event_reminder_bot.service;

import com.example.event_reminder_bot.model.entity.TelegramUser;

import java.util.List;
import java.util.Optional;

public interface TelegramUserService {

    TelegramUser registerOrUpdateUser(TelegramUser userFromTelegram);

    Optional<TelegramUser> findByTelegramId(Long telegramId);

    List<TelegramUser> findAll();
}