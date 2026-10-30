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
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionCountSearch;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.common.repository.SoftDeleteSpecifications;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
@org.springframework.context.annotation.Import(CommissionCountSearch.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration")
@DisplayName("Specifications de académico contra la base")
class AcademicSpecificationsTest {

    @Autowired private CommissionCountSearch commissionCountSearch;
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

        StudyPlan civilDesactivado = studyPlanRepository.save(
                StudyPlan.builder().planCode(99099).specialty(civil).build());
        subjectRepository.save(Subject.builder()
                .code(99098).name("ZZ Estática").term("C").studyPlan(civilDesactivado).build());
        studyPlanRepository.softDelete(civilDesactivado);

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
                .subject(analisis).commission(k1).enrolledCount(30).build());
        subjectCommissionRepository.save(SubjectCommission.builder()
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
        void availableOcultaMateriasDeUnaEspecialidadDadaDeBaja() {
            sistemas.deactivate();
            specialtyRepository.save(sistemas);

            var result = subjectRepository.findAll(
                    SubjectSpecification.withFilter(new SubjectFilter(null, null, 99017, null))
                            .and(SubjectSpecification.available()),
                    Pageable.unpaged());

            assertThat(result).isEmpty();
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
                    CommissionSpecification.withFilter(new CommissionFilter("zzk10", null, null, null))
                            .and(SoftDeleteSpecifications.activeUnless(false)),
                    Pageable.unpaged());
            assertThat(result).extracting(Commission::getId).containsExactly(commissionK1Id);
        }

        @Test
        void filtraPorAcademicPeriodId() {
            var result = commissionRepository.findAll(
                    CommissionSpecification.withFilter(new CommissionFilter(null, period2099s2Id, null, null))
                            .and(SoftDeleteSpecifications.activeUnless(false)),
                    Pageable.unpaged());
            assertThat(result).extracting(Commission::getId).containsExactly(commissionK2Id);
        }

        @Test
        void filtraPorSpecialtyCodeViaMateriasOfrecidas() {
            var result = commissionRepository.findAll(
                    CommissionSpecification.withFilter(new CommissionFilter(null, null, 99017, null))
                            .and(SoftDeleteSpecifications.activeUnless(false)),
                    Pageable.unpaged());
            assertThat(result).extracting(Commission::getId)
                    .containsExactlyInAnyOrder(commissionK1Id, commissionK2Id);
        }

        @Test
        void filtraPorSubjectId() {
            var result = commissionRepository.findAll(
                    CommissionSpecification.withFilter(new CommissionFilter(null, null, null, analisisId))
                            .and(SoftDeleteSpecifications.activeUnless(false)),
                    Pageable.unpaged());
            assertThat(result).extracting(Commission::getId).containsExactly(commissionK1Id);
        }

        @Test
        void specialtyCodeYSubjectIdSeCombinanConAnd() {
            var result = commissionRepository.findAll(
                    CommissionSpecification.withFilter(new CommissionFilter(null, null, 99005, analisisId))
                            .and(SoftDeleteSpecifications.activeUnless(false)),
                    Pageable.unpaged());
            assertThat(result).isEmpty();
        }

        @Test
        void withoutSpecialtyDevuelveSoloComisionesSinMateriaActivaOfrecida() {
            AcademicPeriod period = academicPeriodRepository.findById(period2099s1Id).orElseThrow();
            Commission huerfana = commissionRepository.save(Commission.builder()
                    .courseCode("ZZK3003").academicPeriod(period).sysacadEnabled(true).build());
            Commission soloBaja = commissionRepository.save(Commission.builder()
                    .courseCode("ZZK4004").academicPeriod(period).sysacadEnabled(true).build());
            Subject analisis = subjectRepository.findById(analisisId).orElseThrow();
            SubjectCommission link = subjectCommissionRepository.save(SubjectCommission.builder()
                    .subject(analisis).commission(soloBaja).enrolledCount(10).build());
            subjectCommissionRepository.softDelete(link);

            var result = commissionRepository.findAll(
                    CommissionSpecification.withFilter(
                                    new CommissionFilter(null, null, null, null, true))
                            .and(SoftDeleteSpecifications.activeUnless(false)),
                    Pageable.unpaged());

            assertThat(result).extracting(Commission::getId)
                    .contains(huerfana.getId(), soloBaja.getId())
                    .doesNotContain(commissionK1Id, commissionK2Id);
        }
    }

    @Nested
    @DisplayName("Comisiones ofrecidas por un plan o especialidad dados de baja")
    class CommissionOfDeactivatedChain {
        private Long commissionOnlyInDeactivatedPlanId;

        @BeforeEach
        void linkCommissionToDeactivatedPlanSubject() {
            Subject estatica = subjectRepository.findAll().stream()
                    .filter(subject -> Integer.valueOf(99098).equals(subject.getCode())).findFirst().orElseThrow();
            AcademicPeriod period = academicPeriodRepository.findById(period2099s1Id).orElseThrow();
            Commission commission = commissionRepository.save(Commission.builder()
                    .courseCode("ZZK5005").academicPeriod(period).sysacadEnabled(true).build());
            subjectCommissionRepository.save(SubjectCommission.builder()
                    .subject(estatica).commission(commission).enrolledCount(10).build());
            commissionOnlyInDeactivatedPlanId = commission.getId();
        }

        private List<Long> ids(CommissionFilter filter, boolean includeDeactivated) {
            return commissionRepository.findAll(
                            CommissionSpecification.withFilter(filter, includeDeactivated), Pageable.unpaged())
                    .stream().map(Commission::getId).toList();
        }

        @Test
        void elFiltroPorEspecialidadIgnoraElPlanDadoDeBajaSalvoIncludeDeactivated() {
            var filter = new CommissionFilter(null, null, 99005, null);

            assertThat(ids(filter, false)).doesNotContain(commissionOnlyInDeactivatedPlanId);
            assertThat(ids(filter, true)).contains(commissionOnlyInDeactivatedPlanId);
        }

        @Test
        void sinEspecialidadIncluyeLaComisionCuyoUnicoPlanEstaDadoDeBaja() {
            var filter = new CommissionFilter(null, null, null, null, true);

            assertThat(ids(filter, false)).contains(commissionOnlyInDeactivatedPlanId)
                    .doesNotContain(commissionK1Id, commissionK2Id);
            assertThat(ids(filter, true)).doesNotContain(commissionOnlyInDeactivatedPlanId);
        }

        @Test
        void laComisionNoListaLaEspecialidadDelPlanDadoDeBaja() {
            assertThat(subjectCommissionRepository.findSpecialtiesByCommissionIds(
                    List.of(commissionOnlyInDeactivatedPlanId))).isEmpty();
        }

        @Test
        void elConteoPorEspecialidadNoCuentaElPlanDadoDeBajaSalvoIncludeDeactivated() {
            var civilId = List.of(civil.getId());

            assertThat(subjectCommissionRepository.countCommissionsBySpecialtyIds(civilId, "%", null, false))
                    .isEmpty();
            assertThat(subjectCommissionRepository.countCommissionsBySpecialtyIds(civilId, "%", null, true))
                    .singleElement().satisfies(row -> assertThat(row[1]).isEqualTo(1L));
        }
    }

    @Nested
    @DisplayName("Conteo de comisiones por especialidad")
    class CommissionCountBySpecialty {
        private Map<Long, Long> count(String pattern, Long periodId, boolean includeDeactivated) {
            return subjectCommissionRepository.countCommissionsBySpecialtyIds(
                            List.of(sistemas.getId(), civil.getId()), pattern, periodId, includeDeactivated)
                    .stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        }

        @Test
        void cuentaCadaComisionUnaVezAunqueOfrezcaVariasMateriasDeLaEspecialidad() {
            Commission k1 = commissionRepository.findById(commissionK1Id).orElseThrow();
            Subject fisica = subjectRepository.findById(fisicaId).orElseThrow();
            subjectCommissionRepository.save(SubjectCommission.builder()
                    .subject(fisica).commission(k1).enrolledCount(5).build());

            assertThat(count("%", null, false)).containsEntry(sistemas.getId(), 2L)
                    .doesNotContainKey(civil.getId());
        }

        @Test
        void respetaLosFiltrosDeCursoYPeriodo() {
            assertThat(count("%zzk10%", null, false)).containsEntry(sistemas.getId(), 1L);
            assertThat(count("%", period2099s2Id, false)).containsEntry(sistemas.getId(), 1L);
            assertThat(count("%zzk10%", period2099s2Id, false)).isEmpty();
        }

        @Test
        void excluyeComisionesDadasDeBajaSalvoIncludeDeactivated() {
            commissionRepository.softDelete(commissionRepository.findById(commissionK1Id).orElseThrow());

            assertThat(count("%", null, false)).containsEntry(sistemas.getId(), 1L);
            assertThat(count("%", null, true)).containsEntry(sistemas.getId(), 2L);
        }

        @Test
        void ignoraMateriaComisionDadaDeBaja() {
            subjectCommissionRepository.softDelete(subjectCommissionRepository
                    .findBySubject_IdAndCommission_Id(analisisId, commissionK1Id).orElseThrow());

            assertThat(count("%", null, true)).containsEntry(sistemas.getId(), 1L);
        }
    }

    @Nested
    @DisplayName("Conteo de materias por especialidad")
    class SubjectCountBySpecialty {
        private Map<Long, Long> count(Integer code, String pattern, Long planId, boolean includeDeactivated) {
            return subjectRepository.countSubjectsBySpecialtyIds(
                            List.of(sistemas.getId(), civil.getId()), code, pattern, planId, includeDeactivated)
                    .stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        }

        @Test
        void excluyeMateriasDeUnPlanDadoDeBajaSalvoIncludeDeactivated() {
            assertThat(count(null, "%", null, false)).containsEntry(sistemas.getId(), 2L)
                    .doesNotContainKey(civil.getId());
            assertThat(count(null, "%", null, true)).containsEntry(sistemas.getId(), 2L)
                    .containsEntry(civil.getId(), 1L);
        }

        @Test
        void respetaLosFiltrosDeCodigoNombreYPlan() {
            assertThat(count(99019, "%", null, false)).containsEntry(sistemas.getId(), 1L);
            assertThat(count(null, "%física%", null, false)).containsEntry(sistemas.getId(), 1L);
            assertThat(count(null, "%", plan2023Id, false)).containsEntry(sistemas.getId(), 1L);
            assertThat(count(99019, "%", plan2023Id, false)).isEmpty();
        }

        @Test
        void noCuentaMateriasDadasDeBajaSalvoIncludeDeactivated() {
            subjectRepository.softDelete(subjectRepository.findById(analisisId).orElseThrow());

            assertThat(count(null, "%", null, false)).containsEntry(sistemas.getId(), 1L);
            assertThat(count(null, "%", null, true)).containsEntry(sistemas.getId(), 2L);
        }
    }

    @Nested
    @DisplayName("Especialidades de una comisión")
    class CommissionSpecialties {
        @Test
        void devuelveLaEspecialidadDeLaMateriaVinculada() {
            var rows = subjectCommissionRepository.findSpecialtiesByCommissionIds(List.of(commissionK1Id));
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0)[0]).isEqualTo(commissionK1Id);
            assertThat(rows.get(0)[1]).isEqualTo(99017);
            assertThat(rows.get(0)[2]).isEqualTo("ZZ Sistemas");
        }

        @Test
        void ignoraVinculosDadosDeBaja() {
            SubjectCommission link = subjectCommissionRepository
                    .findBySubject_IdAndCommission_Id(analisisId, commissionK1Id).orElseThrow();
            subjectCommissionRepository.softDelete(link);

            assertThat(subjectCommissionRepository.findSpecialtiesByCommissionIds(List.of(commissionK1Id)))
                    .isEmpty();
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
            assertThat(result).extracting(sc -> sc.getCommission().getId()).containsExactly(commissionK1Id);
        }

        @Test
        void filtraPorCommissionId() {
            var result = subjectCommissionRepository.findAll(
                    SubjectCommissionSpecification.withFilter(new SubjectCommissionFilter(null, commissionK2Id)),
                    Pageable.unpaged());
            assertThat(result).extracting(sc -> sc.getSubject().getId()).containsExactly(fisicaId);
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
                    SpecialtySpecification.withFilter(new SpecialtyFilter(99005, null, null)), Pageable.unpaged());
            assertThat(result).extracting(Specialty::getId).containsExactly(civil.getId());
        }

        @Test
        void filtraPorNameParcial() {
            var result = specialtyRepository.findAll(
                    SpecialtySpecification.withFilter(new SpecialtyFilter(null, "zz sistemas", null)),
                    Pageable.unpaged());
            assertThat(result).extracting(Specialty::getId).containsExactly(sistemas.getId());
        }

        @Test
        void hasSubjectsExcluyeEspecialidadesSinMaterias() {
            var result = specialtyRepository.findAll(
                    SpecialtySpecification.withFilter(new SpecialtyFilter(null, null, true)), Pageable.unpaged());
            assertThat(result).extracting(Specialty::getId)
                    .contains(sistemas.getId())
                    .doesNotContain(civil.getId());
        }
    }

    @Nested
    @DisplayName("Búsqueda q")
    class FreeTextSearch {

        private List<Long> specialtyIds(String q) {
            return specialtyRepository.findAll(SpecialtySpecification.withFilter(
                    new SpecialtyFilter(null, null, null, null, null, null, null, null, q))).stream()
                    .map(Specialty::getId).toList();
        }

        @Test
        void ignoraAcentosYMayusculas() {
            assertThat(specialtyIds("ZZ SISTEMAS")).containsExactly(sistemas.getId());
            assertThat(subjectCommissionIds("analisis matematico")).containsExactly(analisisId);
        }

        @Test
        void toleraErroresDeTipeoSegunLargoDelTermino() {
            assertThat(specialtyIds("sistems")).contains(sistemas.getId());
            assertThat(specialtyIds("sistmas")).contains(sistemas.getId());
            assertThat(specialtyIds("civl")).contains(civil.getId());
            assertThat(specialtyIds("sixtemax")).contains(sistemas.getId());
            assertThat(specialtyIds("sixtemas")).contains(sistemas.getId());
            assertThat(specialtyIds("sxxtemas")).contains(sistemas.getId());
            assertThat(specialtyIds("sxxxemas")).doesNotContain(sistemas.getId());
            assertThat(specialtyIds("civx")).doesNotContain(civil.getId());
        }

        @Test
        void todosLosTokensDebenCoincidir() {
            assertThat(specialtyIds("zz sistemas")).containsExactly(sistemas.getId());
            assertThat(specialtyIds("sistemas civil")).isEmpty();
        }

        @Test
        void qVacioONuloNoFiltra() {
            assertThat(specialtyIds(null)).contains(sistemas.getId(), civil.getId());
            assertThat(specialtyIds("  ")).contains(sistemas.getId(), civil.getId());
        }

        @Test
        void planesBuscanPorCodigoYEspecialidad() {
            var porCodigo = studyPlanRepository.findAll(StudyPlanSpecification.withFilter(
                    new StudyPlanFilter(null, null, "99023")), Pageable.unpaged());
            var porEspecialidad = studyPlanRepository.findAll(StudyPlanSpecification.withFilter(
                    new StudyPlanFilter(null, null, "sistems 99008")), Pageable.unpaged());
            assertThat(porCodigo).extracting(StudyPlan::getId).containsExactly(plan2023Id);
            assertThat(porEspecialidad).extracting(StudyPlan::getId).containsExactly(plan2008Id);
        }

        @Test
        void comisionesBuscanPorCodigoPeriodoYNombreDeEspecialidad() {
            assertThat(commissionIds("zzk1001", true)).containsExactly(commissionK1Id);
            assertThat(commissionIds("2099", true)).contains(commissionK1Id, commissionK2Id);
            assertThat(commissionIds("sistems zzk2002", true)).contains(commissionK2Id);
            assertThat(commissionIds("zz sistemas", true)).contains(commissionK1Id, commissionK2Id);
        }

        @Test
        void comisionesNoMatcheanEspecialidadDadaDeBajaSinIncludeDeactivated() {
            sistemas.deactivate();
            specialtyRepository.save(sistemas);
            assertThat(commissionIds("sistemas", false)).doesNotContain(commissionK1Id, commissionK2Id);
            assertThat(commissionIds("sistemas", true)).contains(commissionK1Id, commissionK2Id);
        }

        @Test
        void materiaComisionBuscaPorMateriaYCodigoDeCurso() {
            assertThat(subjectCommissionIds("fisica zzk2002")).containsExactly(fisicaId);
            assertThat(subjectCommissionIds("fisica zzkabcd")).isEmpty();
        }

        @Test
        void codigosYTokensCortosSoloCoincidenPorPalabraCompleta() {
            assertThat(commissionIds("zzk100", true)).isEmpty();
            assertThat(commissionIds("zzk", true)).isEmpty();
            assertThat(commissionIds("zzk1002", true)).isEmpty();
            assertThat(commissionIds("zzk2002", true)).containsExactly(commissionK2Id);
        }

        @Test
        void tokenCortoDeLetrasEsPrefijoDePalabraDeDosOMasLetras() {
            assertThat(specialtyIds("sis")).contains(sistemas.getId());
            assertThat(specialtyIds("s")).contains(sistemas.getId());
            assertThat(specialtyIds("stem")).contains(sistemas.getId());
            assertThat(specialtyIds("ste")).doesNotContain(sistemas.getId());
        }

        @Test
        void especialidadEncuentraPorMateriaYComisionDescendiente() {
            assertThat(specialtyIds("matematico")).contains(sistemas.getId()).doesNotContain(civil.getId());
            assertThat(specialtyIds("zzk2002")).containsExactly(sistemas.getId());
            assertThat(specialtyIds("fisica zzk2002")).containsExactly(sistemas.getId());
        }

        @Test
        void comisionEncuentraPorMateriaSuya() {
            assertThat(commissionIds("analisis matematico", true)).containsExactly(commissionK1Id);
            assertThat(commissionIds("fisica", true)).containsExactly(commissionK2Id);
        }

        @Test
        void conEspecialidadSoloMiraLaRamaYSiLaEspecialidadCoincideIncluyeTodas() {
            assertThat(commissionIds("fisica", 99017, true)).containsExactly(commissionK2Id);
            assertThat(commissionIds("fisica", 99005, true)).isEmpty();
            assertThat(commissionIds("zz sistemas", 99017, true)).containsExactlyInAnyOrder(commissionK1Id,
                    commissionK2Id);
            assertThat(commissionIds("zz sistemas", 99005, true)).isEmpty();
        }

        @Test
        void materiaComisionIncluyeLosVinculosDeUnaComisionQueCoincide() {
            assertThat(subjectCommissionIds("zzk1001")).containsExactly(analisisId);
            assertThat(subjectCommissionIds("zzk1001 analisis")).containsExactly(analisisId);
        }

        @Test
        void conteoDeComisionesPorEspecialidadRespetaQ() {
            Long id = sistemas.getId();
            assertThat(countByQ(id, "zzk1001")).isEqualTo(1);
            assertThat(countByQ(id, "fisica")).isEqualTo(1);
            assertThat(countByQ(id, "zz sistemas")).isEqualTo(2);
            assertThat(countByQ(id, "zzk3003")).isZero();
        }

        @Test
        void ordenaPorRelevanciaCuandoNoHayOrdenExplicito() {
            Specialty parecida = specialtyRepository.save(
                    Specialty.builder().specialtyCode(99018).name("ZZ Sistemes").build());
            var page = specialtyRepository.findAll(SpecialtySpecification.withFilter(
                    new SpecialtyFilter(null, null, null, null, null, null, null, null, "zz sistemas")),
                    org.springframework.data.domain.PageRequest.of(0, 500));
            List<Long> ids = page.stream().map(Specialty::getId).toList();
            assertThat(ids).contains(sistemas.getId(), parecida.getId());
            assertThat(ids.indexOf(sistemas.getId())).isLessThan(ids.indexOf(parecida.getId()));
        }

        private int countByQ(Long specialtyId, String q) {
            var rows = commissionCountSearch.countBySpecialtyIds(List.of(specialtyId), "%", null, q, true);
            return rows.isEmpty() ? 0 : ((Long) rows.get(0)[1]).intValue();
        }

        private List<Long> commissionIds(String q, Integer specialtyCode, boolean includeDeactivated) {
            return commissionRepository.findAll(CommissionSpecification.withFilter(
                    new CommissionFilter(null, null, specialtyCode, null, null, q), includeDeactivated)).stream()
                    .map(Commission::getId).toList();
        }

        private List<Long> commissionIds(String q, boolean includeDeactivated) {
            return commissionRepository.findAll(CommissionSpecification.withFilter(
                    new CommissionFilter(null, null, null, null, null, q), includeDeactivated)).stream()
                    .map(Commission::getId).toList();
        }

        private List<Long> subjectCommissionIds(String q) {
            return subjectCommissionRepository.findAll(SubjectCommissionSpecification.withFilter(
                    new SubjectCommissionFilter(null, null, q))).stream()
                    .map(sc -> sc.getSubject().getId()).toList();
        }
    }
}
