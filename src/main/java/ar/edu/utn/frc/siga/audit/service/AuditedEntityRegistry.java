package ar.edu.utn.frc.siga.audit.service;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.boot.internal.EnversService;
import org.hibernate.metamodel.mapping.SelectableMapping;
import org.hibernate.persister.entity.AbstractEntityPersister;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity.AuditedColumn;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditedEntityRegistry {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("Allocation", "Asignación"),
            Map.entry("User", "Usuario"),
            Map.entry("RoleAssignment", "Asignación de rol"),
            Map.entry("AcademicEvent", "Evento académico"),
            Map.entry("Occurrence", "Ocurrencia"),
            Map.entry("RoomRequest", "Solicitud de aula"),
            Map.entry("RoomRequestItem", "Ítem de solicitud de aula"),
            Map.entry("RoomPreference", "Preferencia de aula"),
            Map.entry("RoomRequestItemAllocation", "Asignación de solicitud de aula"),
            Map.entry("Setting", "Configuración"),
            Map.entry("AuditArchiveRun", "Archivado de auditoría"));

    // Audited properties never shown in the diff.
    private static final Map<String, Set<String>> EXCLUDED_PROPERTIES = Map.of(
            "User", Set.of("passwordHash"));

    private static final Set<String> ENVERS_COLUMNS = Set.of("rev", "revtype");

    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;

    private List<AuditedEntity> entities = List.of();

    @PostConstruct
    void discover() {
        Set<Class<?>> auditedTypes = entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .filter(type -> type.isAnnotationPresent(Audited.class))
                .collect(Collectors.toSet());

        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);

        entities = entityManager.getMetamodel().getEntities().stream()
                .filter(type -> {
                    Class<?> javaType = type.getJavaType();
                    return javaType.isAnnotationPresent(Audited.class)
                            && !hasAuditedAncestor(javaType, auditedTypes);
                })
                .map(type -> toAuditedEntity(sessionFactory, type, auditedTypes))
                .sorted(Comparator.comparing(AuditedEntity::label))
                .toList();

        log.info("Entidades auditadas descubiertas: {}",
                entities.stream().map(AuditedEntity::jpaName).toList());
    }

    public List<AuditedEntity> all() {
        return entities;
    }

    /** Position in {@link #all()}, which is sorted by label. */
    public int indexOf(AuditedEntity entity) {
        return entities.indexOf(entity);
    }

    public Optional<AuditedEntity> byLabel(String label) {
        return entities.stream().filter(entity -> entity.label().equals(label)).findFirst();
    }

    private static AuditedEntity toAuditedEntity(SessionFactoryImplementor sessionFactory, EntityType<?> type,
                                                 Set<Class<?>> auditedTypes) {
        Class<?> javaType = type.getJavaType();
        EnversService envers = sessionFactory.getServiceRegistry().getService(EnversService.class);
        AbstractEntityPersister auditPersister = auditPersister(sessionFactory, envers, javaType);
        String auditTable = auditPersister.getTableName();

        List<String> idColumns = new ArrayList<>();
        var identifierMapping = sessionFactory.getMappingMetamodel().getEntityDescriptor(javaType).getIdentifierMapping();
        identifierMapping.forEachSelectable((index, selectable) -> idColumns.add(((SelectableMapping) selectable).getSelectionExpression()));
        if (idColumns.size() != 1) {
            throw new IllegalStateException("La entidad auditada " + javaType.getName()
                    + " debe tener un identificador de una sola columna, tiene " + idColumns);
        }
        String idColumn = idColumns.getFirst();

        String jpaName = type.getName();
        Set<String> excluded = EXCLUDED_PROPERTIES.getOrDefault(jpaName, Set.of());
        List<AuditedColumn> columns = new ArrayList<>(columnsOf(auditPersister, idColumn, excluded));
        // JOINED subclasses keep their own columns in their own _aud table.
        auditedTypes.stream()
                .filter(subtype -> subtype != javaType && javaType.isAssignableFrom(subtype))
                .sorted(Comparator.comparing(Class::getName))
                .forEach(subtype -> columns.addAll(
                        columnsOf(auditPersister(sessionFactory, envers, subtype), idColumn, excluded)));

        return new AuditedEntity(javaType, jpaName, labelFor(jpaName), auditTable, idColumn,
                identifierMapping.getJavaType().getJavaTypeClass(), List.copyOf(columns));
    }

    private static AbstractEntityPersister auditPersister(SessionFactoryImplementor sessionFactory,
                                                          EnversService envers, Class<?> javaType) {
        String auditEntityName = envers.getConfig().getAuditEntityName(javaType.getName());
        return (AbstractEntityPersister) sessionFactory.getMappingMetamodel().getEntityDescriptor(auditEntityName);
    }

    private static List<AuditedColumn> columnsOf(AbstractEntityPersister auditPersister, String idColumn,
                                                 Set<String> excluded) {
        List<AuditedColumn> columns = new ArrayList<>();
        auditPersister.getDeclaredAttributeMappings().forEachValue(attribute -> {
            // Envers audits a to-one as a basic property "<property>_id"; the diff uses the Java property name.
            String property = attribute.getAttributeName().endsWith("_id")
                    ? attribute.getAttributeName().substring(0, attribute.getAttributeName().length() - 3)
                    : attribute.getAttributeName();
            if (excluded.contains(property)) {
                return;
            }
            attribute.forEachSelectable((index, selectable) -> {
                String column = selectable.getSelectionExpression();
                if (!column.equals(idColumn) && !ENVERS_COLUMNS.contains(column.toLowerCase())) {
                    columns.add(new AuditedColumn(selectable.getContainingTableExpression(), column, property));
                }
            });
        });
        return columns;
    }

    private static boolean hasAuditedAncestor(Class<?> type, Set<Class<?>> auditedTypes) {
        for (Class<?> ancestor = type.getSuperclass(); ancestor != null; ancestor = ancestor.getSuperclass()) {
            if (auditedTypes.contains(ancestor)) {
                return true;
            }
        }
        return false;
    }

    private static String labelFor(String jpaName) {
        return LABELS.getOrDefault(jpaName, jpaName);
    }
}
