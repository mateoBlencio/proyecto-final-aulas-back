package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.academic.dto.response.HolidayResponseDto;
import ar.edu.utn.frc.siga.academic.model.Holiday;
import ar.edu.utn.frc.siga.academic.repository.HolidayRepository;
import ar.edu.utn.frc.siga.academic.service.HolidayService;
import ar.edu.utn.frc.siga.academic.service.command.HolidaySyncCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HolidayServiceImpl implements HolidayService {

    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private final HolidayRepository holidayRepository;
    private final Clock clock;

    @Override
    public List<HolidayResponseDto> findBetween(LocalDate from, LocalDate to) {
        return holidayRepository.findByDateBetweenOrderByDateAsc(from, to).stream()
                .map(h -> new HolidayResponseDto(h.getDate(), h.getDescription()))
                .toList();
    }

    @Override
    @Transactional
    @AuditOperation("Sincronización de feriados desde SysAcad")
    public int syncHolidays(List<HolidaySyncCommand> commands) {
        LocalDate today = LocalDate.now(clock);
        Instant syncedAt = Instant.now(clock);

        Map<LocalDate, String> incoming = new LinkedHashMap<>();
        for (HolidaySyncCommand command : commands) {
            if (command.date() == null) {
                log.warn("Feriado de SysAcad ignorado por fecha nula");
                continue;
            }
            if (incoming.containsKey(command.date())) {
                log.warn("Feriado de SysAcad repetido, se conserva el primero: {}", command.date());
                continue;
            }
            incoming.put(command.date(), normalizeDescription(command.description()));
        }
        if (incoming.isEmpty()) {
            throw new IllegalStateException(
                    "La vista Feriados no devolvió filas válidas; no se modifica la tabla feriado");
        }

        Map<LocalDate, Holiday> existing = holidayRepository.findAll().stream()
                .collect(Collectors.toMap(Holiday::getDate, Function.identity()));

        int affected = 0;
        for (Map.Entry<LocalDate, String> entry : incoming.entrySet()) {
            LocalDate date = entry.getKey();
            Holiday current = existing.get(date);
            if (current == null) {
                holidayRepository.save(Holiday.builder()
                        .date(date).description(entry.getValue()).syncedAt(syncedAt).build());
                affected++;
            } else if (!date.isBefore(today) && !Objects.equals(current.getDescription(), entry.getValue())) {
                current.setDescription(entry.getValue());
                current.setSyncedAt(syncedAt);
                holidayRepository.save(current);
                affected++;
            }
        }

        for (Holiday holiday : existing.values()) {
            if (!holiday.getDate().isBefore(today) && !incoming.containsKey(holiday.getDate())) {
                log.info("Feriado futuro eliminado por no figurar en SysAcad: {}", holiday.getDate());
                holidayRepository.delete(holiday);
                affected++;
            }
        }
        return affected;
    }

    private static String normalizeDescription(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_DESCRIPTION_LENGTH
                ? trimmed.substring(0, MAX_DESCRIPTION_LENGTH)
                : trimmed;
    }
}
