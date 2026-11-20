package ar.edu.utn.frc.siga.events;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.common.model.SoftDeletableEntity;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.events.repository.SuspendedEventRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vista {@code evento_academico_suspendido} (V68) y {@link SuspendedEventRepository} contra Postgres real.
 * Cada test arma su propia cadena especialidad, plan, materia, comisión y vínculo, con un evento recurrente
 * encima, así que las bajas no se pisan con las de otros tests de la suite.
 */
@Import(IntegrationTestData.class)
@DisplayName("Vista de eventos suspendidos (integración)")
class SuspendedEventViewIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate BAJA = LocalDate.of(2031, 3, 10);

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private SuspendedEventRepository suspendedEventRepository;
    @Autowired
    private SpecialtyRepository specialtyRepository;
    @Autowired
    private StudyPlanRepository studyPlanRepository;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private CommissionRepository commissionRepository;
    @Autowired
    private SubjectCommissionRepository subjectCommissionRepository;
    @Autowired
    private AcademicPeriodRepository academicPeriodRepository;
    @Autowired
    private AcademicEventRepository eventRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private enum Link { SUBJECT, COMMISSION, SUBJECT_COMMISSION, STUDY_PLAN, SPECIALTY }

    private record Chain(Long specialtyId, Long planId, Long subjectId, Long commissionId, Long periodId,
                         Long eventId) {
    }

    private Chain chain() {
        int year = 2100 + (int) (IntegrationTestData.nextSeq() % 500);
        Specialty specialty = testData.especialidad((int) IntegrationTestData.nextSeq());
        StudyPlan plan = testData.planDeEstudio((int) IntegrationTestData.nextSeq(), specialty);
        Subject subject = testData.materia((int) IntegrationTestData.nextSeq(), "Materia-SUSP", plan, "Anual");
        AcademicPeriod period = testData.periodoAcademico(year, ar.edu.utn.frc.siga.academic.model.TermType.ANUAL);
        LocalDate today = LocalDate.now();
        period.setStartDate(today.minusYears(5));
        period.setEndDate(today.plusYears(15));
        period = academicPeriodRepository.save(period);
        Commission commission = testData.comision("CUR-" + IntegrationTestData.nextSeq(), period);
        testData.materiaComision(subject, commission, 30);
        LocalDate monday = LocalDate.now().plusYears(1).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        Long eventId = testData.eventoRecurrente(subject.getId(), commission.getId(), DayOfWeek.MONDAY,
                LocalTime.of(9, 0), 60, monday, monday.plusWeeks(1), 30);
        return new Chain(specialty.getId(), plan.getId(), subject.getId(), commission.getId(), period.getId(), eventId);
    }

    private static Instant at(LocalDate day, LocalTime time) {
        return day.atTime(time).atZone(ARGENTINA).toInstant();
    }

    private static Instant noon(LocalDate day) {
        return at(day, LocalTime.NOON);
    }

    private <T extends SoftDeletableEntity> void deactivate(JpaRepository<T, Long> repository, Long id, Instant when) {
        T entity = repository.findById(id).orElseThrow();
        entity.deactivate(when);
        repository.save(entity);
    }

    private <T extends SoftDeletableEntity> void activate(JpaRepository<T, Long> repository, Long id) {
        T entity = repository.findById(id).orElseThrow();
        entity.activate();
        repository.save(entity);
    }

    private void deactivate(Link link, Chain chain, Instant when) {
        switch (link) {
            case SUBJECT -> deactivate(subjectRepository, chain.subjectId(), when);
            case COMMISSION -> deactivate(commissionRepository, chain.commissionId(), when);
            case SUBJECT_COMMISSION -> deactivate(subjectCommissionRepository, linkId(chain), when);
            case STUDY_PLAN -> deactivate(studyPlanRepository, chain.planId(), when);
            case SPECIALTY -> deactivate(specialtyRepository, chain.specialtyId(), when);
        }
    }

    private void activate(Link link, Chain chain) {
        switch (link) {
            case SUBJECT -> activate(subjectRepository, chain.subjectId());
            case COMMISSION -> activate(commissionRepository, chain.commissionId());
            case SUBJECT_COMMISSION -> activate(subjectCommissionRepository, linkId(chain));
            case STUDY_PLAN -> activate(studyPlanRepository, chain.planId());
            case SPECIALTY -> activate(specialtyRepository, chain.specialtyId());
        }
    }

    private Long linkId(Chain chain) {
        return subjectCommissionRepository.findBySubject_IdAndCommission_Id(chain.subjectId(), chain.commissionId())
                .orElseThrow().getId();
    }

    private Map<Long, LocalDate> suspended() {
        return suspendedEventRepository.findAllSuspendedFrom();
    }

    @ParameterizedTest(name = "baja de {0}")
    @EnumSource(Link.class)
    @DisplayName("la baja manual de cualquier eslabón de la cadena suspende el evento desde el día de la baja y la reactivación lo libera")
    void manualBajaOfAnyLink_suspendsEventFromThatDay_andReactivationReleasesIt(Link link) {
        Chain chain = chain();
        assertThat(suspended()).doesNotContainKey(chain.eventId());

        deactivate(link, chain, noon(BAJA));

        assertThat(suspended()).containsEntry(chain.eventId(), BAJA);
        assertThat(suspendedEventRepository.findIdsAmong(Set.of(chain.eventId()))).containsExactly(chain.eventId());

        activate(link, chain);

        assertThat(suspended()).doesNotContainKey(chain.eventId());
        assertThat(suspendedEventRepository.findIdsAmong(Set.of(chain.eventId()))).isEmpty();
    }

    @Test
    @DisplayName("con dos bajas vigentes en la cadena (materia el día D+3, comisión el día D) la fecha es la más temprana")
    void twoBajas_usesTheEarliestDate() {
        Chain chain = chain();

        deactivate(Link.SUBJECT, chain, noon(BAJA.plusDays(3)));
        assertThat(suspended()).containsEntry(chain.eventId(), BAJA.plusDays(3));
        deactivate(Link.COMMISSION, chain, noon(BAJA));

        assertThat(suspended()).containsEntry(chain.eventId(), BAJA);

        activate(Link.COMMISSION, chain);

        assertThat(suspended()).containsEntry(chain.eventId(), BAJA.plusDays(3));
    }

    @ParameterizedTest(name = "baja a las {0} hora Argentina")
    @CsvSource({"00:30", "01:30", "12:00", "22:30", "23:59"})
    @DisplayName("la fecha de baja es el día de Argentina de la baja, aunque en UTC ya sea el día siguiente (22:30 ART = 01:30 UTC del día siguiente)")
    void fechaBaja_isTheArgentinaDay_notTheUtcDay(LocalTime localTime) {
        Chain chain = chain();
        Instant when = at(BAJA, localTime);

        deactivate(Link.SUBJECT, chain, when);

        String raw = jdbc.queryForObject("SELECT eliminado_en::text FROM materia WHERE id_materia = ?",
                String.class, chain.subjectId());
        assertThat(suspended().get(chain.eventId()))
                .as("eliminado_en guardado como %s para el instante %s", raw, when)
                .isEqualTo(BAJA);
    }

    @Test
    @DisplayName("la baja de un período académico no suspende los eventos de sus comisiones")
    void academicPeriodBaja_doesNotSuspend() {
        Chain chain = chain();

        deactivate(academicPeriodRepository, chain.periodId(), noon(BAJA));

        assertThat(suspended()).doesNotContainKey(chain.eventId());
        activate(academicPeriodRepository, chain.periodId());
    }

    @Test
    @DisplayName("un vínculo materia-comisión sin inscriptos tras el sync no suspende")
    void subjectCommissionWithZeroEnrolled_doesNotSuspend() {
        Chain chain = chain();
        SubjectCommission link = subjectCommissionRepository
                .findBySubject_IdAndCommission_Id(chain.subjectId(), chain.commissionId()).orElseThrow();
        link.setEnrolledCount(0);
        subjectCommissionRepository.save(link);

        assertThat(suspended()).doesNotContainKey(chain.eventId());
    }

    @Test
    @DisplayName("una comisión fuera de SysAcad (habilitado_sysacad = false) no suspende")
    void commissionDroppedFromSysacad_doesNotSuspend() {
        Chain chain = chain();
        Commission commission = commissionRepository.findById(chain.commissionId()).orElseThrow();
        commission.setSysacadEnabled(false);
        commission.setSysacadHash("hash-" + chain.commissionId());
        commissionRepository.save(commission);

        assertThat(suspended()).doesNotContainKey(chain.eventId());
    }

    @Test
    @DisplayName("un evento recurrente fuera de SysAcad (habilitado_sysacad = false) no suspende")
    void eventDroppedFromSysacad_doesNotSuspend() {
        Chain chain = chain();
        jdbc.update("UPDATE evento_academico SET hash_raw = 'h-' || id_evento_academico, habilitado_sysacad = FALSE "
                + "WHERE id_evento_academico = ?", chain.eventId());

        assertThat(suspended()).doesNotContainKey(chain.eventId());
    }

    @Test
    @DisplayName("una especialidad stub (sin hash de SysAcad, activa) no suspende")
    void stubSpecialty_doesNotSuspend() {
        Chain chain = chain();
        Specialty specialty = specialtyRepository.findById(chain.specialtyId()).orElseThrow();
        specialty.setSysacadHash(null);
        specialty.setSyncedAt(Instant.now());
        specialtyRepository.save(specialty);

        assertThat(suspended()).doesNotContainKey(chain.eventId());
    }

    @Test
    @DisplayName("un evento único sin materia ni comisión nunca queda suspendido")
    void uniqueEventWithoutSubjectNorCommission_isNeverSuspended() {
        UniqueEvent unique = eventRepository.save(UniqueEvent.builder()
                .enrolled(20).startTime(LocalTime.of(9, 0)).duration(Duration.ofMinutes(60))
                .date(BAJA.plusYears(1)).description("Sin materia").kind(UniqueEventKind.OTRO).build());

        assertThat(suspended()).doesNotContainKey(unique.getId());
        assertThat(suspendedEventRepository.findIdsAmong(Set.of(unique.getId()))).isEmpty();
    }

    @Test
    @DisplayName("findIdsAmong con lista vacía devuelve vacío sin consultar")
    void findIdsAmong_emptyInput_returnsEmpty() {
        assertThat(suspendedEventRepository.findIdsAmong(Set.of())).isEmpty();
    }
}
