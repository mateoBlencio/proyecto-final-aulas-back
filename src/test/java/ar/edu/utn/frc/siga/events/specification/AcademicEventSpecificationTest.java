package ar.edu.utn.frc.siga.events.specification;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.events.dto.AcademicEventFilter;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cada test corre con rollback ({@code @DataJpaTest}). Los filtros se acotan a las materias y
 * comisiones creadas por el fixture (ids propios) para no depender de eventos que otros tests
 * de integración hayan dejado commiteados.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration")
@DisplayName("AcademicEventSpecification contra la base")
class AcademicEventSpecificationTest {

    @Autowired private AcademicEventRepository academicEventRepository;
    @Autowired private SpecialtyRepository specialtyRepository;
    @Autowired private StudyPlanRepository studyPlanRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private AcademicPeriodRepository academicPeriodRepository;
    @Autowired private CommissionRepository commissionRepository;

    private Long subjectA;
    private Long subjectB;
    private Long commissionA;
    private Long commissionB;
    private Long recurringAId;
    private Long recurringBId;
    private Long uniqueAId;

    @BeforeEach
    void setUp() {
        Specialty specialty = specialtyRepository.save(
                Specialty.builder().specialtyCode(99017).name("ZZ Spec Sistemas").build());
        StudyPlan plan = studyPlanRepository.save(
                StudyPlan.builder().planCode(99008).specialty(specialty).build());
        subjectA = subjectRepository.save(
                Subject.builder().code(99019).name("ZZ Análisis").term("C").studyPlan(plan).build()).getId();
        subjectB = subjectRepository.save(
                Subject.builder().code(99020).name("ZZ Física").term("C").studyPlan(plan).build()).getId();

        AcademicPeriod period = academicPeriodRepository.save(
                AcademicPeriod.builder().year(2099).semester(1).build());
        commissionA = commissionRepository.save(
                Commission.builder().courseCode("ZZK1").academicPeriod(period).sysacadEnabled(true).build()).getId();
        commissionB = commissionRepository.save(
                Commission.builder().courseCode("ZZK2").academicPeriod(period).sysacadEnabled(true).build()).getId();

        recurringAId = academicEventRepository.save(RecurringEvent.builder()
                .enrolled(30).startTime(LocalTime.of(8, 0)).duration(Duration.ofMinutes(120))
                .subjectId(subjectA).commissionId(commissionA)
                .dayOfWeek(DayOfWeek.MONDAY).startDate(LocalDate.of(2099, 3, 16))
                .build()).getId();
        recurringBId = academicEventRepository.save(RecurringEvent.builder()
                .enrolled(25).startTime(LocalTime.of(10, 0)).duration(Duration.ofMinutes(90))
                .subjectId(subjectB).commissionId(commissionB)
                .dayOfWeek(DayOfWeek.TUESDAY).startDate(LocalDate.of(2099, 3, 17))
                .build()).getId();
        uniqueAId = academicEventRepository.save(UniqueEvent.builder()
                .enrolled(40).startTime(LocalTime.of(14, 0)).duration(Duration.ofMinutes(60))
                .subjectId(subjectA).commissionId(commissionA)
                .date(LocalDate.of(2099, 6, 1)).kind(UniqueEventKind.EXAMEN_FINAL)
                .build()).getId();
    }

    @Test
    void filtraPorSubjectId() {
        var result = academicEventRepository.findAll(
                AcademicEventSpecification.withFilter(new AcademicEventFilter(subjectA, null, null)),
                Pageable.unpaged());
        assertThat(result).extracting("id").containsExactlyInAnyOrder(recurringAId, uniqueAId);
    }

    @Test
    void filtraPorCommissionId() {
        var result = academicEventRepository.findAll(
                AcademicEventSpecification.withFilter(new AcademicEventFilter(null, commissionB, null)),
                Pageable.unpaged());
        assertThat(result).extracting("id").containsExactly(recurringBId);
    }

    @Test
    void filtraPorTypeRecurringResuelveALaClaseConcreta() {
        var result = academicEventRepository.findAll(
                AcademicEventSpecification.withFilter(new AcademicEventFilter(subjectA, null, EventType.RECURRING)),
                Pageable.unpaged());
        assertThat(result).extracting("id").containsExactly(recurringAId);
        assertThat(result).allSatisfy(e -> assertThat(e).isInstanceOf(RecurringEvent.class));
    }

    @Test
    void filtraPorTypeUniqueEvent() {
        var result = academicEventRepository.findAll(
                AcademicEventSpecification.withFilter(new AcademicEventFilter(subjectA, null, EventType.UNIQUE_EVENT)),
                Pageable.unpaged());
        assertThat(result).extracting("id").containsExactly(uniqueAId);
        assertThat(result).allSatisfy(e -> assertThat(e).isInstanceOf(UniqueEvent.class));
    }

    @Test
    void combinaSubjectIdConCommissionId() {
        var result = academicEventRepository.findAll(
                AcademicEventSpecification.withFilter(new AcademicEventFilter(subjectB, commissionA, null)),
                Pageable.unpaged());
        assertThat(result).isEmpty();
    }
}
