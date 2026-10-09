package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordRevision;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordStates;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.repository.RecurringEventRepository;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.settings.service.SettingsStore;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the audited state of records against real Postgres. The audit rows come from real commits
 * (no {@code @Transactional}, Envers needs them). Statements are counted with a JDK proxy over the
 * {@link DataSource}, since Hibernate statistics do not see {@code JdbcTemplate} queries.
 */
@Import(IntegrationTestData.class)
@DisplayName("AuditRecordStateRepository (integración)")
class AuditRecordStateRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuditRecordStateRepository stateRepository;
    @Autowired
    private AuditedEntityRegistry registry;
    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RecurringEventRepository recurringEventRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private SettingsStore settingsStore;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private AuditedEntity entity(String jpaName) {
        return registry.all().stream().filter(e -> e.jpaName().equals(jpaName)).findFirst().orElseThrow();
    }

    private int lastRevision(AuditedEntity entity, Object id) {
        return jdbcTemplate.queryForObject(
                "SELECT MAX(rev) FROM " + entity.auditTable() + " WHERE " + entity.idColumn() + " = ?",
                Integer.class, id);
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> action.run());
    }

    /** Recurring event on a future Monday with {@code occurrences} occurrences, all created in one transaction. */
    private SeededEvent seedEvent(int occurrences) {
        var sc = testData.materiaYComision();
        LocalDate start = LocalDate.now().plusDays(300).with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));
        List<Long> occurrenceIds = new ArrayList<>();
        Long[] eventId = new Long[1];
        inTransaction(() -> {
            RecurringEvent event = recurringEventRepository.save(RecurringEvent.builder()
                    .enrolled(30).startTime(LocalTime.of(8, 0)).duration(Duration.ofMinutes(90))
                    .dayOfWeek(DayOfWeek.MONDAY).startDate(start).endDate(start.plusWeeks(occurrences))
                    .subjectId(sc.subjectId()).commissionId(sc.commissionId()).build());
            eventId[0] = event.getId();
            for (int i = 0; i < occurrences; i++) {
                occurrenceIds.add(occurrenceRepository.save(Occurrence.builder().event(event)
                        .date(start.plusWeeks(i)).status(OccurrenceStatus.NEEDS_ROOM).build()).getId());
            }
        });
        return new SeededEvent(eventId[0], occurrenceIds, start);
    }

    private record SeededEvent(Long eventId, List<Long> occurrenceIds, LocalDate start) {
    }

    @Test
    @DisplayName("devuelve el estado actual y el previo de una ocurrencia modificada")
    void loadsCurrentAndPreviousState() {
        AuditedEntity occurrence = entity("Occurrence");
        SeededEvent seeded = seedEvent(1);
        Long occurrenceId = seeded.occurrenceIds().getFirst();
        inTransaction(() -> occurrenceRepository.findById(occurrenceId).orElseThrow()
                .setStatus(OccurrenceStatus.ROOM_RELEASED));
        int revision = lastRevision(occurrence, occurrenceId);

        RecordStates states = stateRepository.load(
                List.of(new RecordRevision(occurrence, occurrenceId.toString(), revision)))
                .get(new RecordRevision(occurrence, occurrenceId.toString(), revision));

        assertThat(states).isNotNull();
        assertThat(states.current()).containsEntry("status", "ROOM_RELEASED")
                .containsEntry("date", seeded.start()).containsEntry("event", seeded.eventId());
        assertThat(states.previous()).containsEntry("status", "NEEDS_ROOM")
                .containsEntry("date", seeded.start()).containsEntry("event", seeded.eventId());
    }

    @Test
    @DisplayName("la revisión de alta no tiene fila previa: todas las propiedades previas son null")
    void creationRevisionHasNoPreviousRow() {
        AuditedEntity occurrence = entity("Occurrence");
        Long occurrenceId = seedEvent(1).occurrenceIds().getFirst();
        int revision = lastRevision(occurrence, occurrenceId);
        RecordRevision key = new RecordRevision(occurrence, occurrenceId.toString(), revision);

        RecordStates states = stateRepository.load(List.of(key)).get(key);

        assertThat(states.current()).containsEntry("status", "NEEDS_ROOM");
        assertThat(states.previous()).isNotEmpty().containsOnlyKeys(states.current().keySet())
                .containsValues((Object) null);
        assertThat(states.previous().values()).containsOnlyNulls();
    }

    @Test
    @DisplayName("una baja devuelve el último estado guardado como estado actual")
    void deletionRevisionKeepsLastState() {
        AuditedEntity occurrence = entity("Occurrence");
        Long occurrenceId = seedEvent(1).occurrenceIds().getFirst();
        inTransaction(() -> occurrenceRepository.deleteById(occurrenceId));
        int revision = lastRevision(occurrence, occurrenceId);
        RecordRevision key = new RecordRevision(occurrence, occurrenceId.toString(), revision);

        RecordStates states = stateRepository.load(List.of(key)).get(key);

        assertThat(jdbcTemplate.queryForObject("SELECT revtype FROM " + occurrence.auditTable()
                + " WHERE id_ocurrencia = ? AND rev = ?", Integer.class, occurrenceId, revision)).isEqualTo(2);
        assertThat(states.current()).containsEntry("status", "NEEDS_ROOM");
    }

    @Test
    @DisplayName("une la tabla de subclase: dayOfWeek y enrolled cambian en la misma revisión")
    void joinsSubclassTable() {
        AuditedEntity academicEvent = entity("AcademicEvent");
        SeededEvent seeded = seedEvent(1);
        int creation = lastRevision(academicEvent, seeded.eventId());
        inTransaction(() -> {
            RecurringEvent event = recurringEventRepository.findById(seeded.eventId()).orElseThrow();
            ReflectionTestUtils.setField(event, "dayOfWeek", DayOfWeek.TUESDAY);
            event.setEnrolled(45);
        });
        int modification = lastRevision(academicEvent, seeded.eventId());
        assertThat(modification).isGreaterThan(creation);
        RecordRevision key = new RecordRevision(academicEvent, seeded.eventId().toString(), modification);

        RecordStates states = stateRepository.load(List.of(key)).get(key);

        assertThat(states.current()).containsEntry("dayOfWeek", "TUESDAY").containsEntry("enrolled", 45)
                .containsEntry("startDate", seeded.start());
        assertThat(states.previous()).containsEntry("dayOfWeek", "MONDAY").containsEntry("enrolled", 30)
                .containsEntry("startDate", seeded.start());
    }

    @Test
    @DisplayName("las columnas de la subclase hermana quedan en null (LEFT JOIN) en un evento recurrente")
    void siblingSubclassColumnsAreNull() {
        AuditedEntity academicEvent = entity("AcademicEvent");
        SeededEvent seeded = seedEvent(1);
        RecordRevision key = new RecordRevision(academicEvent, seeded.eventId().toString(),
                lastRevision(academicEvent, seeded.eventId()));

        RecordStates states = stateRepository.load(List.of(key)).get(key);

        assertThat(states).isNotNull();
        assertThat(states.current()).containsEntry("date", null).containsEntry("kind", null)
                .containsEntry("dayOfWeek", "MONDAY");
    }

    @Test
    @DisplayName("un identificador String (Setting) se resuelve y trae el valor previo")
    void loadsStringIdEntity() {
        AuditedEntity setting = entity("Setting");
        SettingKey key = SettingKey.PREVIEW_TTL_MINUTES;
        String original = settingsStore.getRaw(key);
        int first = Integer.parseInt(original) + 1;
        try {
            inTransaction(() -> settingsStore.write(key, String.valueOf(first)));
            inTransaction(() -> settingsStore.write(key, String.valueOf(first + 1)));
            RecordRevision revision = new RecordRevision(setting, key.getKey(), lastRevision(setting, key.getKey()));

            RecordStates states = stateRepository.load(List.of(revision)).get(revision);

            assertThat(states.current()).containsEntry("value", String.valueOf(first + 1));
            assertThat(states.previous()).containsEntry("value", String.valueOf(first));
        } finally {
            inTransaction(() -> settingsStore.write(key, original));
        }
    }

    @Test
    @DisplayName("resuelve en una llamada claves de entidades distintas, cada una con su estado")
    void loadsKeysOfDifferentEntitiesInOneCall() {
        AuditedEntity occurrence = entity("Occurrence");
        AuditedEntity academicEvent = entity("AcademicEvent");
        SeededEvent seeded = seedEvent(1);
        Long occurrenceId = seeded.occurrenceIds().getFirst();
        RecordRevision occurrenceKey = new RecordRevision(occurrence, occurrenceId.toString(),
                lastRevision(occurrence, occurrenceId));
        RecordRevision eventKey = new RecordRevision(academicEvent, seeded.eventId().toString(),
                lastRevision(academicEvent, seeded.eventId()));

        Map<RecordRevision, RecordStates> result = stateRepository.load(List.of(occurrenceKey, eventKey));

        assertThat(result).containsOnlyKeys(occurrenceKey, eventKey);
        assertThat(result.get(occurrenceKey).current()).containsEntry("status", "NEEDS_ROOM");
        assertThat(result.get(eventKey).current()).containsEntry("dayOfWeek", "MONDAY");
    }

    @Test
    @DisplayName("una clave sin fila de auditoría no aparece en el resultado y no afecta a las demás")
    void keyWithoutAuditRowIsAbsent() {
        AuditedEntity occurrence = entity("Occurrence");
        Long occurrenceId = seedEvent(1).occurrenceIds().getFirst();
        int revision = lastRevision(occurrence, occurrenceId);
        RecordRevision present = new RecordRevision(occurrence, occurrenceId.toString(), revision);
        RecordRevision wrongRevision = new RecordRevision(occurrence, occurrenceId.toString(), revision + 100_000);
        RecordRevision unknownId = new RecordRevision(occurrence, "999999999", revision);

        Map<RecordRevision, RecordStates> result = stateRepository.load(List.of(present, wrongRevision, unknownId));

        assertThat(result).containsOnlyKeys(present);
    }

    @Test
    @DisplayName("sin claves devuelve un mapa vacío")
    void emptyKeysReturnEmptyMap() {
        assertThat(stateRepository.load(List.of())).isEmpty();
    }

    @Test
    @DisplayName("ejecuta una sola consulta por entidad aunque haya muchas claves")
    void runsOneStatementPerEntityWithoutNPlusOne() {
        AuditedEntity occurrence = entity("Occurrence");
        AuditedEntity academicEvent = entity("AcademicEvent");
        SeededEvent seeded = seedEvent(8);
        List<RecordRevision> occurrenceKeys = seeded.occurrenceIds().stream()
                .map(id -> new RecordRevision(occurrence, id.toString(), lastRevision(occurrence, id))).toList();
        RecordRevision eventKey = new RecordRevision(academicEvent, seeded.eventId().toString(),
                lastRevision(academicEvent, seeded.eventId()));
        AtomicInteger statements = new AtomicInteger();
        AuditRecordStateRepository counting = new AuditRecordStateRepository(
                new NamedParameterJdbcTemplate(countingDataSource(dataSource, statements)));

        Map<RecordRevision, RecordStates> oneEntity = counting.load(occurrenceKeys);
        assertThat(oneEntity).hasSize(8);
        assertThat(statements.get()).as("8 claves de una entidad").isEqualTo(1);

        statements.set(0);
        List<RecordRevision> both = new ArrayList<>(occurrenceKeys);
        both.add(eventKey);
        Map<RecordRevision, RecordStates> twoEntities = counting.load(both);
        assertThat(twoEntities).hasSize(9);
        assertThat(statements.get()).as("9 claves de dos entidades").isEqualTo(2);
    }

    @Test
    @DisplayName("el contador de statements detecta una consulta: una llamada directa del JdbcTemplate suma 1")
    void statementCounterCountsQueries() {
        AtomicInteger statements = new AtomicInteger();
        new JdbcTemplate(countingDataSource(dataSource, statements)).queryForList("SELECT 1");

        assertThat(statements.get()).isEqualTo(1);
    }

    private static final Set<String> STATEMENT_FACTORIES = Set.of("prepareStatement", "createStatement", "prepareCall");

    private static DataSource countingDataSource(DataSource delegate, AtomicInteger statements) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    Object result = call(delegate, method, args);
                    return method.getName().equals("getConnection")
                            ? countingConnection((Connection) result, statements) : result;
                });
    }

    private static Connection countingConnection(Connection delegate, AtomicInteger statements) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (STATEMENT_FACTORIES.contains(method.getName())) {
                        statements.incrementAndGet();
                    }
                    return call(delegate, method, args);
                });
    }

    private static Object call(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
