package ar.edu.utn.frc.siga.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.dto.CommissionFilter;
import ar.edu.utn.frc.siga.academic.dto.SubjectCommissionFilter;
import ar.edu.utn.frc.siga.academic.dto.SubjectFilter;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
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
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;

@Import(IntegrationTestData.class)
@DisplayName("Baja lógica en cascada del módulo academic (integración)")
class AcademicSoftDeleteCascadeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private StudyPlanRepository studyPlanRepository;
    @Autowired
    private AcademicPeriodRepository academicPeriodRepository;
    @Autowired
    private CommissionRepository commissionRepository;
    @Autowired
    private SpecialtyRepository specialtyRepository;
    @Autowired
    private SubjectService subjectService;
    @Autowired
    private CommissionService commissionService;
    @Autowired
    private SubjectCommissionService subjectCommissionService;

    private static final Pageable ALL = Pageable.unpaged();

    private final List<Long> notCurrentCommissionIds = new ArrayList<>();

    @AfterEach
    void restoreSysacadFlags() {
        notCurrentCommissionIds.forEach(id -> setCurrent(id, true));
        notCurrentCommissionIds.clear();
    }

    private void markNotCurrent(Commission commission) {
        Commission fresh = commissionRepository.findById(commission.getId()).orElseThrow();
        fresh.setSysacadEnabled(false);
        fresh.setSysacadHash("hash-" + fresh.getId());
        commissionRepository.save(fresh);
        notCurrentCommissionIds.add(fresh.getId());
    }

    private void setCurrent(Long commissionId, boolean current) {
        Commission fresh = commissionRepository.findById(commissionId).orElseThrow();
        fresh.setSysacadEnabled(current);
        commissionRepository.save(fresh);
    }

    private UnavailableReason reasonOf(Long commissionId, Long periodId) {
        return commissionService.findAll(new CommissionFilter(null, periodId, null, null), ALL, true).getContent().stream()
                .filter(dto -> dto.id().equals(commissionId))
                .findFirst().orElseThrow().unavailableReason();
    }

    private StudyPlan newPlan() {
        Specialty specialty = testData.especialidad((int) IntegrationTestData.nextSeq());
        return testData.planDeEstudio((int) IntegrationTestData.nextSeq(), specialty);
    }

    private AcademicPeriod newPeriod() {
        return testData.periodoAcademico(2100 + (int) (IntegrationTestData.nextSeq() % 500), TermType.ANUAL);
    }

    private List<Long> subjectIds(boolean includeDeactivated, Long studyPlanId) {
        return subjectService.findAll(new SubjectFilter(null, null, null, studyPlanId), ALL, includeDeactivated)
                .getContent().stream().map(SubjectResponseDto::id).toList();
    }

    private List<Long> commissionIds(boolean includeDeactivated, Long periodId) {
        return commissionService.findAll(new CommissionFilter(null, periodId, null, null), ALL, includeDeactivated)
                .getContent().stream().map(CommissionResponseDto::id).toList();
    }

    private List<Long> linkedCommissionIds(boolean includeDeactivated, Long subjectId) {
        return subjectCommissionService.findAll(new SubjectCommissionFilter(subjectId, null), ALL, includeDeactivated)
                .getContent().stream().map(SubjectCommissionResponseDto::commissionId).toList();
    }

    @Test
    @DisplayName("una materia activa de un plan inhabilitado deja de listarse y de resolverse, y vuelve al reactivar el plan")
    void subjectOfInactivePlan_isHiddenAndComesBackWhenPlanIsRestored() {
        StudyPlan plan = newPlan();
        Subject subject = testData.materia((int) IntegrationTestData.nextSeq(), "Materia plan", plan, "Anual");
        Long id = subject.getId();
        assertThat(subjectIds(false, plan.getId())).containsExactly(id);
        assertThat(subjectService.findById(id).id()).isEqualTo(id);

        plan.deactivate();
        plan = studyPlanRepository.save(plan);

        assertThat(subject.isActive()).isTrue();
        assertThat(subjectIds(false, plan.getId())).isEmpty();
        assertThat(subjectIds(true, plan.getId())).containsExactly(id);
        assertThatThrownBy(() -> subjectService.findById(id)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(subjectService.findByIds(List.of(id))).isEmpty();
        assertThat(subjectService.findByIdsIncludingDeactivated(List.of(id)))
                .extracting(SubjectResponseDto::id).containsExactly(id);

        studyPlanRepository.restore(plan);

        assertThat(subjectIds(false, plan.getId())).containsExactly(id);
        assertThat(subjectService.findById(id).id()).isEqualTo(id);
    }

    @Test
    @DisplayName("una comisión activa de un período inhabilitado deja de listarse y de resolverse, y vuelve al reactivar el período")
    void commissionOfInactivePeriod_isHiddenAndComesBackWhenPeriodIsRestored() {
        AcademicPeriod period = newPeriod();
        Commission commission = testData.comision("CAS-" + IntegrationTestData.nextSeq(), period);
        Long id = commission.getId();
        assertThat(commissionIds(false, period.getId())).containsExactly(id);

        period.deactivate();
        academicPeriodRepository.save(period);

        assertThat(commissionIds(false, period.getId())).isEmpty();
        assertThat(commissionIds(true, period.getId())).containsExactly(id);
        assertThatThrownBy(() -> commissionService.findById(id)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(commissionService.findByIds(List.of(id))).isEmpty();
        assertThat(commissionService.findByIdsIncludingDeactivated(List.of(id)))
                .extracting(CommissionResponseDto::id).containsExactly(id);

        academicPeriodRepository.restore(period);

        assertThat(commissionIds(false, period.getId())).containsExactly(id);
        assertThat(commissionService.findById(id).id()).isEqualTo(id);
    }

    @Test
    @DisplayName("un vínculo materia-comisión se oculta si su comisión está inhabilitada y no se puede resolver")
    void subjectCommissionOfInactiveCommission_isHiddenAndNotResolvable() {
        Subject subject = testData.materia((int) IntegrationTestData.nextSeq(), "Materia vínculo", newPlan(), "Anual");
        Commission commission = testData.comision("CAS-" + IntegrationTestData.nextSeq(), newPeriod());
        SubjectCommission link = testData.materiaComision(subject, commission, 30);
        Long subjectId = subject.getId();
        Long commissionId = commission.getId();
        assertThat(linkedCommissionIds(false, subjectId)).containsExactly(commissionId);
        assertThat(subjectCommissionService.findBySubjectAndCommission(subjectId, commissionId)).isNotNull();

        commission.deactivate();
        commission = commissionRepository.save(commission);

        assertThat(link.isActive()).isTrue();
        assertThat(linkedCommissionIds(false, subjectId)).isEmpty();
        assertThat(linkedCommissionIds(true, subjectId)).containsExactly(commissionId);
        assertThatThrownBy(() -> subjectCommissionService.findBySubjectAndCommission(subjectId, commissionId))
                .isInstanceOf(ResourceNotFoundException.class);

        commissionRepository.restore(commission);

        assertThat(linkedCommissionIds(false, subjectId)).containsExactly(commissionId);
    }

    @Test
    @DisplayName("un vínculo materia-comisión se oculta si el plan de su materia está inhabilitado")
    void subjectCommissionOfSubjectInInactivePlan_isHidden() {
        StudyPlan plan = newPlan();
        Subject subject = testData.materia((int) IntegrationTestData.nextSeq(), "Materia plan", plan, "Anual");
        Commission commission = testData.comision("CAS-" + IntegrationTestData.nextSeq(), newPeriod());
        testData.materiaComision(subject, commission, 30);
        Long subjectId = subject.getId();
        assertThat(linkedCommissionIds(false, subjectId)).containsExactly(commission.getId());

        plan.deactivate();
        studyPlanRepository.save(plan);

        assertThat(linkedCommissionIds(false, subjectId)).isEmpty();
        assertThatThrownBy(() -> subjectCommissionService.findBySubjectAndCommission(subjectId, commission.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("un vínculo materia-comisión se oculta si el período de su comisión está inhabilitado")
    void subjectCommissionOfCommissionInInactivePeriod_isHidden() {
        AcademicPeriod period = newPeriod();
        Subject subject = testData.materia((int) IntegrationTestData.nextSeq(), "Materia per", newPlan(), "Anual");
        Commission commission = testData.comision("CAS-" + IntegrationTestData.nextSeq(), period);
        testData.materiaComision(subject, commission, 30);
        Long subjectId = subject.getId();
        assertThat(linkedCommissionIds(false, subjectId)).containsExactly(commission.getId());

        period.deactivate();
        period = academicPeriodRepository.save(period);

        assertThat(linkedCommissionIds(false, subjectId)).isEmpty();
        assertThat(linkedCommissionIds(true, subjectId)).containsExactly(commission.getId());

        academicPeriodRepository.restore(period);
    }

    @Test
    @DisplayName("sin ninguna baja en la cadena, todo sigue visible (control negativo)")
    void fullyActiveChain_staysVisible() {
        Subject subject = testData.materia((int) IntegrationTestData.nextSeq(), "Materia ok", newPlan(), "Anual");
        Commission commission = testData.comision("CAS-" + IntegrationTestData.nextSeq(), newPeriod());
        testData.materiaComision(subject, commission, 30);

        assertThat(linkedCommissionIds(false, subject.getId())).containsExactly(commission.getId());
        assertThat(subjectService.findByIds(List.of(subject.getId()))).hasSize(1);
        assertThat(commissionService.findByIds(List.of(commission.getId()))).hasSize(1);
    }

    @Test
    @DisplayName("una comisión no vigente en SysAcad se lista y se resuelve sin includeDeactivated, y lo informa con currentInSysacad false")
    void commissionNotInSysacad_isListedResolvedAndFlagged() {
        AcademicPeriod period = newPeriod();
        Commission commission = testData.comision("CAS-" + IntegrationTestData.nextSeq(), period);
        Long id = commission.getId();
        assertThat(commissionService.findById(id).currentInSysacad()).isTrue();

        markNotCurrent(commission);

        assertThat(commissionRepository.findById(id).orElseThrow().isActive()).isTrue();
        assertThat(commissionIds(false, period.getId())).containsExactly(id);
        assertThat(commissionIds(true, period.getId())).containsExactly(id);
        assertThat(reasonOf(id, period.getId())).isNull();
        assertThat(commissionService.findById(id).currentInSysacad()).isFalse();
        assertThat(commissionService.findByIds(List.of(id)))
                .extracting(CommissionResponseDto::id).containsExactly(id);
        assertThat(commissionService.findAll(new CommissionFilter(null, period.getId(), null, null), ALL, false)
                .getContent()).singleElement().extracting(CommissionResponseDto::currentInSysacad).isEqualTo(false);
    }

    @Test
    @DisplayName("una comisión no vigente en SysAcad y dada de baja a mano se oculta (la baja manual sigue mandando)")
    void commissionNotInSysacadAndManuallyDeactivated_isHidden() {
        AcademicPeriod period = newPeriod();
        Commission commission = testData.comision("CAS-" + IntegrationTestData.nextSeq(), period);
        markNotCurrent(commission);

        Commission fresh = commissionRepository.findById(commission.getId()).orElseThrow();
        fresh.deactivate();
        fresh = commissionRepository.save(fresh);

        assertThat(commissionIds(false, period.getId())).isEmpty();
        assertThat(reasonOf(fresh.getId(), period.getId())).isEqualTo(UnavailableReason.DEACTIVATED);
        Long id = fresh.getId();
        assertThatThrownBy(() -> commissionService.findById(id)).isInstanceOf(ResourceNotFoundException.class);

        commissionRepository.restore(fresh);
    }

    @Test
    @DisplayName("una materia de un plan colgado de una especialidad stub (sin nombre ni hash) se lista y se resuelve")
    void subjectOfStubSpecialtyPlan_isListedAndResolved() {
        Specialty stub = specialtyRepository.save(Specialty.builder()
                .specialtyCode((int) IntegrationTestData.nextSeq()).build());
        StudyPlan plan = testData.planDeEstudio((int) IntegrationTestData.nextSeq(), stub);
        Subject subject = testData.materia((int) IntegrationTestData.nextSeq(), "Materia stub", plan, "Anual");
        Long id = subject.getId();

        assertThat(stub.getName()).isNull();
        assertThat(stub.isActive()).isTrue();
        assertThat(subjectIds(false, plan.getId())).containsExactly(id);
        assertThat(subjectService.findById(id).id()).isEqualTo(id);
    }

    @Test
    @DisplayName("una comisión cargada a mano (hash nulo, flag apagado) sigue listándose")
    void manualCommission_staysVisible() {
        AcademicPeriod period = newPeriod();
        Commission commission = testData.comision("CAS-" + IntegrationTestData.nextSeq(), period);
        setCurrent(commission.getId(), false);

        assertThat(commissionRepository.findById(commission.getId()).orElseThrow().getSysacadHash()).isNull();
        assertThat(commissionIds(false, period.getId())).containsExactly(commission.getId());
        assertThat(reasonOf(commission.getId(), period.getId())).isNull();
    }

    @Test
    @DisplayName("una comisión no vigente conserva el motivo PARENT_DEACTIVATED si además su período está dado de baja")
    void commissionNotInSysacadInInactivePeriod_reportsParentDeactivated() {
        AcademicPeriod period = newPeriod();
        Commission commission = testData.comision("CAS-" + IntegrationTestData.nextSeq(), period);
        markNotCurrent(commission);

        period.deactivate();
        period = academicPeriodRepository.save(period);

        assertThat(reasonOf(commission.getId(), period.getId())).isEqualTo(UnavailableReason.PARENT_DEACTIVATED);

        academicPeriodRepository.restore(period);
    }

    @Test
    @DisplayName("un vínculo materia-comisión sigue listado y resoluble si su comisión no está vigente en SysAcad")
    void subjectCommissionOfCommissionNotInSysacad_staysVisible() {
        Subject subject = testData.materia((int) IntegrationTestData.nextSeq(), "Materia vig", newPlan(), "Anual");
        Commission commission = testData.comision("CAS-" + IntegrationTestData.nextSeq(), newPeriod());
        testData.materiaComision(subject, commission, 30);
        Long subjectId = subject.getId();
        assertThat(linkedCommissionIds(false, subjectId)).containsExactly(commission.getId());

        markNotCurrent(commission);

        assertThat(linkedCommissionIds(false, subjectId)).containsExactly(commission.getId());
        assertThat(linkedCommissionIds(true, subjectId)).containsExactly(commission.getId());
        assertThat(subjectCommissionService.findBySubjectAndCommission(subjectId, commission.getId())).isNotNull();
    }

    @Test
    @DisplayName("un vínculo materia-comisión con cero inscriptos sigue activo y listado")
    void subjectCommissionWithZeroEnrolled_staysVisible() {
        Subject subject = testData.materia((int) IntegrationTestData.nextSeq(), "Materia cero", newPlan(), "Anual");
        Commission commission = testData.comision("CAS-" + IntegrationTestData.nextSeq(), newPeriod());
        SubjectCommission link = testData.materiaComision(subject, commission, 0);

        assertThat(link.isActive()).isTrue();
        assertThat(linkedCommissionIds(false, subject.getId())).containsExactly(commission.getId());
        assertThat(subjectCommissionService.findAll(new SubjectCommissionFilter(subject.getId(), null), ALL, false)
                .getContent()).singleElement().extracting(SubjectCommissionResponseDto::enrolledCount).isEqualTo(0);
    }
}
