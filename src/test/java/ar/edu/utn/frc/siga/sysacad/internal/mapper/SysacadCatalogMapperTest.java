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
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SysacadCatalogMapper")
class SysacadCatalogMapperTest {

    private final SysacadCatalogMapper mapper = new SysacadCatalogMapper();

    @Test
    @DisplayName("toBuilding: recorta el relleno de espacios del nombre")
    void recortaNombreDeEdificio() {
        SysacadBuildingDto building = mapper.toBuilding(
                new RawBuilding(2, "Edif.Central                            "));

        assertThat(building).isEqualTo(new SysacadBuildingDto(2, "Edif.Central"));
    }

    @Test
    @DisplayName("toClassroom: 'S' habilitada, cualquier otro valor no")
    void traduceHabilitada() {
        assertThat(mapper.toClassroom(new RawClassroom(101, 2, "S", 40)))
                .isEqualTo(new SysacadClassroomDto(101, 2, true, 40));
        assertThat(mapper.toClassroom(new RawClassroom(0, 1, "N", 0)))
                .isEqualTo(new SysacadClassroomDto(0, 1, false, 0));
    }

    @Test
    @DisplayName("toClassroom: habilitada nula se toma como no habilitada")
    void habilitadaNulaEsFalse() {
        assertThat(mapper.toClassroom(new RawClassroom(101, 2, null, 40)).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("toSpecialty: recorta nombre y abreviatura")
    void recortaEspecialidad() {
        SysacadSpecialtyDto specialty = mapper.toSpecialty(new RawSpecialty(
                5,
                "Ingeniería en Sistemas de Información                       ",
                "Ing. Sist. Inf."));

        assertThat(specialty).isEqualTo(
                new SysacadSpecialtyDto(5, "Ingeniería en Sistemas de Información", "Ing. Sist. Inf."));
    }

    @Test
    @DisplayName("toCommission: recorta el código de curso y conserva los códigos numéricos")
    void recortaCodigoDeCurso() {
        SysacadCommissionDto commission = mapper.toCommission(
                new RawCommission("5S1   ", 17, 94, 519, 2026, 10));

        assertThat(commission).isEqualTo(new SysacadCommissionDto("5S1", 17, 94, 519, 2026, 10));
    }

    @Test
    @DisplayName("toSubject: recorta el nombre de la materia y propaga el term recibido")
    void recortaNombreDeMateria() {
        SysacadSubjectDto subject = mapper.toSubject(
                new RawSubject(17, 94, 519, "Análisis Matemático I   "), "1 Cuat.");

        assertThat(subject).isEqualTo(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", "1 Cuat."));
    }

    @Test
    @DisplayName("toSubject: propaga term null tal cual")
    void propagaTermNulo() {
        SysacadSubjectDto subject = mapper.toSubject(
                new RawSubject(17, 94, 519, "Análisis Matemático I"), null);

        assertThat(subject.term()).isNull();
    }

    @Test
    @DisplayName("toSubjectCommission: recorta el código de curso, conserva los códigos numéricos y no propaga comisionDictado")
    void recortaCodigoDeCursoEnSubjectCommission() {
        SysacadSubjectCommissionDto subjectCommission = mapper.toSubjectCommission(
                new RawSubjectCommission("5S1   ", 519, 30, "1"));

        assertThat(subjectCommission).isEqualTo(new SysacadSubjectCommissionDto("5S1", 519, 30));
    }

    @Test
    @DisplayName("toAcademicEvent: mapea Dia a DayOfWeek ISO, recorta el curso y parsea HoraComienzo")
    void mapeaEventoAcademicoValido() {
        SysacadAcademicEventDto event = mapper.toAcademicEvent(
                new RawAcademicEventMock("1C1   ", 101, 3, "18:00", 90, 1));

        assertThat(event).isEqualTo(new SysacadAcademicEventDto(
                "1C1", 101, DayOfWeek.WEDNESDAY, LocalTime.of(18, 0), 90, 1));
    }

    @Test
    @DisplayName("toAcademicEvent: Dia fuera de rango ISO-8601 (1..7) descarta la fila")
    void descartaEventoConDiaFueraDeRango() {
        assertThat(mapper.toAcademicEvent(new RawAcademicEventMock("1C1", 101, 0, "18:00", 90, 1))).isNull();
        assertThat(mapper.toAcademicEvent(new RawAcademicEventMock("1C1", 101, 8, "18:00", 90, 1))).isNull();
        assertThat(mapper.toAcademicEvent(new RawAcademicEventMock("1C1", 101, null, "18:00", 90, 1))).isNull();
    }

    @Test
    @DisplayName("toAcademicEvent: HoraComienzo inválida o vacía descarta la fila")
    void descartaEventoConHoraComienzoInvalida() {
        assertThat(mapper.toAcademicEvent(new RawAcademicEventMock("1C1", 101, 3, "25:99", 90, 1))).isNull();
        assertThat(mapper.toAcademicEvent(new RawAcademicEventMock("1C1", 101, 3, "no-es-hora", 90, 1))).isNull();
        assertThat(mapper.toAcademicEvent(new RawAcademicEventMock("1C1", 101, 3, "", 90, 1))).isNull();
        assertThat(mapper.toAcademicEvent(new RawAcademicEventMock("1C1", 101, 3, null, 90, 1))).isNull();
    }

    @Test
    @DisplayName("toAcademicEvent: DURACION rara (0/negativa) no se rechaza, se propaga tal cual")
    void propagaDuracionRaraSinRechazar() {
        SysacadAcademicEventDto zero = mapper.toAcademicEvent(new RawAcademicEventMock("1C1", 101, 3, "18:00", 0, 1));
        SysacadAcademicEventDto negative = mapper.toAcademicEvent(new RawAcademicEventMock("1C1", 101, 3, "18:00", -15, 1));

        assertThat(zero.durationMinutes()).isZero();
        assertThat(negative.durationMinutes()).isEqualTo(-15);
    }

    @Test
    @DisplayName("toAllocation: mapea horario + aula/edificio de la vista real HorariosComisionesCupos")
    void mapeaAsignacionValida() {
        SysacadAllocationDto allocation = mapper.toAllocation(new RawSchedule(
                "1H90SR", 90, 805, 15, "Edif. Ing.Possetto",
                2, 0, "A", "A",
                "10:30", "12:45", "10:30-12:45", 135,
                5, "Ingeniería en Sistemas de Información", 2008, 115, "Sistemas de Representación", 0));

        assertThat(allocation).isEqualTo(new SysacadAllocationDto(
                "1H90SR", 115, DayOfWeek.TUESDAY, LocalTime.of(10, 30), 135, 0, 805, 15));
    }

    @Test
    @DisplayName("toAllocation: Dia fuera de rango ISO-8601 (1..7) descarta la fila")
    void descartaAsignacionConDiaFueraDeRango() {
        RawSchedule row = new RawSchedule("1H90SR", 90, 805, 15, "Edif. X", 9, 0, "A", "A",
                "10:30", "12:45", "10:30-12:45", 135, 5, "Especialidad", 2008, 115, "Materia", 0);

        assertThat(mapper.toAllocation(row)).isNull();
    }

    @Test
    @DisplayName("toAllocation: HoraComienzo inválida descarta la fila")
    void descartaAsignacionConHoraComienzoInvalida() {
        RawSchedule row = new RawSchedule("1H90SR", 90, 805, 15, "Edif. X", 2, 0, "A", "A",
                "no-es-hora", "12:45", "10:30-12:45", 135, 5, "Especialidad", 2008, 115, "Materia", 0);

        assertThat(mapper.toAllocation(row)).isNull();
    }

    @Test
    @DisplayName("toAllocation: DURACION inconsistente con HoraComienzo/HoraFin no se recalcula, se usa la columna tal cual")
    void noRecalculaDuracionInconsistente() {
        // HoraComienzo-HoraFin da 75 minutos, pero DURACION dice 135: se conserva 135 (no se recalcula, §2).
        RawSchedule row = new RawSchedule("1H90SR", 90, 805, 15, "Edif. X", 2, 0, "A", "A",
                "10:30", "11:45", "10:30-11:45", 135, 5, "Especialidad", 2008, 115, "Materia", 0);

        SysacadAllocationDto allocation = mapper.toAllocation(row);

        assertThat(allocation.durationMinutes()).isEqualTo(135);
    }

    @Test
    @DisplayName("toAcademicEvent(RawSchedule): mapea Dia a DayOfWeek ISO, recorta el curso y parsea HoraComienzo")
    void mapeaEventoAcademicoDesdeSchedule() {
        SysacadAcademicEventDto event = mapper.toAcademicEvent(new RawSchedule(
                "1H90SR", 90, 805, 15, "Edif. Ing.Possetto",
                2, 0, "A", "A",
                "10:30", "12:45", "10:30-12:45", 135,
                5, "Ingeniería en Sistemas de Información", 2008, 115, "Sistemas de Representación", 30));

        assertThat(event).isEqualTo(new SysacadAcademicEventDto(
                "1H90SR", 115, DayOfWeek.TUESDAY, LocalTime.of(10, 30), 135, 0));
    }

    @Test
    @DisplayName("toAcademicEvent(RawSchedule): Dia fuera de rango ISO-8601 (1..7) descarta la fila")
    void descartaEventoDesdeScheduleConDiaFueraDeRango() {
        RawSchedule row = new RawSchedule("1H90SR", 90, 805, 15, "Edif. X", 9, 0, "A", "A",
                "10:30", "12:45", "10:30-12:45", 135, 5, "Especialidad", 2008, 115, "Materia", 30);

        assertThat(mapper.toAcademicEvent(row)).isNull();
    }

    @Test
    @DisplayName("toSubjectCommission(RawSchedule): recorta el curso y toma materia/inscriptos tal cual")
    void mapeaSubjectCommissionDesdeSchedule() {
        SysacadSubjectCommissionDto subjectCommission = mapper.toSubjectCommission(new RawSchedule(
                "1H90SR", 90, 805, 15, "Edif. Ing.Possetto",
                2, 0, "A", "A",
                "10:30", "12:45", "10:30-12:45", 135,
                5, "Ingeniería en Sistemas de Información", 2008, 115, "Sistemas de Representación", 30));

        assertThat(subjectCommission).isEqualTo(new SysacadSubjectCommissionDto("1H90SR", 115, 30));
    }
}
