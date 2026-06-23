package ar.edu.utn.frc.classroom_allocation.solver.controller;

import ar.edu.utn.frc.classroom_allocation.solver.dto.request.AllocationRequestDto;
import ar.edu.utn.frc.classroom_allocation.solver.dto.response.AllocationPreviewResponseDto;
import ar.edu.utn.frc.classroom_allocation.solver.service.SolverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/solver")
@RequiredArgsConstructor
@Tag(name = "Solver", description = "Optimal classroom assignment via constraint solver")
public class SolverController {

    private final SolverService solverService;

    @PostMapping("/preview")
    @Operation(summary = "Generate optimal allocation preview",
               description = "Runs the constraint solver and returns suggested classroom assignments without persisting them.")
    public ResponseEntity<AllocationPreviewResponseDto> preview(
            @Valid @RequestBody AllocationRequestDto request) {
        return ResponseEntity.ok(solverService.preview(request));
    }
}
