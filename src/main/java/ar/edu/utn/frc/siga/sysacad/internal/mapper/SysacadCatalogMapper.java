package ar.edu.utn.frc.siga.sysacad.internal.mapper;

import ar.edu.utn.frc.siga.sysacad.api.SysacadAcademicEventDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAllocationDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadBuildingDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadClassroomDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSpecialtyDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectDto;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawAcademicEventMock;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawBuilding;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawClassroom;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSpecialty;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubjectCommission;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadCatalogMapper {

    private static final String YES = "S";
    private static final int MIN_ISO_DAY = DayOfWeek.MONDAY.getValue();
    private static final int MAX_ISO_DAY = DayOfWeek.SUNDAY.getValue();

    public SysacadBuildingDto toBuilding(RawBuilding raw) {
        return new SysacadBuildingDto(raw.edificio(), trim(raw.edificioNombre()));
    }

    public SysacadClassroomDto toClassroom(RawClassroom raw) {
        return new SysacadClassroomDto(
                raw.aula(),
                raw.edificio(),
                YES.equalsIgnoreCase(trim(raw.habilitada())),
                raw.capacidad());
    }

    public SysacadSpecialtyDto toSpecialty(RawSpecialty raw) {
        return new SysacadSpecialtyDto(
                raw.especialid(),
                trim(raw.asEspecialidadNombre()),
                trim(raw.abreviatura()));
    }

    public SysacadCommissionDto toCommission(RawCommission raw) {
        return new SysacadCommissionDto(
                trim(raw.curso()),
                raw.especialid(),
                raw.plan(),
                raw.materia(),
                raw.anoacademi(),
                raw.comision());
    }

    public SysacadSubjectDto toSubject(RawSubject raw, String term) {
        return new SysacadSubjectDto(
                raw.especialid(),
                raw.plan(),
                raw.materia(),
                trim(raw.materiaNombre()),
                term);
    }

    public SysacadSubjectCommissionDto toSubjectCommission(RawSubjectCommission raw) {
        return new SysacadSubjectCommissionDto(
                trim(raw.curso()),
                raw.materia(),
                raw.inscriptos());
    }

    public SysacadSubjectCommissionDto toSubjectCommission(RawSchedule raw) {
        return new SysacadSubjectCommissionDto(
                trim(raw.curso()),
                raw.materia(),
                raw.inscriptos());
    }

    public SysacadAcademicEventDto toAcademicEvent(RawAcademicEventMock raw) {
        String curso = trim(raw.curso());
        DayOfWeek dayOfWeek = parseDayOfWeek(raw.dia(), curso, raw.materia());
        if (dayOfWeek == null) {
            return null;
        }
        LocalTime startTime = parseStartTime(raw.horaComienzo(), curso, raw.materia());
        if (startTime == null) {
            return null;
        }
        return new SysacadAcademicEventDto(
                curso,
                raw.materia(),
                dayOfWeek,
                startTime,
                raw.duracion(),
                raw.horarioCuatrimestre());
    }

    /**
     * Ocurrencia semanal de clase (vista real). Mismo criterio de descarte que
     * {@link #toAcademicEvent(RawAcademicEventMock)} para {@code Dia}/{@code HoraComienzo}.
     */
    public SysacadAcademicEventDto toAcademicEvent(RawSchedule raw) {
        String curso = trim(raw.curso());
        DayOfWeek dayOfWeek = parseDayOfWeek(raw.dia(), curso, raw.materia());
        if (dayOfWeek == null) {
            return null;
        }
        LocalTime startTime = parseStartTime(raw.horaComienzo(), curso, raw.materia());
        if (startTime == null) {
            return null;
        }
        return new SysacadAcademicEventDto(
                curso,
                raw.materia(),
                dayOfWeek,
                startTime,
                raw.duracion(),
                raw.horarioCuatrimestre());
    }


    public SysacadAllocationDto toAllocation(RawSchedule raw) {
        String curso = trim(raw.curso());
        DayOfWeek dayOfWeek = parseDayOfWeek(raw.dia(), curso, raw.materia());
        if (dayOfWeek == null) {
            return null;
        }
        LocalTime startTime = parseStartTime(raw.horaComienzo(), curso, raw.materia());
        if (startTime == null) {
            return null;
        }
        warnIfDurationInconsistent(raw, curso, startTime);
        return new SysacadAllocationDto(
                curso,
                raw.materia(),
                dayOfWeek,
                startTime,
                raw.duracion(),
                raw.horarioCuatrimestre(),
                raw.aula(),
                raw.edificio());
    }

    private DayOfWeek parseDayOfWeek(Integer dia, String curso, Integer materia) {
        if (dia == null || dia < MIN_ISO_DAY || dia > MAX_ISO_DAY) {
            log.warn("Dia fuera de rango ISO-8601 (1..7) para curso={} materia={}: {}", curso, materia, dia);
            return null;
        }
        return DayOfWeek.of(dia);
    }

    private LocalTime parseStartTime(String horaComienzo, String curso, Integer materia) {
        String trimmed = trim(horaComienzo);
        if (trimmed == null || trimmed.isEmpty()) {
            log.warn("HoraComienzo vacía para curso={} materia={}", curso, materia);
            return null;
        }
        try {
            return LocalTime.parse(trimmed);
        } catch (DateTimeParseException e) {
            log.warn("HoraComienzo inválida para curso={} materia={}: '{}'", curso, materia, trimmed);
            return null;
        }
    }

    private void warnIfDurationInconsistent(RawSchedule raw, String curso, LocalTime startTime) {
        String horaFin = trim(raw.horaFin());
        if (raw.duracion() == null || horaFin == null || horaFin.isEmpty()) {
            return;
        }
        try {
            LocalTime endTime = LocalTime.parse(horaFin);
            long computedMinutes = Duration.between(startTime, endTime).toMinutes();
            if (computedMinutes != raw.duracion()) {
                log.warn("DURACION inconsistente para curso={} materia={}: columna={} min, "
                                + "HoraComienzo-HoraFin={} min",
                        curso, raw.materia(), raw.duracion(), computedMinutes);
            }
        } catch (DateTimeParseException e) {
            log.warn("HoraFin inválida para curso={} materia={}: '{}'", curso, raw.materia(), horaFin);
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
