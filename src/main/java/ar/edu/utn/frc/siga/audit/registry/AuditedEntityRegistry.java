package ar.edu.utn.frc.siga.audit.registry;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.Audited;
import org.springframework.stereotype.Component;

import java.util.Collection;
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
            "AcademicEvent", "Evento académico",
            "Occurrence", "Ocurrencia",
            "RoomRequest", "Solicitud de aula",
            "RoomRequestItem", "Ítem de solicitud de aula",
            "RoomPreference", "Preferencia de aula",
            "Setting", "Configuración");

    private final EntityManager entityManager;

    private List<AuditedEntity> entities = List.of();

    @PostConstruct
    void discover() {
        Set<Class<?>> auditedTypes = entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .filter(type -> type.isAnnotationPresent(Audited.class))
                .collect(Collectors.toSet());

        entities = entityManager.getMetamodel().getEntities().stream()
                .filter(type -> {
                    Class<?> javaType = type.getJavaType();
                    return javaType.isAnnotationPresent(Audited.class)
                            && !hasAuditedAncestor(javaType, auditedTypes);
                })
                .map(type -> new AuditedEntity(type.getJavaType(), type.getName(), labelFor(type.getName())))
                .sorted(Comparator.comparing(AuditedEntity::label))
                .toList();

        log.info("Entidades auditadas descubiertas: {}",
                entities.stream().map(AuditedEntity::jpaName).toList());
    }

    public Collection<AuditedEntity> all() {
        return entities;
    }

    public Optional<AuditedEntity> byLabel(String label) {
        return entities.stream().filter(entity -> entity.label().equals(label)).findFirst();
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
