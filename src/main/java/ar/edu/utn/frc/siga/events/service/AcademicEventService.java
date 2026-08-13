package ar.edu.utn.frc.siga.events.service;

import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.UpdateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;

import java.util.Collection;
import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface AcademicEventService {
    List<AcademicEventResponseDto> findAll();
    AcademicEventResponseDto findById(Long eventId);

    List<AcademicEventResponseDto> findByIds(Collection<Long> eventIds);
    List<OccurrenceResponseDto> findOccurrencesByEventId(Long eventId);

    AcademicEventResponseDto createRecurringEvent(CreateRecurringEventRequestDto dto);

    FindOrCreateResult<Long> findOrCreateRecurringEvent(CreateRecurringEventRequestDto dto);

    AcademicEventResponseDto createUniqueEvent(CreateUniqueEventRequestDto dto);

    List<AcademicEventResponseDto> findUniqueEvents();

    AcademicEventResponseDto updateUniqueEvent(Long id, UpdateUniqueEventRequestDto dto);
}
