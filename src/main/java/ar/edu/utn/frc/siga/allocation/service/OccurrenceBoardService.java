package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceBoardEventDto;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceIssueType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

@NamedInterface("api")
public interface OccurrenceBoardService {

    Page<OccurrenceBoardEventDto> findBoard(LocalDate from, LocalDate to, boolean onlyFuture,
                                            Set<OccurrenceIssueType> issues, String q, DayOfWeek dayOfWeek,
                                            String specialty, Pageable pageable);
}
