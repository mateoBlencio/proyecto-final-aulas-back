package ar.edu.utn.frc.siga.testsupport;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
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

@TestConfiguration
@RequiredArgsConstructor
public class IntegrationTestData {

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

    public static long nextSeq() {
        return SEQ.incrementAndGet();
    }

    public ClassroomType tipoAulaNormal() {
        return classroomTypeRepository.findByDescriptionIgnoreCaseAndDeletedAtIsNull(defaultClassroomTypeDescription)
                .orElseGet(() -> classroomTypeRepository.save(
                        ClassroomType.builder()
                                .description(defaultClassroomTypeDescription)
                                .build()));
    }

    public Building edificio(String namePrefix, boolean active) {
        Building building = Building.builder()
                .name(namePrefix + "-" + nextSeq())
                .build();
        if (!active) {
            building.deactivate();
        }
        return buildingRepository.save(building);
    }

    public Building edificio() {
        return edificio("Edificio-IT", true);
    }

    public Building edificioConNombre(String name) {
        return buildingRepository.save(Building.builder()
                .name(name)
                .build());
    }

    public Classroom aula(Building building, ClassroomType tipo, int capacity) {
        return aulaConNumero((int) nextSeq(), building, tipo, capacity);
    }

    public Classroom aula(Building building) {
        return aula(building, tipoAulaNormal(), 40);
    }

    public Classroom aulaConNumero(Integer roomNumber, Building building, ClassroomType tipo, int capacity) {
        return classroomRepository.save(Classroom.builder()
                .roomNumber(roomNumber)
                .capacity(capacity)
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

    public record SubjectAndCommission(Long subjectId, Long commissionId) {}

    public SubjectAndCommission materiaYComision() {
        int specialtyCode = (int) nextSeq();
        int planCode = (int) nextSeq();
        int subjectCode = (int) nextSeq();
        Specialty specialty = especialidad(specialtyCode);
        StudyPlan plan = planDeEstudio(planCode, specialty);
        Subject subject = materia(subjectCode, "Materia-IT", plan, TermType.ANUAL.getLabel());

        int year = 2100 + (int) (nextSeq() % 500);
        AcademicPeriod period = periodoAcademico(year, TermType.ANUAL);
        Commission commission = comision("CUR-" + nextSeq(), period);
        materiaComision(subject, commission, 30);

        return new SubjectAndCommission(subject.getId(), commission.getId());
    }

    public AcademicPeriod periodoAcademico(int year, TermType termType) {
        academicPeriodService.findOrCreate(year, termType);
        return academicPeriodRepository.findByYearAndSemester(year, termType.getSemester()).orElseThrow();
    }

    public Commission comision(String courseCode, AcademicPeriod period) {
        return commissionRepository.save(Commission.builder()
                .courseCode(courseCode)
                .academicPeriod(period)
                .build());
    }

    public SubjectCommission materiaComision(Subject subject, Commission commission, int enrolledCount) {
        return subjectCommissionRepository.save(SubjectCommission.builder()
                .id(new SubjectCommissionId())
                .subject(subject)
                .commission(commission)
                .enrolledCount(enrolledCount)
                .build());
    }

    public Long eventoRecurrente(Long subjectId, Long commissionId, DayOfWeek dayOfWeek, LocalTime startTime,
            int durationMinutes, LocalDate startDate, LocalDate endDate, int enrolled) {
        CreateRecurringEventRequestDto dto = new CreateRecurringEventRequestDto(
                enrolled, startTime, durationMinutes, dayOfWeek, startDate, endDate, subjectId, commissionId);
        return academicEventService.createRecurringEvent(dto).id();
    }
}
