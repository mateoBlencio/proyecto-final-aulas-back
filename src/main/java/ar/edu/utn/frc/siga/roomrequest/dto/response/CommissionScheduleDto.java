package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.events.dto.response.ScheduleStatus;
import java.time.LocalDate;
import java.util.List;

/**
 * Cursado de una comisión para el formulario de solicitud: los {@code slots} (día + horario), las
 * {@code dates} de cursado futuras y, por fecha, los horarios de clase de ese día ({@code dateSlots}).
 * {@code emptyReason} explica por qué no hay {@code slots} (ENDED, DISABLED_IN_SYSACAD o NO_SCHEDULE) y es
 * {@code null} cuando los hay.
 */
public record CommissionScheduleDto(
        List<ClassSlotDto> slots,
        List<LocalDate> dates,
        List<ClassDateSlotsDto> dateSlots,
        ScheduleStatus emptyReason
) {}
