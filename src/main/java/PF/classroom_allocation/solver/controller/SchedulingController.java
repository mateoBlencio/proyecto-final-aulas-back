package PF.classroom_allocation.solver.controller;

import PF.classroom_allocation.solver.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("") // TODO: completar URI
@RequiredArgsConstructor
public class SchedulingController {

    private final SchedulingService schedulingService;

//    @PostMapping("/solve")
//    public ResponseEntity<SchedulingResultDTO> solve(@RequestBody @Valid SchedulingRequestDTO request) {
//        return ResponseEntity.ok(schedulingService.solve(request));
//    }
}
