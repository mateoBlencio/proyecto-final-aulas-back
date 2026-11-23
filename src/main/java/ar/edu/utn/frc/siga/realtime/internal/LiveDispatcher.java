package ar.edu.utn.frc.siga.realtime.internal;

import ar.edu.utn.frc.siga.common.realtime.LiveChange;
import ar.edu.utn.frc.siga.realtime.config.LiveProperties;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Reparte los cambios cada {@link LiveProperties#flushInterval()} y manda el heartbeat. Usa un hilo propio: el
 * scheduler de Spring lo comparten jobs largos como el sync de SysAcad, que demorarían los avisos.
 */
@Slf4j
@Component
class LiveDispatcher {

    private final LiveProperties properties;
    private final LiveSubscriptions subscriptions;
    private final ClassroomService classroomService;
    private final LiveChangeBuffer buffer = new LiveChangeBuffer();
    private ScheduledExecutorService executor;

    LiveDispatcher(LiveProperties properties, LiveSubscriptions subscriptions, ClassroomService classroomService) {
        this.properties = properties;
        this.subscriptions = subscriptions;
        this.classroomService = classroomService;
    }

    @PostConstruct
    void start() {
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("live-dispatcher").daemon().factory());
        long flush = properties.flushInterval().toMillis();
        long heartbeat = properties.heartbeatInterval().toMillis();
        executor.scheduleWithFixedDelay(this::safeFlush, flush, flush, TimeUnit.MILLISECONDS);
        executor.scheduleWithFixedDelay(subscriptions::heartbeat, heartbeat, heartbeat, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    void accept(LiveChange change) {
        buffer.add(change);
    }

    void flush() {
        for (LiveChangeBuffer.Drained drained : buffer.drain()) {
            subscriptions.deliver(resolve(drained));
        }
    }

    private void safeFlush() {
        try {
            flush();
        } catch (RuntimeException e) {
            log.warn("No se pudieron repartir los avisos en tiempo real", e);
        }
    }

    /** Pasa aulas a edificios. Un aula desconocida vuelve el aviso general: mejor un refetch de más que uno de menos. */
    private LiveDelivery resolve(LiveChangeBuffer.Drained drained) {
        if (drained.unscoped() || drained.classroomIds().isEmpty()) {
            return new LiveDelivery(drained.topic(), drained.buildingIds(), drained.unscoped());
        }
        List<ClassroomResponseDto> classrooms = classroomService.findByIdsIncludingDeactivated(drained.classroomIds());
        Set<Long> buildingIds = new HashSet<>(drained.buildingIds());
        classrooms.forEach(classroom -> buildingIds.add(classroom.buildingId()));
        boolean unknownClassroom = classrooms.size() < drained.classroomIds().size();
        return new LiveDelivery(drained.topic(), buildingIds, unknownClassroom);
    }
}
