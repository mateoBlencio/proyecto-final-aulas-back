package ar.edu.utn.frc.classroom_allocation.solver.mapper;

import ar.edu.utn.frc.classroom_allocation.space.dto.response.ClassroomResponseDTO;
import ar.edu.utn.frc.classroom_allocation.solver.dto.request.AllocationRequestDto;
import ar.edu.utn.frc.classroom_allocation.solver.dto.request.EventRequestDto;
import ar.edu.utn.frc.classroom_allocation.solver.dto.response.AllocationPreviewResponseDto;
import ar.edu.utn.frc.classroom_allocation.solver.dto.response.AllocationSummaryDto;
import ar.edu.utn.frc.classroom_allocation.solver.dto.response.AssignmentResultDto;
import ar.edu.utn.frc.classroom_allocation.solver.dto.response.QualityDetailDto;
import ar.edu.utn.frc.classroom_allocation.solver.dto.response.WarningDto;
import ar.edu.utn.frc.classroom_allocation.allocation.model.AllocationQuality;
import ar.edu.utn.frc.classroom_allocation.allocation.model.AllocationStatus;
import ar.edu.utn.frc.classroom_allocation.solver.optimization.impl.ClassAssignment;
import ar.edu.utn.frc.classroom_allocation.solver.optimization.impl.ScheduleSolution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AllocationResponseMapper {

    private final EventMapper eventMapper;

    public AllocationPreviewResponseDto toPreviewResponse(ScheduleSolution solution,
                                                          AllocationRequestDto request,
                                                          long solverDurationMs) {
        Map<String, EventRequestDto> eventsById = request.getEvents().stream()
                .collect(Collectors.toMap(EventRequestDto::getId, e -> e));
        Map<Integer, ClassroomResponseDTO> classroomsById = request.getClassrooms().stream()
                .collect(Collectors.toMap(ClassroomResponseDTO::getId, c -> c));

        List<AssignmentResultDto> assignments = new ArrayList<>();
        List<WarningDto> warnings = new ArrayList<>();

        for (ClassAssignment a : solution.getAssignments()) {
            EventRequestDto eventDto = eventsById.get(a.getEvent().getPlanningId());
            ClassroomResponseDTO classroomDto = a.getClassroom() != null
                    ? classroomsById.get(a.getClassroom().getId()) : null;

            AllocationQuality quality = computeQuality(a);
            QualityDetailDto qualityDetail = classroomDto != null ? buildQualityDetail(a) : null;

            assignments.add(AssignmentResultDto.builder()
                    .event(eventMapper.toSummary(eventDto))
                    .classroom(classroomDto)
                    .quality(quality)
                    .qualityDetail(qualityDetail)
                    .build());

            if (quality == AllocationQuality.UNASSIGNED) {
                warnings.add(WarningDto.builder()
                        .code("NO_CLASSROOM_AVAILABLE")
                        .eventId(a.getEvent().getPlanningId())
                        .message("No hay aulas disponibles sin conflicto para este evento.")
                        .build());
            }
        }

        long assignedCount = assignments.stream().filter(a -> a.getClassroom() != null).count();
        long unassignedCount = assignments.size() - assignedCount;
        AllocationStatus status = resolveStatus(solution, unassignedCount);

        var score = solution.getScore();
        return AllocationPreviewResponseDto.builder()
                .previewId("prev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .snapshotTimestamp(Instant.now())
                .generatedAt(Instant.now())
                .status(status)
                .summary(AllocationSummaryDto.builder()
                        .totalEvents(assignments.size())
                        .assigned((int) assignedCount)
                        .unassigned((int) unassignedCount)
                        .hardScore(score != null ? (int) score.hardScore() : Integer.MIN_VALUE)
                        .softScore(score != null ? (int) score.softScore() : Integer.MIN_VALUE)
                        .solverDurationMs(solverDurationMs)
                        .build())
                .assignments(assignments)
                .warnings(warnings)
                .build();
    }

    private AllocationQuality computeQuality(ClassAssignment a) {
        return AllocationQuality.of(a.getClassroom() != null, a.getOvercrowding(), occupancyRatio(a));
    }

    private QualityDetailDto buildQualityDetail(ClassAssignment a) {
        return QualityDetailDto.builder()
                .overcrowding(a.getOvercrowding())
                .unusedCapacity(a.getUnusedCapacity())
                .occupancyRatio(occupancyRatio(a))
                .build();
    }

    private double occupancyRatio(ClassAssignment a) {
        int total = a.getEvent().getEnrolled() + a.getUnusedCapacity();
        return total > 0 ? (double) a.getEvent().getEnrolled() / total : 0.0;
    }

    private AllocationStatus resolveStatus(ScheduleSolution solution, long unassignedCount) {
        var score = solution.getScore();
        if (score != null && score.hardScore() < 0) return AllocationStatus.INFEASIBLE;
        if (unassignedCount > 0) return AllocationStatus.PARTIAL_SUCCESS;
        return AllocationStatus.SUCCESS;
    }
}
