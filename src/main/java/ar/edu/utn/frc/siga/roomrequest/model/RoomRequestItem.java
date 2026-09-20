package ar.edu.utn.frc.siga.roomrequest.model;

import ar.edu.utn.frc.siga.common.converter.DurationMinutesConverter;
import ar.edu.utn.frc.siga.common.model.TimestampedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.envers.Audited;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Audited
@Table(name = "solicitud_aula_item",
       uniqueConstraints = @UniqueConstraint(name = "uq_solicitud_item_orden",
                                             columnNames = {"id_solicitud", "orden"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class RoomRequestItem extends TimestampedEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_solicitud", nullable = false)
    private RoomRequest request;

    @Column(name = "orden", nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    @Builder.Default
    private RoomRequestStatus status = RoomRequestStatus.NEW;

    @Column(name = "decidido_por", length = 150)
    private String decidedBy;

    @Column(name = "fecha_decision")
    private LocalDateTime decidedAt;

    @Column(name = "motivo_decision")
    private String decisionReason;

    @Column(name = "id_comision")
    private Long commissionId;

    /** Fecha puntual del pedido. Nula en los tipos que se atan a un día de dictado ({@link #dayOfWeek}). */
    @Column(name = "fecha")
    private LocalDate date;

    /** Día de dictado del pedido. Nulo en los tipos que se atan a una fecha puntual ({@link #date}). */
    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", length = 20)
    private DayOfWeek dayOfWeek;

    /** Evento recurrente del que se derivó día y horario, cuando el tipo usa el cursado. Trazabilidad. */
    @Column(name = "id_evento_recurrente")
    private Long sourceRecurringEventId;

    @Column(name = "hora_inicio")
    private LocalTime startTime;

    @Convert(converter = DurationMinutesConverter.class)
    @Column(name = "duracion_minutos")
    private Duration duration;

    /** Cantidad estimada de asistentes. Nula en los cambios de aula, que no la piden. */
    @Column(name = "cantidad_estimada")
    private Integer estimated;

    @Column(name = "cantidad_aulas", nullable = false)
    @Builder.Default
    private Integer classroomCount = 1;

    @Column(name = "requiere_proyector", nullable = false)
    @Builder.Default
    private Boolean requiresProjector = false;

    @Column(name = "requiere_computadoras", nullable = false)
    @Builder.Default
    private Boolean requiresComputers = false;

    @Column(name = "cantidad_computadoras")
    private Integer computerCount;

    @Column(name = "requiere_usuarios_examen")
    private Boolean requiresExamUsers;

    @Column(name = "software_requerido", length = 255)
    private String requiredSoftware;

    @Column(name = "observaciones", length = 1000)
    private String observations;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<RoomPreference> preferences = new ArrayList<>();

    @Column(name = "id_edificio_derivado")
    private Long derivedBuildingId;

    @Column(name = "fecha_derivacion")
    private LocalDateTime derivedAt;

    @Column(name = "id_edificio_devuelto")
    private Long returnedFromBuildingId;

    @Column(name = "motivo_devolucion")
    private String returnedReason;

    @Column(name = "fecha_notificacion")
    private LocalDateTime notifiedAt;

    /** Lock optimista: dos resoluciones concurrentes sobre el mismo ítem (assign/notify/cancel a la vez) chocan en el commit en vez de pisarse. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<RoomRequestItemAllocation> allocations = new ArrayList<>();

    public LocalTime endTime() {
        return (startTime == null || duration == null) ? null : startTime.plus(duration);
    }

    /** Fuente de verdad de la regla; {@link ar.edu.utn.frc.siga.roomrequest.specification.RoomRequestItemSpecification}
     *  la repite en SQL para poder filtrar sin traer las filas, así que un cambio acá hay que replicarlo ahí. */
    public boolean requiresSpecialAssignment() {
        return Boolean.TRUE.equals(requiresComputers) || requiredSoftware != null
                || Boolean.TRUE.equals(requiresExamUsers);
    }

    void attachTo(RoomRequest request, int position) {
        this.request = request;
        this.position = position;
    }

    /** Trunca a microsegundos: Postgres guarda timestamp con esa precisión, y sin truncar acá la respuesta de la misma llamada (en memoria, con nanosegundos) no coincide byte a byte con una relectura posterior desde la base. */
    public void decide(RoomRequestStatus target, String decidedBy, String reason, LocalDateTime decidedAt) {
        this.status = target;
        this.decidedBy = decidedBy;
        this.decisionReason = reason;
        this.decidedAt = truncateToMicros(decidedAt);
    }

    /** decidedBy/decidedAt trackean la última decisión sea cual sea; el CHECK chk_solicitud_item_decision exige ambos no nulos fuera de NEW. */
    public void deriveTo(Long buildingId, String decidedBy, LocalDateTime derivedAt) {
        LocalDateTime truncated = truncateToMicros(derivedAt);
        this.status = RoomRequestStatus.DERIVED_TO_BUILDING;
        this.derivedBuildingId = buildingId;
        this.derivedAt = truncated;
        this.decidedBy = decidedBy;
        this.decidedAt = truncated;
    }

    /** No toca decisionReason: preserva el motivo de resolución parcial que haya dejado el último assign. */
    public void resolve(String decidedBy, LocalDateTime notifiedAt) {
        LocalDateTime truncated = truncateToMicros(notifiedAt);
        this.status = RoomRequestStatus.RESOLVED;
        this.decidedBy = decidedBy;
        this.decidedAt = truncated;
        this.notifiedAt = truncated;
    }

    private static LocalDateTime truncateToMicros(LocalDateTime value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    public void returnFromBuilding(String reason) {
        this.status = RoomRequestStatus.NEW;
        this.returnedFromBuildingId = this.derivedBuildingId;
        this.returnedReason = reason;
        this.derivedBuildingId = null;
        this.derivedAt = null;
    }

    /** classroomIds[i] cubre occurrencesBySlot.get(i): una fila por ocurrencia real, orden = i+1. */
    /** Actualiza en el lugar la fila cuya ocurrencia se reutiliza (p. ej. la principal en una reasignación): borrar y recrear con la misma id_ocurrencia viola el UNIQUE(id_item, id_ocurrencia) por el orden de flush de Hibernate. */
    public void assignClassrooms(List<Long> classroomIds, List<List<Long>> occurrencesBySlot) {
        Map<Long, RoomRequestItemAllocation> existingByOccurrence = allocations.stream()
                .collect(java.util.stream.Collectors.toMap(RoomRequestItemAllocation::getOccurrenceId, a -> a));
        Set<Long> keptOccurrenceIds = new HashSet<>();

        for (int i = 0; i < classroomIds.size(); i++) {
            Long classroomId = classroomIds.get(i);
            int position = i + 1;
            for (Long occurrenceId : occurrencesBySlot.get(i)) {
                keptOccurrenceIds.add(occurrenceId);
                RoomRequestItemAllocation existing = existingByOccurrence.get(occurrenceId);
                if (existing != null) {
                    existing.update(classroomId, position);
                } else {
                    allocations.add(RoomRequestItemAllocation.builder()
                            .item(this)
                            .occurrenceId(occurrenceId)
                            .classroomId(classroomId)
                            .position(position)
                            .build());
                }
            }
        }
        allocations.removeIf(a -> !keptOccurrenceIds.contains(a.getOccurrenceId()));
    }

    public void addPreferences(List<Long> classroomIds) {
        classroomIds.forEach(this::addPreference);
    }

    public void addPreference(Long classroomId) {
        preferences.add(RoomPreference.builder()
                .item(this)
                .classroomId(classroomId)
                .position(preferences.size() + 1)
                .build());
    }
}
