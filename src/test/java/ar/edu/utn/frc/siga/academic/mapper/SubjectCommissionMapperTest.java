package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubjectCommissionMapper")
class SubjectCommissionMapperTest {

    private final SubjectCommissionMapperImpl mapper = new SubjectCommissionMapperImpl();

    SubjectCommissionMapperTest() {
        ReflectionTestUtils.setField(mapper, "commissionMapper", Mockito.mock(CommissionMapper.class));
    }

    private static SubjectCommission link(Integer semester) {
        StudyPlan plan = StudyPlan.builder().id(1L).specialty(Specialty.builder().id(1L).build()).build();
        Subject subject = Subject.builder().id(3L).code(55).name("Sistemas de Representación").studyPlan(plan).build();
        Commission commission = Commission.builder().id(9L).courseCode("101").academicPeriod(AcademicPeriod.builder().id(1L).build()).build();
        return SubjectCommission.builder().subject(subject).commission(commission).enrolledCount(30)
                .semester(semester).build();
    }

    @ParameterizedTest(name = "cuatrimestre {0} se expone como term {1}")
    @CsvSource({"0,Anual", "1,1 Cuat.", "2,2 Cuat."})
    @DisplayName("toDto: el cuatrimestre del link se expone como el label de TermType")
    void toDtoMapsSemesterToTermLabel(int semester, String expectedTerm) {
        SubjectCommissionResponseDto dto = mapper.toDto(link(semester));

        assertThat(dto.term()).isEqualTo(expectedTerm);
    }

    @Test
    @DisplayName("toDto: un link sin cuatrimestre expone term null")
    void toDtoNullSemesterGivesNullTerm() {
        assertThat(mapper.toDto(link(null)).term()).isNull();
    }

    @Test
    @DisplayName("toDto: un cuatrimestre fuera de 0/1/2 expone term null en vez de fallar")
    void toDtoUnknownSemesterGivesNullTerm() {
        assertThat(mapper.toDto(link(3)).term()).isNull();
    }

    @Test
    @DisplayName("toDto: el term no pisa el resto de los campos del link")
    void toDtoKeepsOtherFields() {
        SubjectCommissionResponseDto dto = mapper.toDto(link(1));

        assertThat(dto.subjectId()).isEqualTo(3L);
        assertThat(dto.commissionId()).isEqualTo(9L);
        assertThat(dto.enrolledCount()).isEqualTo(30);
        assertThat(dto.enabled()).isTrue();
    }
}
