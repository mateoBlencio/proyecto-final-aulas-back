package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code RoomRequestItem.version} (V8) es la defensa contra dos resoluciones concurrentes sobre el
 * mismo ítem (dos auxiliares áulicos asignando/cancelando a la vez). Un {@code @Transactional} normal
 * de punta a punta casi nunca alcanza a interlear lectura-y-escritura entre dos hilos porque cada
 * transacción es demasiado rápida; acá se fuerza la carrera con {@link TransactionTemplate} manual
 * y una {@link CyclicBarrier} entre la lectura y la escritura, para que ambos hilos lean la misma
 * versión antes de que cualquiera de los dos comitee.
 */
@Import(IntegrationTestData.class)
@DisplayName("Lock optimista en RoomRequestItem (integración)")
class RoomRequestItemVersionConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private RoomRequestItemRepository itemRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("dos cancelaciones concurrentes sobre el mismo ítem: una gana, la otra pierde con OptimisticLockingFailureException")
    void concurrentUpdatesOnSameItem_loserGetsOptimisticLockingFailure() throws Exception {
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, sc.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, sc.commissionId(), LocalDate.now().plusDays(10),
                RoomRequestStatus.NEW);
        roomRequestRepository.save(request);
        Long itemId = item.getId();

        CyclicBarrier barrier = new CyclicBarrier(2);
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        Callable<Void> cancelTask = () -> {
            txTemplate.executeWithoutResult(status -> {
                RoomRequestItem loaded = itemRepository.findById(itemId).orElseThrow();
                awaitBothReady(barrier);
                loaded.decide(RoomRequestStatus.CANCELLED, "subsecretaria@frc.utn.edu.ar", "motivo",
                        LocalDateTime.now());
                itemRepository.saveAndFlush(loaded);
            });
            return null;
        };

        List<Future<Void>> futures;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            futures = List.of(pool.submit(cancelTask), pool.submit(cancelTask));
        }

        int succeeded = 0;
        List<Throwable> failures = new ArrayList<>();
        for (Future<Void> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
                succeeded++;
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            }
        }

        assertThat(succeeded).isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst()).isInstanceOf(OptimisticLockingFailureException.class);
    }

    private static void awaitBothReady(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
