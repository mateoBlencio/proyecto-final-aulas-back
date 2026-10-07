package ar.edu.utn.frc.siga.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.stereotype.Component;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Closed inventory of audited write points: every method with a write {@code @Transactional},
 * {@code @ApplicationModuleListener} or {@code @Scheduled} in the modules that write
 * {@code @Audited} entities must carry {@link AuditOperation} or be listed in {@link #EXCEPTIONS}
 * with its reason. Without this, the Envers revision has no {@code operacion_id} and the log
 * shows it as a standalone transaction.
 */
@DisplayName("Cobertura de @AuditOperation")
class AuditOperationCoverageTest {

    private static final List<String> PACKAGES = List.of(
            "ar.edu.utn.frc.siga.allocation",
            "ar.edu.utn.frc.siga.auth",
            "ar.edu.utn.frc.siga.events",
            "ar.edu.utn.frc.siga.roomrequest",
            "ar.edu.utn.frc.siga.settings",
            "ar.edu.utn.frc.siga.sysacad.internal.sync",
            "ar.edu.utn.frc.siga.ingest.service");

    /** Key {@code Class#method}; the value is the reason it carries no annotation. */
    private static final Map<String, String> EXCEPTIONS = Map.ofEntries(
            Map.entry("AuthServiceImpl#login", "writes no audited entities (refresh tokens are not @Audited)"),
            Map.entry("AuthServiceImpl#refresh", "writes no audited entities (refresh tokens are not @Audited)"),
            Map.entry("AuthServiceImpl#logout", "writes no audited entities (refresh tokens are not @Audited)"),
            Map.entry("RoomRequestExpiryScheduler#expireOverdueItems",
                    "only delegates to RoomRequestExpiryServiceImpl.expireOverdueItems, which carries the annotation"),
            Map.entry("RefreshTokenServiceImpl#issue", "refresh tokens are not @Audited"),
            Map.entry("RefreshTokenServiceImpl#refresh", "refresh tokens are not @Audited"),
            Map.entry("RefreshTokenServiceImpl#revoke", "refresh tokens are not @Audited"),
            Map.entry("RefreshTokenServiceImpl#revokeAllByUserId", "refresh tokens are not @Audited"),
            Map.entry("ClassroomAllocationLock#lock", "propagation MANDATORY, only takes an advisory lock and writes nothing"),
            Map.entry("IngestRowResolver#resolveRefs",
                    "runs inside IngestServiceImpl.ingestFile, which opens the operation"),
            Map.entry("RecurringEventReconciler#reconcileFuture",
                    "its only caller is AcademicPeriodChangedListener.on, which carries the annotation"));

    @Test
    @DisplayName("todo punto de escritura tiene @AuditOperation o una excepción justificada")
    void everyWritePointIsAnnotatedOrExcepted() {
        Set<String> uncovered = new TreeSet<>();
        for (Class<?> type : scanComponents()) {
            for (String key : unannotatedWritePoints(type)) {
                if (!EXCEPTIONS.containsKey(key)) {
                    uncovered.add(key);
                }
            }
        }

        assertThat(uncovered)
                .as("métodos de escritura sin @AuditOperation ni excepción en AuditOperationCoverageTest")
                .isEmpty();
    }

    @Test
    @DisplayName("cada excepción apunta a un método real que de otro modo sería una infracción")
    void exceptionsAreNotStale() {
        Set<String> unannotated = new TreeSet<>();
        scanComponents().forEach(type -> unannotated.addAll(unannotatedWritePoints(type)));

        assertThat(unannotated).containsAll(EXCEPTIONS.keySet());
    }

    @Test
    @DisplayName("el escaneo encuentra las clases con puntos de escritura anotados")
    void scanFindsKnownAnnotatedWriters() {
        assertThat(scanComponents()).extracting(Class::getSimpleName)
                .contains("AcademicEventServiceImpl", "RoomRequestResolutionServiceImpl", "SettingsServiceImpl",
                        "UserServiceImpl", "OccurrenceVacatedListener", "AcademicPeriodChangedListener",
                        "AcademicEventSyncService", "IngestServiceImpl");
    }

    @Test
    @DisplayName("el detector marca un método transaccional de escritura sin anotación")
    void detectorFlagsUnannotatedTransactionalMethod() {
        assertThat(unannotatedWritePoints(Fixture.class))
                .containsExactlyInAnyOrder("Fixture#unannotatedWrite", "Fixture#unannotatedListener",
                        "Fixture#unannotatedScheduled");
    }

    // Own scan instead of ClassPathScanningCandidateComponentProvider: that one discards beans with
    // @ConditionalOnProperty (Sysacad sync), which are audited all the same.
    private static List<Class<?>> scanComponents() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        SimpleMetadataReaderFactory readers = new SimpleMetadataReaderFactory(resolver);
        List<Class<?>> types = new ArrayList<>();
        try {
            for (String basePackage : PACKAGES) {
                Resource[] resources = resolver.getResources("classpath*:"
                        + basePackage.replace('.', '/') + "/**/*.class");
                for (Resource resource : resources) {
                    AnnotationMetadata metadata = readers.getMetadataReader(resource).getAnnotationMetadata();
                    if (metadata.isConcrete() && metadata.isIndependent()
                            && metadata.isAnnotated(Component.class.getName())) {
                        types.add(Class.forName(metadata.getClassName()));
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("No se pudieron escanear los componentes", e);
        }
        return types;
    }

    static List<String> unannotatedWritePoints(Class<?> type) {
        boolean classWritesInTransaction = isWriteTransactional(type.getAnnotation(Transactional.class));
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .filter(m -> isWritePoint(m, classWritesInTransaction))
                .filter(m -> !AnnotatedElementUtils.hasAnnotation(m, AuditOperation.class))
                .map(m -> type.getSimpleName() + "#" + m.getName())
                .distinct()
                .toList();
    }

    private static boolean isWritePoint(Method method, boolean classWritesInTransaction) {
        MergedAnnotations annotations = MergedAnnotations.from(method);
        if (annotations.isPresent(ApplicationModuleListener.class) || annotations.isPresent(Scheduled.class)) {
            return true;
        }
        if (annotations.isPresent(Transactional.class)) {
            return !annotations.get(Transactional.class).getBoolean("readOnly");
        }
        // Class-level write @Transactional: only reaches public methods.
        return classWritesInTransaction && Modifier.isPublic(method.getModifiers());
    }

    private static boolean isWriteTransactional(Transactional transactional) {
        return transactional != null && !transactional.readOnly();
    }

    /** Detector test class: the three unannotated methods must be flagged. */
    static class Fixture {

        @Transactional
        public void unannotatedWrite() {
        }

        @ApplicationModuleListener
        void unannotatedListener(Object event) {
        }

        @Scheduled(fixedDelay = 1000)
        void unannotatedScheduled() {
        }

        @Transactional
        @AuditOperation("Anotada")
        public void annotatedWrite() {
        }

        @Transactional(readOnly = true)
        public void readOnly() {
        }

        public void notTransactional() {
        }
    }
}
