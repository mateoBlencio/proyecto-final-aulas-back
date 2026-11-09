package ar.edu.utn.frc.siga.allocation.service.command;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import java.util.List;
import org.springframework.modulith.NamedInterface;

/**
 * Devuelve cada ocurrencia a un aula con el origen indicado. Con {@code allOrNothing} no escribe nada si
 * alguna fecha tiene el aula ocupada; sin él restaura las libres y devuelve las ocupadas.
 */
@NamedInterface("api")
public record RestoreCommand(List<RestoreItem> items, String observation, boolean allOrNothing) {

    @NamedInterface("api")
    public record RestoreItem(Long occurrenceId, Long classroomId, AllocationSource source) {
    }
}
