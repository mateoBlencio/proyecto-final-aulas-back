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
import java.util.TreeMap;
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
            "ar.edu.utn.frc.siga.audit",
            "ar.edu.utn.frc.siga.academic",
            "ar.edu.utn.frc.siga.allocation",
            "ar.edu.utn.frc.siga.auth",
            "ar.edu.utn.frc.siga.events",
            "ar.edu.utn.frc.siga.roomrequest",
            "ar.edu.utn.frc.siga.settings",
            "ar.edu.utn.frc.siga.space",
            "ar.edu.utn.frc.siga.sysacad.internal.sync",
            "ar.edu.utn.frc.siga.ingest.service");

    /** Key {@code Class#method}; the value is the reason it carries no annotation. */
    private static final Map<String, String> EXCEPTIONS = Map.ofEntries(
            Map.entry("AuthServiceImpl#refresh", "writes no audited entities (refresh tokens are not @Audited)"),
            Map.entry("AuthServiceImpl#logout", "writes no audited entities (refresh tokens are not @Audited)"),
            Map.entry("RoomRequestExpiryScheduler#expireOverdueItems",
                    "only delegates to RoomRequestExpiryServiceImpl.expireOverdueItems, which carries the annotation"),
            Map.entry("RoomRequestAcceptanceScheduler#acceptOverdueNotifiedItems",
                    "only delegates to RoomRequestExpiryServiceImpl.acceptOverdueNotifiedItems, which carries the annotation"),
            Map.entry("RoomRequestSuggestionStore#onSettingChanged", "only updates the in-memory suggestion cache TTL"),
            Map.entry("DirectEventSuggestionStore#onSettingChanged", "only updates the in-memory suggestion cache TTL"),
            Map.entry("RefreshTokenServiceImpl#issue", "refresh tokens are not @Audited"),
            Map.entry("RefreshTokenServiceImpl#issueForLogin", "refresh tokens are not @Audited"),
            Map.entry("RefreshTokenServiceImpl#refresh", "refresh tokens are not @Audited"),
            Map.entry("RefreshTokenServiceImpl#revoke", "refresh tokens are not @Audited"),
            Map.entry("RefreshTokenServiceImpl#revokeAllByUserId", "refresh tokens are not @Audited"),
            Map.entry("RefreshTokenServiceImpl#purgeExpired", "refresh tokens are not @Audited"),
            Map.entry("RefreshTokenPurgeScheduler#purgeExpired",
                    "only delegates to RefreshTokenServiceImpl.purgeExpired and PasswordResetServiceImpl.purgeExpired; "
                            + "refresh tokens and reset tokens are not @Audited"),
            Map.entry("PasswordResetServiceImpl#issueResetLink",
                    "writes no audited entities: reset tokens and notifications are not @Audited"),
            Map.entry("PasswordResetServiceImpl#purgeExpired", "reset tokens are not @Audited"),
            Map.entry("AuditArchiveScheduler#archive",
                    "only delegates to AuditArchiveServiceImpl.archive, which carries the annotation"),
            Map.entry("AuditArchiveRepository#archiveBatch",
                    "writes the _aud tables by JDBC (Envers does not see it); runs inside AuditArchiveServiceImpl.archive,"
                            + " whose AuditArchiveRun record carries the operation"),
            Map.entry("AuditArchiveRepository#tryLock",
                    "propagation MANDATORY, only takes an advisory lock and writes nothing"),
            Map.entry("ClassroomAllocationLock#lock", "propagation MANDATORY, only takes an advisory lock and writes nothing"),
            Map.entry("SysacadScheduleLoader#load",
                    "only writes sysacad_horario, which is not @Audited; its own log table keeps the history"),
            Map.entry("RecurringEventReconciler#reconcileFuture",
                    "its only caller is AcademicPeriodChangedListener.on, which carries the annotation"),
            Map.entry("RecurringEventReconciler#retireFuture",
                    "its only caller is AcademicEventServiceImpl.syncRecurringEvents, which carries the annotation"),
            Map.entry("AllocationServiceImpl#revalidateRescheduled",
                    "its only caller is UniqueEventRescheduledListener.on, which runs inside"
                            + " AcademicEventServiceImpl.updateUniqueEvent, which carries the annotation"));

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
                        "UserServiceImpl", "AcademicPeriodChangedListener",
                        "AcademicEventSyncService", "IngestServiceImpl");
    }

    @Test
    @DisplayName("el detector marca un método transaccional de escritura sin anotación")
    void detectorFlagsUnannotatedTransactionalMethod() {
        assertThat(unannotatedWritePoints(Fixture.class))
                .containsExactlyInAnyOrder("Fixture#unannotatedWrite", "Fixture#unannotatedListener",
                        "Fixture#unannotatedScheduled");
    }


    /** Operations that name no records, so they carry no {@code subject}. Key {@code Class#method}. */
    private static final Map<String, String> WITHOUT_SUBJECT = Map.of(
            "AuditArchiveServiceImpl#archive", "archives audit rows: it changes no record a summary could name",
            "DashboardSnapshotServiceImpl#regenerate", "writes its own description with the month and the reason",
            "AcademicPeriodChangedListener#on", "writes its own description with the period");

    @Test
    @DisplayName("toda @AuditOperation tiene subject, salvo las tres que no nombran registros")
    void everyAuditOperationHasSubjectExceptTheExempt() {
        Map<String, AuditOperation> operations = auditOperations();

        Set<String> withoutSubject = new TreeSet<>();
        operations.forEach((key, operation) -> {
            if (operation.subject().isBlank()) {
                withoutSubject.add(key);
            }
        });

        assertThat(withoutSubject)
                .as("@AuditOperation sin subject que no está en WITHOUT_SUBJECT")
                .containsExactlyInAnyOrderElementsOf(WITHOUT_SUBJECT.keySet());
    }

    @Test
    @DisplayName("el escaneo de operaciones encuentra las anotaciones de todos los módulos, también las exentas")
    void auditOperationScanFindsOperationsOfEveryModule() {
        Map<String, AuditOperation> operations = auditOperations();

        assertThat(operations.size()).isGreaterThanOrEqualTo(85);
        assertThat(operations).containsKeys(WITHOUT_SUBJECT.keySet().toArray(String[]::new));
        assertThat(operations.get("SubjectServiceImpl#deactivate")).isNotNull();
    }

    @Test
    @DisplayName("ningún texto base de @AuditOperation contiene el nombre de una constante de enum en mayúsculas")
    void noBaseTextContainsAnEnumConstantName() {
        Set<String> constants = enumConstantNames();
        assertThat(constants).contains("VIRTUAL_CLASS", "ADMINISTRADOR", "NEW");

        Map<String, String> offending = new TreeMap<>();
        auditOperations().forEach((key, operation) -> {
            for (String word : operation.value().split("[^\\p{L}\\p{N}_]+")) {
                if (constants.contains(word)) {
                    offending.put(key, operation.value());
                }
            }
        });

        assertThat(offending).as("textos base con códigos internos").isEmpty();
    }

    @Test
    @DisplayName("el detector de constantes marca un texto con 'VIRTUAL_CLASS' y deja pasar 'Clase virtual'")
    void enumConstantDetectorFlagsCodes() {
        Set<String> constants = enumConstantNames();

        assertThat(containsConstant("Liberación de aulas (VIRTUAL_CLASS)", constants)).isTrue();
        assertThat(containsConstant("Liberación de aulas (Clase virtual)", constants)).isFalse();
    }

    private static boolean containsConstant(String text, Set<String> constants) {
        return Arrays.stream(text.split("[^\\p{L}\\p{N}_]+")).anyMatch(constants::contains);
    }

    /** Every {@code @AuditOperation} method of the application, keyed {@code Class#method}. Test classes are left out. */
    static Map<String, AuditOperation> auditOperations() {
        Map<String, AuditOperation> operations = new TreeMap<>();
        for (Resource resource : mainClasses()) {
            AnnotationMetadata metadata = metadataOf(resource);
            if (!metadata.hasAnnotatedMethods(AuditOperation.class.getName())) {
                continue;
            }
            for (Method method : classOf(metadata).getDeclaredMethods()) {
                AuditOperation operation = AnnotatedElementUtils.findMergedAnnotation(method, AuditOperation.class);
                if (operation != null) {
                    operations.put(method.getDeclaringClass().getSimpleName() + "#" + method.getName(), operation);
                }
            }
        }
        return operations;
    }

    private static Set<String> enumConstantNames() {
        Set<String> names = new TreeSet<>();
        for (Resource resource : mainClasses()) {
            AnnotationMetadata metadata = metadataOf(resource);
            if ("java.lang.Enum".equals(metadata.getSuperClassName())) {
                Object[] constants = classOf(metadata).getEnumConstants();
                if (constants != null) {
                    Arrays.stream(constants).forEach(constant -> names.add(((Enum<?>) constant).name()));
                }
            }
        }
        return names;
    }

    private static List<Resource> mainClasses() {
        try {
            return Arrays.stream(new PathMatchingResourcePatternResolver()
                            .getResources("classpath*:ar/edu/utn/frc/siga/**/*.class"))
                    .filter(resource -> !resourcePath(resource).contains("test-classes"))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudieron escanear las clases", e);
        }
    }

    private static String resourcePath(Resource resource) {
        try {
            return resource.getURL().getPath();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AnnotationMetadata metadataOf(Resource resource) {
        try {
            return new SimpleMetadataReaderFactory().getMetadataReader(resource).getAnnotationMetadata();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Class<?> classOf(AnnotationMetadata metadata) {
        try {
            return Class.forName(metadata.getClassName());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
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
