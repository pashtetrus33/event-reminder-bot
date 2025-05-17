package com.example.event_reminder_bot.service.impl;

import com.example.event_reminder_bot.model.entity.TelegramUser;
import com.example.event_reminder_bot.repository.TelegramUserRepository;
import com.example.event_reminder_bot.service.TelegramUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TelegramUserServiceImpl implements TelegramUserService {

    private final TelegramUserRepository userRepository;

    @Override
    public TelegramUser registerOrUpdateUser(TelegramUser userFromTelegram) {
        return userRepository.findByTelegramId(userFromTelegram.getTelegramId())
                .map(existing -> {
                    existing.setUsername(userFromTelegram.getUsername());
                    existing.setFirstName(userFromTelegram.getFirstName());
                    existing.setLastName(userFromTelegram.getLastName());
                    existing.setLanguageCode(userFromTelegram.getLanguageCode());
                    existing.setBot(userFromTelegram.isBot());
                    existing.setUpdatedAt(userFromTelegram.getUpdatedAt());
                    existing.setHasAccess(userFromTelegram.isHasAccess());
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(userFromTelegram));
    }

    @Override
    public Optional<TelegramUser> findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    @Override
    public List<TelegramUser> findAll() {
        return userRepository.findAll();
    }
}