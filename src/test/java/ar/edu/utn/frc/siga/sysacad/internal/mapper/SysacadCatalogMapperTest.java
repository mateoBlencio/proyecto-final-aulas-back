package ar.edu.utn.frc.siga.sysacad.internal.mapper;

import ar.edu.utn.frc.siga.sysacad.api.SysacadAcademicEventDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAllocationDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadBuildingDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadClassroomDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSpecialtyDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectDto;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawBuilding;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawClassroom;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSpecialty;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

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
    @DisplayName("toAcademicEvent(RawSchedule): HoraComienzo inválida o vacía descarta la fila")
    void descartaEventoDesdeScheduleConHoraComienzoInvalida() {
        RawSchedule row = new RawSchedule("1H90SR", 90, 805, 15, "Edif. X", 3, 0, "A", "A",
                "no-es-hora", "12:45", "10:30-12:45", 135, 5, "Especialidad", 2008, 115, "Materia", 30);

        assertThat(mapper.toAcademicEvent(row)).isNull();
    }

    @Test
    @DisplayName("toAcademicEvent(RawSchedule): DURACION rara (0/negativa) no se rechaza, se propaga tal cual")
    void propagaDuracionRaraDesdeScheduleSinRechazar() {
        RawSchedule row = new RawSchedule("1H90SR", 90, 805, 15, "Edif. X", 3, 0, "A", "A",
                "18:00", "12:45", "10:30-12:45", -15, 5, "Especialidad", 2008, 115, "Materia", 30);

        SysacadAcademicEventDto event = mapper.toAcademicEvent(row);

        assertThat(event.durationMinutes()).isEqualTo(-15);
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

    private static RawSchedule scheduleWithCourse(String course) {
        return new RawSchedule(course, 90, 805, 15, "Edif. X", 2, 0, "A", "A",
                "10:30", "12:45", "10:30-12:45", 135, 5, "Especialidad", 2008, 115, "Materia", 30);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
    @DisplayName("normalizeCourseCode: quita caracteres inválidos y pasa a mayúsculas")
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            "5D1.|5D1",
            "' 3k1 '|3K1",
            "EIG-VW|EIG-VW",
            "5d1|5D1",
            "'5 D 1'|5D1",
            "5D1/|5D1",
            "-|-"})
    void normalizaCodigoDeCurso(String raw, String expected) {
        assertThat(SysacadCatalogMapper.normalizeCourseCode(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("normalizeCourseCode: vacío tras normalizar devuelve null")
    @ValueSource(strings = {"...", "", "   ", "./ ."})
    @NullSource
    void codigoDeCursoVacioEsNull(String raw) {
        assertThat(SysacadCatalogMapper.normalizeCourseCode(raw)).isNull();
    }

    @Test
    @DisplayName("toCommission: normaliza '5D1.' a '5D1' y ' 3k1 ' a '3K1'")
    void toCommissionNormalizaCodigo() {
        assertThat(mapper.toCommission(new RawCommission("5D1.", 17, 94, 519, 2026, 10)).courseCode())
                .isEqualTo("5D1");
        assertThat(mapper.toCommission(new RawCommission(" 3k1 ", 17, 94, 519, 2026, 10)).courseCode())
                .isEqualTo("3K1");
        assertThat(mapper.toCommission(new RawCommission("EIG-VW", 17, 94, 519, 2026, 10)).courseCode())
                .isEqualTo("EIG-VW");
    }

    @Test
    @DisplayName("toCommission: curso '...' o null devuelve null (fila salteada)")
    void toCommissionSaltea() {
        assertThat(mapper.toCommission(new RawCommission("...", 17, 94, 519, 2026, 10))).isNull();
        assertThat(mapper.toCommission(new RawCommission(null, 17, 94, 519, 2026, 10))).isNull();
    }

    @Test
    @DisplayName("toSubjectCommission: normaliza el curso y saltea '...' o null")
    void toSubjectCommissionNormalizaYSaltea() {
        assertThat(mapper.toSubjectCommission(scheduleWithCourse("5D1.")).courseCode()).isEqualTo("5D1");
        assertThat(mapper.toSubjectCommission(scheduleWithCourse(" 3k1 ")).courseCode()).isEqualTo("3K1");
        assertThat(mapper.toSubjectCommission(scheduleWithCourse("EIG-VW")).courseCode()).isEqualTo("EIG-VW");
        assertThat(mapper.toSubjectCommission(scheduleWithCourse("..."))).isNull();
        assertThat(mapper.toSubjectCommission(scheduleWithCourse(null))).isNull();
    }

    @Test
    @DisplayName("toAcademicEvent: normaliza el curso y saltea '...' o null")
    void toAcademicEventNormalizaYSaltea() {
        assertThat(mapper.toAcademicEvent(scheduleWithCourse("5D1.")).courseCode()).isEqualTo("5D1");
        assertThat(mapper.toAcademicEvent(scheduleWithCourse(" 3k1 ")).courseCode()).isEqualTo("3K1");
        assertThat(mapper.toAcademicEvent(scheduleWithCourse("EIG-VW")).courseCode()).isEqualTo("EIG-VW");
        assertThat(mapper.toAcademicEvent(scheduleWithCourse("..."))).isNull();
        assertThat(mapper.toAcademicEvent(scheduleWithCourse(null))).isNull();
    }

    @Test
    @DisplayName("toAllocation: normaliza el curso y saltea '...' o null")
    void toAllocationNormalizaYSaltea() {
        assertThat(mapper.toAllocation(scheduleWithCourse("5D1.")).courseCode()).isEqualTo("5D1");
        assertThat(mapper.toAllocation(scheduleWithCourse(" 3k1 ")).courseCode()).isEqualTo("3K1");
        assertThat(mapper.toAllocation(scheduleWithCourse("EIG-VW")).courseCode()).isEqualTo("EIG-VW");
        assertThat(mapper.toAllocation(scheduleWithCourse("..."))).isNull();
        assertThat(mapper.toAllocation(scheduleWithCourse(null))).isNull();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
    @DisplayName("cleanText: recorta bordes y colapsa espacios, tabs y saltos internos")
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            "'Edif.  Central'|Edif. Central",
            "'Edif.\t\tCentral'|Edif. Central",
            "'Edif. \t Central'|Edif. Central",
            "'  Edif. Central  '|Edif. Central",
            "'\tEdif. Central\t'|Edif. Central",
            "'Edif.\nCentral'|Edif. Central",
            "Edif. Central|Edif. Central"})
    void limpiaTexto(String raw, String expected) {
        assertThat(SysacadCatalogMapper.cleanText(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("cleanText: null queda null y solo espacios queda vacío")
    void limpiaTextoNuloYVacio() {
        assertThat(SysacadCatalogMapper.cleanText(null)).isNull();
        assertThat(SysacadCatalogMapper.cleanText("  \t ")).isEmpty();
    }

    @Test
    @DisplayName("cleanText: no toca la puntuación ('Ing. Sist. Inf.' queda intacto)")
    void limpiaTextoConservaPuntuacion() {
        assertThat(SysacadCatalogMapper.cleanText("Ing. Sist. Inf.")).isEqualTo("Ing. Sist. Inf.");
    }

    @Test
    @DisplayName("toBuilding, toSpecialty y toSubject: colapsan espacios internos repetidos")
    void colapsaEspaciosEnTextosMapeados() {
        assertThat(mapper.toBuilding(new RawBuilding(2, "Edif.   Central  ")).name()).isEqualTo("Edif. Central");

        SysacadSpecialtyDto specialty = mapper.toSpecialty(new RawSpecialty(5, " Ing.  en   Sistemas ", "Ing.  Sist. Inf."));
        assertThat(specialty.name()).isEqualTo("Ing. en Sistemas");
        assertThat(specialty.abbreviation()).isEqualTo("Ing. Sist. Inf.");

        assertThat(mapper.toSubject(new RawSubject(17, 94, 519, " Análisis\t Matemático   I "), null).name())
                .isEqualTo("Análisis Matemático I");
    }
}
