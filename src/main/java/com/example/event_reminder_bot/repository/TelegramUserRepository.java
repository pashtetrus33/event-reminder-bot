package com.example.event_reminder_bot.repository;

import com.example.event_reminder_bot.model.entity.TelegramUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelegramUserRepository extends JpaRepository<TelegramUser, Long> {

    Optional<TelegramUser> findByTelegramId(Long telegramId);

    boolean existsByTelegramId(Long telegramId);
}