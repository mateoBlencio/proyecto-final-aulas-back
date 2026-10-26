package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationSearchResultDto;
import java.time.LocalDate;
import java.util.List;

public interface AllocationSearchService {

    List<AllocationSearchResultDto> search(String q, LocalDate date, int limit);
}
