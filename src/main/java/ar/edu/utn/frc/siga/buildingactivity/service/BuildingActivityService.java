package ar.edu.utn.frc.siga.buildingactivity.service;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.buildingactivity.dto.response.BuildingActivityDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface BuildingActivityService {

    /**
     * Dentro del alcance del usuario; {@code buildingId} fuera de alcance da 403. Sin sort, del más nuevo al más viejo;
     * un campo de sort no permitido da 400. {@code actor} es coincidencia parcial sin distinguir mayúsculas.
     */
    Page<BuildingActivityDto> find(String user, Long buildingId, LocalDate from, LocalDate to,
                                   AllocationSource source, Boolean seen, String actor, Pageable pageable);

    long countUnseen(String user);

    void markSeen(String user, Long lastSeenId);
}
