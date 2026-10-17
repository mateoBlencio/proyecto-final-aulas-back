package ar.edu.utn.frc.siga.buildingactivity.service;

import ar.edu.utn.frc.siga.buildingactivity.dto.response.BuildingActivityDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface BuildingActivityService {

    /** Del más nuevo al más viejo, dentro del alcance del usuario; {@code buildingId} fuera de alcance da 403. */
    Page<BuildingActivityDto> find(String user, Long buildingId, LocalDate from, LocalDate to, Pageable pageable);

    long countUnseen(String user);

    void markSeen(String user, Long lastSeenId);
}
