package ar.edu.utn.frc.siga.roomrequest.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Cursado de una comisión para el formulario de solicitud: los {@code slots} (día + horario), las
 * {@code dates} de cursado futuras y, por fecha, los horarios de clase de ese día ({@code dateSlots}).
 */
public record CommissionScheduleDto(
        List<ClassSlotDto> slots,
        List<LocalDate> dates,
        List<ClassDateSlotsDto> dateSlots
) {}
