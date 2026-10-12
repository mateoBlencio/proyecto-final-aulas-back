package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.HolidayResponseDto;
import ar.edu.utn.frc.siga.academic.service.command.HolidaySyncCommand;
import java.time.LocalDate;
import java.util.List;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface HolidayService {

    /** Feriados con fecha entre from y to (inclusive), ordenados por fecha. */
    List<HolidayResponseDto> findBetween(LocalDate from, LocalDate to);

    /**
     * Sincroniza los feriados de SysAcad. Espeja desde hoy en adelante (inserta, actualiza y
     * borra); las fechas pasadas solo se insertan si faltan. Falla si no hay filas válidas.
     * Devuelve la cantidad de filas afectadas.
     */
    int syncHolidays(List<HolidaySyncCommand> commands);
}
