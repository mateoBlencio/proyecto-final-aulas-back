package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;

import java.util.List;

public interface AcademicEventService {
    List<AcademicEventResponseDto> findAll();
    AcademicEventResponseDto findById(Long eventId);
    List<OccurrenceResponseDto> findOccurrencesByEventId(Long eventId);
    AcademicEventResponseDto createRecurringEvent(CreateRecurringEventRequestDto dto);
    FindOrCreateResult<AcademicEventResponseDto> findOrCreateRecurringEvent(CreateRecurringEventRequestDto dto);
    AcademicEventResponseDto createUniqueEvent(CreateUniqueEventRequestDto dto);
}
