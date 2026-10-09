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

    private static final Map<String, String> LABELS = Map.of(
            "Allocation", "Asignación",
            "User", "Usuario",
            "RoleAssignment", "Asignación de rol",
            "AcademicEvent", "Evento académico",
            "Occurrence", "Ocurrencia",
            "RoomRequest", "Solicitud de aula",
            "RoomRequestItem", "Ítem de solicitud de aula",
            "RoomPreference", "Preferencia de aula",
            "RoomRequestItemAllocation", "Asignación de solicitud de aula",
            "Setting", "Configuración");

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
                .map(type -> toAuditedEntity(sessionFactory, type))
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

    private static AuditedEntity toAuditedEntity(SessionFactoryImplementor sessionFactory, EntityType<?> type) {
        Class<?> javaType = type.getJavaType();
        String auditEntityName = sessionFactory.getServiceRegistry().getService(EnversService.class)
                .getConfig().getAuditEntityName(javaType.getName());
        String auditTable = ((AbstractEntityPersister) sessionFactory.getMappingMetamodel()
                .getEntityDescriptor(auditEntityName)).getTableName();

        List<String> idColumns = new ArrayList<>();
        sessionFactory.getMappingMetamodel().getEntityDescriptor(javaType).getIdentifierMapping()
                .forEachSelectable((index, selectable) -> idColumns.add(((SelectableMapping) selectable).getSelectionExpression()));
        if (idColumns.size() != 1) {
            throw new IllegalStateException("La entidad auditada " + javaType.getName()
                    + " debe tener un identificador de una sola columna, tiene " + idColumns);
        }
        return new AuditedEntity(javaType, type.getName(), labelFor(type.getName()), auditTable, idColumns.getFirst());
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
