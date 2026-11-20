package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
class DeactivatedClassroomReconciler implements ApplicationRunner {

    private final ClassroomService classroomService;
    private final DeactivatedClassroomReleaser releaser;
    private final TransactionTemplate transactionTemplate;

    DeactivatedClassroomReconciler(ClassroomService classroomService, DeactivatedClassroomReleaser releaser,
                                   PlatformTransactionManager transactionManager) {
        this.classroomService = classroomService;
        this.releaser = releaser;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Un fallo no frena el arranque: la transacción hace rollback, queda en el log y se reintenta en el
     * próximo arranque. La transacción va por {@link TransactionTemplate} y no con {@code @Transactional}
     * para que el catch quede afuera de ella: con la anotación, el commit rollback-only escaparía del catch.
     */
    @Override
    @AuditOperation(value = "Reconciliación de aulas dadas de baja", subject = "Allocation")
    public void run(@NonNull ApplicationArguments args) {
        try {
            transactionTemplate.executeWithoutResult(status -> SystemScope.run(this::reconcile));
        } catch (RuntimeException e) {
            log.error("Falló la reconciliación de aulas dadas de baja; se reintenta en el próximo arranque", e);
        }
    }

    private void reconcile() {
        List<ClassroomResponseDto> deactivated = classroomService.findDeactivatedInSiga();
        if (deactivated.isEmpty()) {
            return;
        }
        Map<Long, String> noteByClassroom = new LinkedHashMap<>();
        for (ClassroomResponseDto classroom : deactivated) {
            if (classroom.unavailableReason() == UnavailableReason.DEACTIVATED) {
                noteByClassroom.put(classroom.id(), "Aula deshabilitada");
            } else if (classroom.unavailableReason() == UnavailableReason.PARENT_DEACTIVATED) {
                noteByClassroom.put(classroom.id(), "Edificio deshabilitado");
            }
        }
        if (noteByClassroom.isEmpty()) {
            return;
        }
        DeactivatedClassroomReleaser.DeactivationResult result = releaser.apply(noteByClassroom);
        log.info("Reconciliación de aulas dadas de baja: clasesLiberadas={}, clasesFusionadas={}",
                result.releasedClasses(), result.mergedClasses());
    }
}
