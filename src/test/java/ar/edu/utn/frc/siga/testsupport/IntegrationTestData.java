package ar.edu.utn.frc.siga.testsupport;

import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomTypeRepository;

import java.util.concurrent.atomic.AtomicLong;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;

/**
 * Seeds idempotentes para tests de integración. No corre {@code data.sql} (perfil
 * {@code integration}, ver {@code AbstractIntegrationTest}), así que lo que otros perfiles dan
 * por sentado (tipo de aula por defecto, etc.) hay que sembrarlo acá.
 *
 * <p>Sin rollback entre tests (Envers necesita commits reales): cada seed que crea una fila usa
 * una clave natural con sufijo único ({@link #nextSeq()}, contador atómico sembrado con
 * {@code nanoTime}) para no colisionar entre tests ni entre clases.
 *
 * <p>{@code @TestConfiguration} en lugar de {@code @Component}: así el classpath scanning de
 * {@code @SpringBootTest} no la recoge sola (queda excluida por el filtro de test-slices);
 * cada test que la necesita la trae explícitamente con {@code @Import(IntegrationTestData.class)}.
 *
 * <p>Solo lo que las fases actuales (space CRUD + findOrCreate) necesitan. Los seeds de
 * {@code academic} y de eventos recurrentes con ocurrencias se agregan cuando haya un test que
 * los use.
 */
@TestConfiguration
@RequiredArgsConstructor
public class IntegrationTestData {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    private final BuildingRepository buildingRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomTypeRepository classroomTypeRepository;

    @Value("${siga.space.default-classroom-type:Normal}")
    private String defaultClassroomTypeDescription;

    /** Sufijo único para claves naturales (roomNumber, nombre de edificio, etc.). */
    public static long nextSeq() {
        return SEQ.incrementAndGet();
    }

    /**
     * Tipo de aula por defecto ({@code siga.space.default-classroom-type}), el que
     * {@code ClassroomTypeServiceImpl.findDefault()} espera encontrar. El perfil
     * {@code integration} no corre {@code data.sql}, así que sin este seed
     * {@code ClassroomService.findOrCreate} rompe con {@code SpaceDomainException}.
     */
    public ClassroomType tipoAulaNormal() {
        return classroomTypeRepository.findByDescriptionIgnoreCase(defaultClassroomTypeDescription)
                .orElseGet(() -> classroomTypeRepository.save(
                        ClassroomType.builder()
                                .description(defaultClassroomTypeDescription)
                                .deleted(false)
                                .build()));
    }

    public Building edificio(String namePrefix, int floorCount, boolean active) {
        return buildingRepository.save(Building.builder()
                .name(namePrefix + "-" + nextSeq())
                .floorCount(floorCount)
                .active(active)
                .deleted(false)
                .build());
    }

    /** Edificio activo con 5 pisos, nombre único. */
    public Building edificio() {
        return edificio("Edificio-IT", 5, true);
    }

    public Classroom aula(Building building, ClassroomType tipo, int floor, int capacity, boolean available) {
        return classroomRepository.save(Classroom.builder()
                .roomNumber("AULA-" + nextSeq())
                .floor(floor)
                .capacity(capacity)
                .available(available)
                .deleted(false)
                .building(building)
                .classroomType(tipo)
                .build());
    }

    /** Aula disponible, piso 1, capacidad 40, con el tipo de aula por defecto. */
    public Classroom aula(Building building) {
        return aula(building, tipoAulaNormal(), 1, 40, true);
    }
}
