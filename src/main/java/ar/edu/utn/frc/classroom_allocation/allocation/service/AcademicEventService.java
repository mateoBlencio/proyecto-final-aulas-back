package ar.edu.utn.frc.classroom_allocation.allocation.service;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AcademicEventResponseDto;

public interface AcademicEventService {
    AcademicEventResponseDto createRecurringEvent(CreateRecurringEventRequestDto dto);
    AcademicEventResponseDto createUniqueEvent(CreateUniqueEventRequestDto dto);
}
