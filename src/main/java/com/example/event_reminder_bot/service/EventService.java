package com.example.event_reminder_bot.service;

import com.example.event_reminder_bot.model.entity.Event;

import java.time.LocalDate;
import java.util.List;

public interface EventService {

    Event saveEvent(Event event);

    List<Event> getEventsForDate(LocalDate date);

    List<Event> getUpcomingEvents(int daysFromNow);

    void importEventsFromExcel(byte[] excelBytes);
}
