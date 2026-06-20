package ar.edu.utn.frc.classroom_allocation.allocation.controller;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.service.AcademicEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/events")
@RequiredArgsConstructor
@Tag(name = "Academic Events", description = "Create and manage academic events and their occurrences")
public class AcademicEventController {

    private final AcademicEventService academicEventService;

    @PostMapping("/recurring")
    @Operation(summary = "Create recurring event",
               description = "Creates a weekly recurring event and generates all its occurrences.")
    public ResponseEntity<AcademicEventResponseDto> createRecurring(
            @Valid @RequestBody CreateRecurringEventRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(academicEventService.createRecurringEvent(dto));
    }

    @PostMapping("/unique")
    @Operation(summary = "Create unique event",
               description = "Creates a one-time event and generates its single occurrence.")
    public ResponseEntity<AcademicEventResponseDto> createUnique(
            @Valid @RequestBody CreateUniqueEventRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(academicEventService.createUniqueEvent(dto));
    }
}
