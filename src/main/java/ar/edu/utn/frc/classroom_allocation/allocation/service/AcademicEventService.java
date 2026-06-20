package ar.edu.utn.frc.classroom_allocation.allocation.service;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.OccurrenceResponseDto;

import java.util.List;

public interface AcademicEventService {
    AcademicEventResponseDto findById(Long eventId);
    List<OccurrenceResponseDto> findOccurrencesByEventId(Long eventId);
    AcademicEventResponseDto createRecurringEvent(CreateRecurringEventRequestDto dto);
    AcademicEventResponseDto createUniqueEvent(CreateUniqueEventRequestDto dto);
}
