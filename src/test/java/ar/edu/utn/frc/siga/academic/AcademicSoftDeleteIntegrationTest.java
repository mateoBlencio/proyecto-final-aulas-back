package ar.edu.utn.frc.siga.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(IntegrationTestData.class)
@DisplayName("Academic soft-delete (integración)")
class AcademicSoftDeleteIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private AcademicPeriodRepository academicPeriodRepository;

    @Autowired
    private SubjectService subjectService;

    @Test
    @DisplayName("un Subject borrado desaparece de los finders de negocio y se puede re-leer/restaurar por id")
    void softDeletedSubject_isHiddenFromBusinessFindersAndCanBeRestored() {
        Specialty specialty = testData.especialidad((int) IntegrationTestData.nextSeq());
        StudyPlan plan = testData.planDeEstudio((int) IntegrationTestData.nextSeq(), specialty);
        Subject subject = testData.materia((int) IntegrationTestData.nextSeq(), "Materia IT", plan, "Anual");
        Long id = subject.getId();
        Integer code = subject.getCode();

        assertThat(subjectRepository.findByCodeAndStudyPlanAndDeletedAtIsNull(code, plan)).isPresent();
        assertThat(subjectService.findAll()).anyMatch(dto -> dto.id().equals(id));

        subject.deactivate();
        subjectRepository.save(subject);

        assertThat(subjectRepository.findByCodeAndStudyPlanAndDeletedAtIsNull(code, plan)).isEmpty();
        assertThat(subjectService.findAll()).noneMatch(dto -> dto.id().equals(id));
        assertThatThrownBy(() -> subjectService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);

        Subject deleted = subjectRepository.findById(id).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();

        subjectRepository.restore(deleted);

        assertThat(subjectRepository.findByCodeAndStudyPlanAndDeletedAtIsNull(code, plan)).isPresent();
        assertThat(subjectService.findAll()).anyMatch(dto -> dto.id().equals(id));
        SubjectResponseDto restored = subjectService.findById(id);
        assertThat(restored.id()).isEqualTo(id);
    }

    @Test
    @DisplayName("un AcademicPeriod borrado sale de findAllActive() pero se re-lee por id y se restaura")
    void softDeletedAcademicPeriod_isHiddenFromFindAllActiveAndCanBeRestored() {
        int year = 2100 + (int) (IntegrationTestData.nextSeq() % 500);
        AcademicPeriod period = testData.periodoAcademico(year, TermType.ANUAL);
        Long id = period.getId();

        assertThat(academicPeriodRepository.findAllActive()).anyMatch(p -> p.getId().equals(id));

        period.deactivate();
        academicPeriodRepository.save(period);

        assertThat(academicPeriodRepository.findAllActive()).noneMatch(p -> p.getId().equals(id));

        AcademicPeriod deleted = academicPeriodRepository.findById(id).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();

        academicPeriodRepository.restore(deleted);

        assertThat(academicPeriodRepository.findAllActive()).anyMatch(p -> p.getId().equals(id));
    }
}
