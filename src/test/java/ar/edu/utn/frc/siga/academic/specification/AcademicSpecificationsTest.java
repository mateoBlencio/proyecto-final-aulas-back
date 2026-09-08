package ar.edu.utn.frc.siga.academic.specification;

import ar.edu.utn.frc.siga.academic.dto.AcademicPeriodFilter;
import ar.edu.utn.frc.siga.academic.dto.CommissionFilter;
import ar.edu.utn.frc.siga.academic.dto.SpecialtyFilter;
import ar.edu.utn.frc.siga.academic.dto.StudyPlanFilter;
import ar.edu.utn.frc.siga.academic.dto.SubjectCommissionFilter;
import ar.edu.utn.frc.siga.academic.dto.SubjectFilter;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.common.repository.SoftDeleteSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cada test corre con rollback ({@code @DataJpaTest}). El fixture usa códigos y un año
 * distintivos (rango 99xxx / 2099) y todas las aserciones son por id/membresía, de modo que
 * los datos que otros tests de integración dejen commiteados no afectan el resultado.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration")
@DisplayName("Specifications de académico contra la base")
class AcademicSpecificationsTest {

    @Autowired private SpecialtyRepository specialtyRepository;
    @Autowired private StudyPlanRepository studyPlanRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private AcademicPeriodRepository academicPeriodRepository;
    @Autowired private CommissionRepository commissionRepository;
    @Autowired private SubjectCommissionRepository subjectCommissionRepository;

    private Specialty sistemas;
    private Specialty civil;
    private Long plan2008Id;
    private Long plan2023Id;
    private Long analisisId;
    private Long fisicaId;
    private Long period2099s1Id;
    private Long period2099s2Id;
    private Long commissionK1Id;
    private Long commissionK2Id;

    @BeforeEach
    void setUp() {
        sistemas = specialtyRepository.save(Specialty.builder().specialtyCode(99017).name("ZZ Sistemas").build());
        civil = specialtyRepository.save(Specialty.builder().specialtyCode(99005).name("ZZ Civil").build());

        StudyPlan sistemas2008 = studyPlanRepository.save(
                StudyPlan.builder().planCode(99008).specialty(sistemas).build());
        StudyPlan sistemas2023 = studyPlanRepository.save(
                StudyPlan.builder().planCode(99023).specialty(sistemas).build());
        plan2008Id = sistemas2008.getId();
        plan2023Id = sistemas2023.getId();

        Subject analisis = subjectRepository.save(Subject.builder()
                .code(99019).name("ZZ Análisis Matemático I").term("C").studyPlan(sistemas2008).build());
        Subject fisica = subjectRepository.save(Subject.builder()
                .code(99020).name("ZZ Física I").term("C").studyPlan(sistemas2023).build());
        analisisId = analisis.getId();
        fisicaId = fisica.getId();

        AcademicPeriod period1 = academicPeriodRepository.save(
                AcademicPeriod.builder().year(2099).semester(1).build());
        AcademicPeriod period2 = academicPeriodRepository.save(
                AcademicPeriod.builder().year(2099).semester(2).build());
        period2099s1Id = period1.getId();
        period2099s2Id = period2.getId();

        Commission k1 = commissionRepository.save(Commission.builder()
                .courseCode("ZZK1001").academicPeriod(period1).sysacadEnabled(true).build());
        Commission k2 = commissionRepository.save(Commission.builder()
                .courseCode("ZZK2002").academicPeriod(period2).sysacadEnabled(true).build());
        commissionK1Id = k1.getId();
        commissionK2Id = k2.getId();

        subjectCommissionRepository.save(SubjectCommission.builder()
                .id(new SubjectCommissionId(analisisId, commissionK1Id))
                .subject(analisis).commission(k1).enrolledCount(30).build());
        subjectCommissionRepository.save(SubjectCommission.builder()
                .id(new SubjectCommissionId(fisicaId, commissionK2Id))
                .subject(fisica).commission(k2).enrolledCount(25).build());
    }

    @Nested
    @DisplayName("StudyPlanSpecification")
    class StudyPlanSpec {
        @Test
        void filtraPorPlanCode() {
            var result = studyPlanRepository.findAll(
                    StudyPlanSpecification.withFilter(new StudyPlanFilter(99008, null)), Pageable.unpaged());
            assertThat(result).extracting(StudyPlan::getId).containsExactly(plan2008Id);
        }

        @Test
        void filtraPorSpecialtyCodeNavegandoLaRelacion() {
            var result = studyPlanRepository.findAll(
                    StudyPlanSpecification.withFilter(new StudyPlanFilter(null, 99017)), Pageable.unpaged());
            assertThat(result).extracting(StudyPlan::getId).containsExactlyInAnyOrder(plan2008Id, plan2023Id);
        }
    }

    @Nested
    @DisplayName("SubjectSpecification")
    class SubjectSpec {
        @Test
        void filtraPorCodeExacto() {
            var result = subjectRepository.findAll(
                    SubjectSpecification.withFilter(new SubjectFilter(99019, null, null, null))
                            .and(SoftDeleteSpecifications.activeUnless(false)),
                    Pageable.unpaged());
            assertThat(result).extracting(Subject::getId).containsExactly(analisisId);
        }

        @Test
        void filtraPorNameParcialSinDistinguirMayusculas() {
            var result = subjectRepository.findAll(
                    SubjectSpecification.withFilter(new SubjectFilter(null, "zz física", null, null))
                            .and(SoftDeleteSpecifications.activeUnless(false)),
                    Pageable.unpaged());
            assertThat(result).extracting(Subject::getId).containsExactly(fisicaId);
        }

        @Test
        void filtraPorSpecialtyCodeNavegandoStudyPlanSpecialty() {
            var result = subjectRepository.findAll(
                    SubjectSpecification.withFilter(new SubjectFilter(null, null, 99017, null))
                            .and(SoftDeleteSpecifications.activeUnless(false)),
                    Pageable.unpaged());
            assertThat(result).extracting(Subject::getId).containsExactlyInAnyOrder(analisisId, fisicaId);
        }

        @Test
        void filtraPorStudyPlanId() {
            var result = subjectRepository.findAll(
                    SubjectSpecification.withFilter(new SubjectFilter(null, null, null, plan2023Id))
                            .and(SoftDeleteSpecifications.activeUnless(false)),
                    Pageable.unpaged());
            assertThat(result).extracting(Subject::getId).containsExactly(fisicaId);
        }

        @Test
        void activeUnlessOcultaLasDesactivadasSalvoQueSePidanExplicitamente() {
            Subject analisis = subjectRepository.findById(analisisId).orElseThrow();
            analisis.deactivate();
            subjectRepository.save(analisis);

            var soloActivas = subjectRepository.findAll(
                    SubjectSpecification.withFilter(new SubjectFilter(null, null, 99017, null))
                            .and(SoftDeleteSpecifications.activeUnless(false)),
                    Pageable.unpaged());
            var todas = subjectRepository.findAll(
                    SubjectSpecification.withFilter(new SubjectFilter(null, null, 99017, null))
                            .and(SoftDeleteSpecifications.activeUnless(true)),
                    Pageable.unpaged());

            assertThat(soloActivas).extracting(Subject::getId).containsExactly(fisicaId);
            assertThat(todas).extracting(Subject::getId).containsExactlyInAnyOrder(analisisId, fisicaId);
        }
    }

    @Nested
    @DisplayName("CommissionSpecification")
    class CommissionSpec {
        @Test
        void filtraPorCourseCodeParcial() {
            var result = commissionRepository.findAll(
                    CommissionSpecification.withFilter(new CommissionFilter("zzk10", null))
                            .and(SoftDeleteSpecifications.activeUnless(false)),
                    Pageable.unpaged());
            assertThat(result).extracting(Commission::getId).containsExactly(commissionK1Id);
        }

        @Test
        void filtraPorAcademicPeriodId() {
            var result = commissionRepository.findAll(
                    CommissionSpecification.withFilter(new CommissionFilter(null, period2099s2Id))
                            .and(SoftDeleteSpecifications.activeUnless(false)),
                    Pageable.unpaged());
            assertThat(result).extracting(Commission::getId).containsExactly(commissionK2Id);
        }
    }

    @Nested
    @DisplayName("SubjectCommissionSpecification")
    class SubjectCommissionSpec {
        @Test
        void filtraPorSubjectId() {
            var result = subjectCommissionRepository.findAll(
                    SubjectCommissionSpecification.withFilter(new SubjectCommissionFilter(analisisId, null)),
                    Pageable.unpaged());
            assertThat(result).extracting(sc -> sc.getId().getCommissionId()).containsExactly(commissionK1Id);
        }

        @Test
        void filtraPorCommissionId() {
            var result = subjectCommissionRepository.findAll(
                    SubjectCommissionSpecification.withFilter(new SubjectCommissionFilter(null, commissionK2Id)),
                    Pageable.unpaged());
            assertThat(result).extracting(sc -> sc.getId().getSubjectId()).containsExactly(fisicaId);
        }
    }

    @Nested
    @DisplayName("AcademicPeriodSpecification")
    class AcademicPeriodSpec {
        @Test
        void filtraPorYear() {
            var result = academicPeriodRepository.findAll(
                    AcademicPeriodSpecification.withFilter(new AcademicPeriodFilter(2099, null)),
                    Pageable.unpaged());
            assertThat(result).extracting(AcademicPeriod::getId)
                    .containsExactlyInAnyOrder(period2099s1Id, period2099s2Id);
        }

        @Test
        void filtraPorYearYSemester() {
            var result = academicPeriodRepository.findAll(
                    AcademicPeriodSpecification.withFilter(new AcademicPeriodFilter(2099, 2)),
                    Pageable.unpaged());
            assertThat(result).extracting(AcademicPeriod::getId).containsExactly(period2099s2Id);
        }
    }

    @Nested
    @DisplayName("SpecialtySpecification")
    class SpecialtySpec {
        @Test
        void filtraPorSpecialtyCode() {
            var result = specialtyRepository.findAll(
                    SpecialtySpecification.withFilter(new SpecialtyFilter(99005, null)), Pageable.unpaged());
            assertThat(result).extracting(Specialty::getId).containsExactly(civil.getId());
        }

        @Test
        void filtraPorNameParcial() {
            var result = specialtyRepository.findAll(
                    SpecialtySpecification.withFilter(new SpecialtyFilter(null, "zz sistemas")), Pageable.unpaged());
            assertThat(result).extracting(Specialty::getId).containsExactly(sistemas.getId());
        }
    }
}
