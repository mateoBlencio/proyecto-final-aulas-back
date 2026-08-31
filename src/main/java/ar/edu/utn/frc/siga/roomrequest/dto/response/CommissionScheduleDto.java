package ar.edu.utn.frc.siga.roomrequest.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Cursado de una comisión para el formulario de solicitud: los {@code slots} (día + horario) y las
 * {@code dates} de cursado futuras (calendario del cambio de aula "por única vez").
 */
public record CommissionScheduleDto(
        List<CursadoSlotDto> slots,
        List<LocalDate> dates
) {}
