package ar.edu.utn.frc.siga.space.specification;

import ar.edu.utn.frc.siga.common.repository.SoftDeleteSpecifications;
import ar.edu.utn.frc.siga.space.dto.BuildingFilter;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
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
 * Cada test corre en su propia transacción con rollback ({@code @DataJpaTest}), así que no
 * hace falta limpiar entre tests. Se usan nombres con prefijo distintivo para no chocar con
 * los edificios que puedan haber dejado commiteados otros tests de integración.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration")
@DisplayName("BuildingSpecification contra la base")
class BuildingSpecificationTest {

    private static final String PREFIX = "ZZ-BSpec ";

    @Autowired
    private BuildingRepository buildingRepository;

    private Long centralId;
    private Long anexoId;

    @BeforeEach
    void setUp() {
        centralId = buildingRepository.save(Building.builder().name(PREFIX + "Central").build()).getId();
        anexoId = buildingRepository.save(Building.builder().name(PREFIX + "Anexo Sur").build()).getId();
    }

    @Test
    void filtraPorNameParcialSinDistinguirMayusculas() {
        var result = buildingRepository.findAll(
                BuildingSpecification.withFilter(new BuildingFilter("zz-bspec central"))
                        .and(SoftDeleteSpecifications.activeUnless(false)),
                Pageable.unpaged());
        assertThat(result).extracting(Building::getId).containsExactly(centralId);
    }

    @Test
    void filtroPrefijoDevuelveLosDosDelFixture() {
        var result = buildingRepository.findAll(
                BuildingSpecification.withFilter(new BuildingFilter(PREFIX.trim()))
                        .and(SoftDeleteSpecifications.activeUnless(false)),
                Pageable.unpaged());
        assertThat(result).extracting(Building::getId).containsExactlyInAnyOrder(centralId, anexoId);
    }

    @Test
    void activeUnlessOcultaLosDesactivadosSalvoQueSePidanExplicitamente() {
        Building anexo = buildingRepository.findById(anexoId).orElseThrow();
        anexo.deactivate();
        buildingRepository.save(anexo);

        var soloActivos = buildingRepository.findAll(
                BuildingSpecification.withFilter(new BuildingFilter(PREFIX.trim()))
                        .and(SoftDeleteSpecifications.activeUnless(false)),
                Pageable.unpaged());
        var todos = buildingRepository.findAll(
                BuildingSpecification.withFilter(new BuildingFilter(PREFIX.trim()))
                        .and(SoftDeleteSpecifications.activeUnless(true)),
                Pageable.unpaged());

        assertThat(soloActivos).extracting(Building::getId).containsExactly(centralId);
        assertThat(todos).extracting(Building::getId).containsExactlyInAnyOrder(centralId, anexoId);
    }
}
