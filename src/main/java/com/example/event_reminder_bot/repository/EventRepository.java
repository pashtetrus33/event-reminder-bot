package com.example.event_reminder_bot.repository;

import com.example.event_reminder_bot.model.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByEventDate(LocalDate date);

    List<Event> findByEventDateBetween(LocalDate from, LocalDate to);

    List<Event> findByEventDateAndIsActive(LocalDate date, boolean isActive);

    boolean existsByEventDateAndDescription(LocalDate date, String description);
}