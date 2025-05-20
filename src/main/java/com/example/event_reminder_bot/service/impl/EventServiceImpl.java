package com.example.event_reminder_bot.service.impl;

import com.example.event_reminder_bot.model.entity.Event;
import com.example.event_reminder_bot.repository.EventRepository;
import com.example.event_reminder_bot.service.EventService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
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

                try {
                    Cell dateCell = row.getCell(0);
                    Cell descriptionCell = row.getCell(1);

                    LocalDate date = extractLocalDate(dateCell);
                    String description = extractString(descriptionCell);

                    if (date == null && (description == null || description.isBlank())) {
                        log.info("Встречена пустая строка на {}. Завершение обработки.", row.getRowNum());
                        break; // выход из цикла при первой пустой строке
                    }

                    if (date == null || description == null || description.isBlank()) {
                        log.warn("Пропущена строка {}: некорректные данные (дата: {}, описание: '{}')",
                                row.getRowNum(), date, description);
                        continue;
                    }

                    boolean exists = eventRepository.existsByEventDateAndDescription(date, description);
                    if (!exists) {
                        Event event = new Event();
                        event.setEventDate(date);
                        event.setDescription(description);
                        event.setActive(true);
                        eventsToSave.add(event);
                        log.info("Добавлено событие: {} - {}", date, description);
                    } else {
                        log.debug("Событие уже существует: {} - {}", date, description);
                    }
                } catch (Exception rowEx) {
                    log.error("Ошибка при обработке строки {}: {}", row.getRowNum(), rowEx.getMessage(), rowEx);
                }
            }

            if (!eventsToSave.isEmpty()) {
                eventRepository.saveAll(eventsToSave);
                log.info("Сохранено {} новых событий.", eventsToSave.size());
            } else {
                log.info("Нет новых событий для добавления.");
            }
        } catch (Exception e) {
            log.error("Ошибка при импорте Excel-файла: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при импорте Excel-файла: " + e.getMessage(), e);
        }
    }


    private LocalDate extractLocalDate(Cell cell) {
        if (cell == null) return null;

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        return null;
    }

    private String extractString(Cell cell) {
        if (cell == null) return null;

        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> String.valueOf(cell.getNumericCellValue()).trim();
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue()).trim();
                case FORMULA -> {
                    try {
                        yield cell.getStringCellValue().trim();
                    } catch (IllegalStateException e) {
                        yield String.valueOf(cell.getNumericCellValue()).trim();
                    }
                }
                default -> null;
            };
        } catch (Exception e) {
            log.warn("Ошибка при чтении значения ячейки: {}", e.getMessage(), e);
            return null;
        }
    }


    @Override
    public List<Event> getAllEventsFromDate(LocalDate date) {
        return eventRepository.findAllByEventDateGreaterThanEqualOrderByEventDateAsc(date);
    }
}