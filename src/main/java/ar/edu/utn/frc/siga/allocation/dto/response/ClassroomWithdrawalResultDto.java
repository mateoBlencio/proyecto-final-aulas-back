package ar.edu.utn.frc.siga.allocation.dto.response;

import java.time.LocalDate;
import java.util.List;

public record ClassroomWithdrawalResultDto(
        List<Consolidated> consolidated,
        List<MirrorRetired> mirrorsRetired,
        List<WithoutAlternative> withoutAlternative
) {

    public record Consolidated(Long occurrenceId, Long eventId, LocalDate date, Long fromClassroomId,
                               Long toClassroomId) {}

    public record MirrorRetired(Long occurrenceId, Long eventId, LocalDate date, Long principalOccurrenceId) {}

    public record WithoutAlternative(Long occurrenceId, Long eventId, LocalDate date) {}
}
