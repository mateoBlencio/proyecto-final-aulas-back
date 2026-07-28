package ar.edu.utn.frc.siga.testsupport;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomTypeRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicLong;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;

/**
 * Seeds idempotentes para tests de integración. No corre {@code data.sql} (perfil
 * {@code integration}, ver {@code AbstractIntegrationTest}), así que lo que otros perfiles dan
 * por sentado (tipo de aula por defecto, etc.) hay que sembrarlo acá.
 *
 * <p>Sin rollback entre tests (Envers necesita commits reales): cada seed que crea una fila usa
 * una clave natural con sufijo único ({@link #nextSeq()}, contador atómico sembrado con
 * {@code nanoTime}) para no colisionar entre tests ni entre clases.
 *
 * <p>{@code @TestConfiguration} en lugar de {@code @Component}: así el classpath scanning de
 * {@code @SpringBootTest} no la recoge sola (queda excluida por el filtro de test-slices);
 * cada test que la necesita la trae explícitamente con {@code @Import(IntegrationTestData.class)}.
 *
 * <p>Commission/SubjectCommission/RecurringEvent son catálogo (find-only, no se crean desde
 * la app): estos seeds los guardan directo o vía {@code AcademicEventService.createRecurringEvent}
 * (para generar también las occurrences), simulando la carga externa que en producción hace el DBA.
 */
@TestConfiguration
@RequiredArgsConstructor
public class IntegrationTestData {

    // % 1_000_000_000L acota a 9 dígitos: nanoTime() crudo (~19 dígitos) rompe columnas angostas
    // como aula.num_aula (varchar(20)) al concatenarle un prefijo ("AULA-" + nextSeq()).
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    private final BuildingRepository buildingRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomTypeRepository classroomTypeRepository;

    private final SpecialtyRepository specialtyRepository;
    private final StudyPlanRepository studyPlanRepository;
    private final SubjectRepository subjectRepository;
    private final CommissionRepository commissionRepository;
    private final SubjectCommissionRepository subjectCommissionRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final AcademicPeriodService academicPeriodService;
    private final AcademicEventService academicEventService;

    @Value("${siga.space.default-classroom-type:Normal}")
    private String defaultClassroomTypeDescription;

    /** Sufijo único para claves naturales (roomNumber, nombre de edificio, etc.). */
    public static long nextSeq() {
        return SEQ.incrementAndGet();
    }

    /**
     * Tipo de aula "Normal" ({@code siga.space.default-classroom-type}), usado como
     * fixture genérico por los tests que necesitan un {@code ClassroomType} válido.
     * El perfil {@code integration} no corre {@code data.sql}, de ahí el seed manual.
     */
    public ClassroomType tipoAulaNormal() {
        return classroomTypeRepository.findByDescriptionIgnoreCase(defaultClassroomTypeDescription)
                .orElseGet(() -> classroomTypeRepository.save(
                        ClassroomType.builder()
                                .description(defaultClassroomTypeDescription)
                                .deleted(false)
                                .build()));
    }

    public Building edificio(String namePrefix, int floorCount, boolean active) {
        return buildingRepository.save(Building.builder()
                .name(namePrefix + "-" + nextSeq())
                .floorCount(floorCount)
                .active(active)
                .deleted(false)
                .build());
    }

    /** Edificio activo con 5 pisos, nombre único. */
    public Building edificio() {
        return edificio("Edificio-IT", 5, true);
    }

    /** Edificio activo con 5 pisos, con el nombre exacto dado (sin sufijo). */
    public Building edificioConNombre(String name) {
        return buildingRepository.save(Building.builder()
                .name(name)
                .floorCount(5)
                .active(true)
                .deleted(false)
                .build());
    }

    public Classroom aula(Building building, ClassroomType tipo, int floor, int capacity, boolean available) {
        return aulaConNumero("AULA-" + nextSeq(), building, tipo, floor, capacity, available);
    }

    /** Aula disponible, piso 1, capacidad 40, con el tipo de aula por defecto, número único. */
    public Classroom aula(Building building) {
        return aula(building, tipoAulaNormal(), 1, 40, true);
    }

    /** Aula con {@code roomNumber} exacto (sin sufijo), para tests que necesitan buscarla por clave natural. */
    public Classroom aulaConNumero(String roomNumber, Building building, ClassroomType tipo, int floor, int capacity, boolean available) {
        return classroomRepository.save(Classroom.builder()
                .roomNumber(roomNumber)
                .floor(floor)
                .capacity(capacity)
                .available(available)
                .deleted(false)
                .building(building)
                .classroomType(tipo)
                .build());
    }

    public Specialty especialidad(int specialtyCode) {
        return specialtyRepository.save(Specialty.builder()
                .specialtyCode(specialtyCode)
                .name("Especialidad-" + specialtyCode)
                .build());
    }

    public StudyPlan planDeEstudio(int planCode, Specialty specialty) {
        return studyPlanRepository.save(StudyPlan.builder()
                .planCode(planCode)
                .specialty(specialty)
                .build());
    }

    public Subject materia(int code, String name, StudyPlan studyPlan, String term) {
        return subjectRepository.save(Subject.builder()
                .code(code)
                .name(name)
                .studyPlan(studyPlan)
                .term(term)
                .build());
    }

    /** Materia + comisión resultantes del seed {@link #materiaYComision()}. */
    public record SubjectAndCommission(Long subjectId, Long commissionId) {}

    /**
     * Siembra Specialty->StudyPlan->Subject (catálogo, guardado directo por repository:
     * no hay findOrCreate para estas entidades) y encadena AcademicPeriod (find-or-create)
     * ->Commission (catálogo, guardado directo) para obtener un par subjectId/commissionId
     * válido, el mínimo que {@code AcademicEventService.createRecurringEvent} necesita
     * (valida existencia de ambos por ID contra las fachadas de {@code academic}). También
     * los vincula vía {@code SubjectCommission} (materiaComision): {@code createUniqueEvent}
     * además valida que la comisión pertenezca a la materia (ver ADR-011), a diferencia del
     * recurrente.
     */
    public SubjectAndCommission materiaYComision() {
        int specialtyCode = (int) nextSeq();
        int planCode = (int) nextSeq();
        int subjectCode = (int) nextSeq();
        Specialty specialty = especialidad(specialtyCode);
        StudyPlan plan = planDeEstudio(planCode, specialty);
        Subject subject = materia(subjectCode, "Materia-IT", plan, TermType.ANUAL.getLabel());

        int year = 2100 + (int) (nextSeq() % 500);
        AcademicPeriod period = periodoAcademico(year, TermType.ANUAL);
        Commission commission = comision("CUR-" + nextSeq(), 1, period);
        materiaComision(subject, commission, 30);

        return new SubjectAndCommission(subject.getId(), commission.getId());
    }

    /** Encuentra o crea el {@link AcademicPeriod} por (year, term) y devuelve la entidad. */
    public AcademicPeriod periodoAcademico(int year, TermType termType) {
        academicPeriodService.findOrCreate(year, termType);
        return academicPeriodRepository.findByYearAndSemester(year, termType.getSemester()).orElseThrow();
    }

    /** Comisión (catálogo): guardado directo por repository, sin find-or-create. */
    public Commission comision(String courseCode, int commissionNumber, AcademicPeriod period) {
        return commissionRepository.save(Commission.builder()
                .courseCode(courseCode)
                .commissionNumber(commissionNumber)
                .yearLevel(1)
                .academicPeriod(period)
                .build());
    }

    /** Materia-comisión (catálogo): guardado directo por repository, sin find-or-create. */
    public SubjectCommission materiaComision(Subject subject, Commission commission, int enrolledCount) {
        return subjectCommissionRepository.save(SubjectCommission.builder()
                .subject(subject)
                .commission(commission)
                .enrolledCount(enrolledCount)
                .build());
    }

    /**
     * Evento recurrente (catálogo): usa {@code AcademicEventService.createRecurringEvent}
     * para generar también sus occurrences, en vez de guardar la entidad a mano.
     */
    public Long eventoRecurrente(Long subjectId, Long commissionId, DayOfWeek dayOfWeek, LocalTime startTime,
            int durationMinutes, LocalDate startDate, LocalDate endDate, int enrolled) {
        CreateRecurringEventRequestDto dto = new CreateRecurringEventRequestDto(
                enrolled, startTime, durationMinutes, dayOfWeek, startDate, endDate, subjectId, commissionId);
        return academicEventService.createRecurringEvent(dto).id();
    }
}
