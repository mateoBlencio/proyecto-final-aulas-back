package ar.edu.utn.frc.classroom_allocation.allocation.controller;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.AssignFromDateRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.AssignOccurrenceRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.service.AllocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/allocations")
@RequiredArgsConstructor
@Tag(name = "Allocations", description = "Manual classroom assignment for specific occurrences")
public class AllocationController {

    private final AllocationService allocationService;

    @PostMapping("/occurrences/{occurrenceId}")
    @Operation(summary = "Assign classroom to occurrence",
               description = "Manually assigns a classroom to a specific occurrence. Fails if the occurrence already has an allocation or has already taken place.")
    public ResponseEntity<AllocationResponseDto> assign(
            @PathVariable Long occurrenceId,
            @Valid @RequestBody AssignOccurrenceRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(allocationService.assign(occurrenceId, dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Reassign classroom",
               description = "Changes the classroom for an existing allocation. Fails if the occurrence has already taken place.")
    public ResponseEntity<AllocationResponseDto> reassign(
            @PathVariable Long id,
            @Valid @RequestBody AssignOccurrenceRequestDto dto) {
        return ResponseEntity.ok(allocationService.reassign(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel allocation",
               description = "Removes a classroom assignment for a specific occurrence. The occurrence itself remains scheduled.")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        allocationService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/from-date")
    @Operation(summary = "Assign classroom from date",
               description = "Assigns a classroom to all future occurrences of a recurring event starting from the given date. Creates new allocations or updates existing ones.")
    public ResponseEntity<List<AllocationResponseDto>> assignFromDate(
            @Valid @RequestBody AssignFromDateRequestDto dto) {
        return ResponseEntity.ok(allocationService.assignFromDate(dto));
    }
}
