package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.exception.ReallocationConflictException;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ClassroomAllocationLock} (SEC-RACE-001, plan {@code sugerencia-aula-reasignacion.md} §"lock por
 * aula") es la defensa contra dos escrituras concurrentes en la misma aula y franja bajo READ COMMITTED.
 * Un {@code @Transactional} de punta a punta no alcanza a interlear las dos escrituras porque cada
 * transacción es demasiado rápida; acá se fuerza la carrera reteniendo la transacción del hilo A con
 * {@link TransactionTemplate} manual detrás de un {@link CountDownLatch}, igual que
 * {@code RoomRequestItemVersionConcurrencyIntegrationTest} hace con el lock optimista de
 * {@code RoomRequestItem}.
 */
@Import(IntegrationTestData.class)
@DisplayName("ClassroomAllocationLock (integración)")
class ClassroomAllocationLockIntegrationTest extends AbstractIntegrationTest {

    private static final LocalTime START = LocalTime.of(8, 0);
    private static final int DURATION = 90;

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private AllocationService allocationService;
    @Autowired
    private ClassroomAllocationLock classroomAllocationLock;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("dos reasignaciones a la misma aula y franja se serializan: la segunda espera el commit de la primera y termina en conflicto")
    void reallocateToLockedClassroom_waitsForCommitThenConflicts() throws Exception {
        Building building = testData.edificio();
        Classroom aula = testData.aula(building);
        LocalDate date = LocalDate.now().plusDays(21);
        Occurrence occB = seedOccurrence(testData.materiaYComision(), date);
        Occurrence occA = seedOccurrence(testData.materiaYComision(), date);

        CountDownLatch aHoldingLock = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        Callable<Void> taskA = () -> {
            txTemplate.executeWithoutResult(status -> {
                classroomAllocationLock.lock(Set.of(aula.getId()));
                aHoldingLock.countDown();
                awaitLatch(releaseA);
                allocationRepository.saveAndFlush(Allocation.builder()
                        .occurrenceId(occA.getId())
                        .classroomId(aula.getId())
                        .source(AllocationSource.MANUAL)
                        .build());
            });
            return null;
        };
        Callable<List<AllocationResponseDto>> taskB = () -> asFixtureUser(() -> allocationService.reallocate(
                AllocationCommand.manual(
                        List.of(new AllocationItem(new AllocationTarget.Occurrences(List.of(occB.getId())), aula.getId())),
                        "reasignación bajo lock")));

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Void> futureA = pool.submit(taskA);
            awaitLatch(aHoldingLock);
            Future<List<AllocationResponseDto>> futureB = pool.submit(taskB);

            // Sin el lock, B (aula libre, franja libre) terminaría casi al instante: si este assert
            // no puede fallar, es porque el lock dejó de tomarse antes de la validación de solapamiento.
            assertThatThrownBy(() -> futureB.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);

            releaseA.countDown();
            futureA.get(30, TimeUnit.SECONDS);

            assertThatThrownBy(() -> {
                try {
                    futureB.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    throw e.getCause();
                }
            }).isInstanceOf(ReallocationConflictException.class);
        }

        List<Allocation> allocations = allocationRepository.findByOccurrenceIdIn(List.of(occA.getId(), occB.getId()));
        assertThat(allocations).hasSize(1);
        assertThat(allocations.getFirst().getOccurrenceId()).isEqualTo(occA.getId());
    }

    @Test
    @DisplayName("el lock es por aula: una reasignación a otra aula no espera al lock tomado sobre la primera")
    void reallocateToDifferentClassroom_doesNotWaitForLockOnAnotherOne() throws Exception {
        Building building = testData.edificio();
        Classroom aulaLockeada = testData.aula(building);
        Classroom aulaLibre = testData.aula(building);
        LocalDate date = LocalDate.now().plusDays(22);
        Occurrence occ = seedOccurrence(testData.materiaYComision(), date);

        CountDownLatch aHoldingLock = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        Callable<Void> taskA = () -> {
            txTemplate.executeWithoutResult(status -> {
                classroomAllocationLock.lock(Set.of(aulaLockeada.getId()));
                aHoldingLock.countDown();
                awaitLatch(releaseA);
            });
            return null;
        };
        Callable<List<AllocationResponseDto>> taskB = () -> asFixtureUser(() -> allocationService.reallocate(
                AllocationCommand.manual(
                        List.of(new AllocationItem(new AllocationTarget.Occurrences(List.of(occ.getId())), aulaLibre.getId())),
                        "reasignación a aula distinta")));

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Void> futureA = pool.submit(taskA);
            awaitLatch(aHoldingLock);
            Future<List<AllocationResponseDto>> futureB = pool.submit(taskB);

            List<AllocationResponseDto> result = futureB.get(1, TimeUnit.SECONDS);
            assertThat(result).hasSize(1);

            releaseA.countDown();
            futureA.get(30, TimeUnit.SECONDS);
        }

        assertThat(allocationRepository.findByOccurrenceIdIn(List.of(occ.getId())))
                .singleElement()
                .extracting(Allocation::getClassroomId)
                .isEqualTo(aulaLibre.getId());
    }

    @Test
    @DisplayName("lock(...) llamado fuera de una transacción activa lanza IllegalTransactionStateException (Propagation.MANDATORY)")
    void lockOutsideTransaction_throwsIllegalTransactionStateException() {
        assertThatThrownBy(() -> classroomAllocationLock.lock(Set.of(1L)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private Occurrence seedOccurrence(IntegrationTestData.SubjectAndCommission sc, LocalDate date) {
        var dto = new CreateRecurringEventRequestDto(
                30, START, DURATION, date.getDayOfWeek(), date, date, sc.subjectId(), sc.commissionId());
        Long eventId = academicEventService.createRecurringEvent(dto).id();
        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId);
        assertThat(occurrences).hasSize(1);
        return occurrences.getFirst();
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout esperando el latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
