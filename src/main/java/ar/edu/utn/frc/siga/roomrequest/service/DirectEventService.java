package ar.edu.utn.frc.siga.roomrequest.service;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.roomrequest.direct.dto.CreateDirectEventRequestDto;
import ar.edu.utn.frc.siga.roomrequest.direct.dto.DirectEventDraftDto;
import ar.edu.utn.frc.siga.roomrequest.direct.dto.DirectEventSuggestionRequestDto;
import ar.edu.utn.frc.siga.roomrequest.direct.dto.DirectEventSuggestionResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AllowedClassroomDto;

import java.util.List;

public interface DirectEventService {

    List<AllowedClassroomDto> findAvailableClassrooms(DirectEventDraftDto draft, String actor);

    DirectEventSuggestionResponseDto suggest(DirectEventSuggestionRequestDto request, String actor);

    List<AllocationResponseDto> create(CreateDirectEventRequestDto request, String actor);
}
