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
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.util.List;
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
    private SubjectService subjectService;
    @Autowired
    private CommissionService commissionService;
    @Autowired
    private SubjectCommissionService subjectCommissionService;

    private static final Pageable ALL = Pageable.unpaged();

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
        return commissionService.findAll(new CommissionFilter(null, periodId), ALL, includeDeactivated)
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
}
