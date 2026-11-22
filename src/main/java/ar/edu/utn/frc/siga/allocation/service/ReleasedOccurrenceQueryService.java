package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.ReleasedOccurrenceItemDto;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

import java.time.DayOfWeek;
import java.time.LocalDate;

@NamedInterface("api")
public interface ReleasedOccurrenceQueryService {

    Page<ReleasedOccurrenceItemDto> findReleased(ReleaseReason reason, LocalDate from, LocalDate to, String q,
                                                 DayOfWeek dayOfWeek, String specialty, Pageable pageable);
}
