package ar.edu.utn.frc.classroom_allocation.schedule.repository;

import ar.edu.utn.frc.classroom_allocation.schedule.model.TimeSlot;
import java.time.LocalTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    Optional<TimeSlot> findByDiaSemanaAndHoraInicioAndHoraFin(
            String diaSemana, LocalTime horaInicio, LocalTime horaFin);
}
