package com.example.event_reminder_bot.service.impl;

import com.example.event_reminder_bot.model.entity.Event;
import com.example.event_reminder_bot.repository.EventRepository;
import com.example.event_reminder_bot.service.EventService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;

    @Override
    public Event saveEvent(Event event) {
        return eventRepository.save(event);
    }

    @Override
    public List<Event> getEventsForDate(LocalDate date) {
        return eventRepository.findByEventDateAndIsActive(date, true);
    }

    @Override
    public List<Event> getUpcomingEvents(int daysFromNow) {
        LocalDate today = LocalDate.now();
        LocalDate target = today.plusDays(daysFromNow);
        return eventRepository.findByEventDateAndIsActive(target, true);
    }

    @Override
    @Transactional
    public void importEventsFromExcel(byte[] excelBytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Event> eventsToSave = new ArrayList<>();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // пропустить заголовки

                Cell dateCell = row.getCell(0);
                Cell descriptionCell = row.getCell(1);

                if (dateCell != null && descriptionCell != null) {
                    LocalDate date = dateCell.getLocalDateTimeCellValue().toLocalDate();
                    String description = descriptionCell.getStringCellValue().trim();

                    boolean exists = eventRepository.existsByEventDateAndDescription(date, description);
                    if (!exists) {
                        Event event = new Event();
                        event.setEventDate(date);
                        event.setDescription(description);
                        event.setActive(true);
                        eventsToSave.add(event);
                    }
                }
            }

            if (!eventsToSave.isEmpty()) {
                eventRepository.saveAll(eventsToSave);
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при импорте Excel-файла: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Event> getAllEventsFromDate(LocalDate date) {
        return eventRepository.findAllByEventDateGreaterThanEqualOrderByEventDateAsc(date);
    }
}