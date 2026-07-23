package ar.edu.utn.frc.siga.academic;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.service.SpecialtyService;
import ar.edu.utn.frc.siga.academic.service.StudyPlanService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Specialty/StudyPlan/Subject son catálogo (el import solo busca, no crea): sin
 * endpoint HTTP propio (lo consume {@code excelimport}), se inyectan los services directamente.
 */
@Import(IntegrationTestData.class)
@DisplayName("Academic find-only: Specialty/StudyPlan/Subject (integración)")
class AcademicFindByCodeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private SpecialtyService specialtyService;
    @Autowired
    private StudyPlanService studyPlanService;
    @Autowired
    private SubjectService subjectService;

    @Test
    @DisplayName("cadena Specialty->StudyPlan->Subject existente: cada find devuelve el DTO correspondiente")
    void findByCode_existingChain_returnsDtos() {
        int specialtyCode = (int) IntegrationTestData.nextSeq();
        int planCode = (int) IntegrationTestData.nextSeq();
        int subjectCode = (int) IntegrationTestData.nextSeq();

        Specialty specialty = testData.especialidad(specialtyCode);
        StudyPlan plan = testData.planDeEstudio(planCode, specialty);
        var subject = testData.materia(subjectCode, "Materia IT", plan, "Anual");

        SpecialtyResponseDto specialtyDto = specialtyService.findBySpecialtyCode(specialtyCode);
        StudyPlanResponseDto planDto = studyPlanService.findByPlanCodeAndSpecialtyCode(planCode, specialtyCode);
        SubjectResponseDto subjectDto = subjectService.findByCodeAndStudyPlan(subjectCode, planCode, specialtyCode);

        assertThat(specialtyDto.specialtyCode()).isEqualTo(specialtyCode);
        assertThat(planDto.planCode()).isEqualTo(planCode);
        assertThat(subjectDto.id()).isEqualTo(subject.getId());
    }

    @Test
    @DisplayName("Specialty inexistente: lanza ResourceNotFoundException")
    void findBySpecialtyCode_missing_throwsResourceNotFound() {
        assertThatThrownBy(() -> specialtyService.findBySpecialtyCode(-1))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("StudyPlan inexistente: lanza ResourceNotFoundException")
    void findByPlanCodeAndSpecialtyCode_missing_throwsResourceNotFound() {
        Specialty specialty = testData.especialidad((int) IntegrationTestData.nextSeq());

        assertThatThrownBy(() -> studyPlanService.findByPlanCodeAndSpecialtyCode(-1, specialty.getSpecialtyCode()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Subject inexistente: lanza ResourceNotFoundException")
    void findByCodeAndStudyPlan_missing_throwsResourceNotFound() {
        Specialty specialty = testData.especialidad((int) IntegrationTestData.nextSeq());
        StudyPlan plan = testData.planDeEstudio((int) IntegrationTestData.nextSeq(), specialty);

        assertThatThrownBy(() -> subjectService.findByCodeAndStudyPlan(-1, plan.getPlanCode(), specialty.getSpecialtyCode()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
