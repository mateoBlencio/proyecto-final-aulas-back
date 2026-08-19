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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
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
    private RoomRequestStatus status = RoomRequestStatus.PENDING;

    @Column(name = "decidido_por", length = 150)
    private String decidedBy;

    @Column(name = "fecha_decision")
    private LocalDateTime decidedAt;

    @Column(name = "motivo_decision")
    private String decisionReason;

    @Column(name = "id_comision")
    private Long commissionId;

    @Column(name = "fecha", nullable = false)
    private LocalDate date;

    @Column(name = "hora_inicio")
    private LocalTime startTime;

    @Convert(converter = DurationMinutesConverter.class)
    @Column(name = "duracion_minutos")
    private Duration duration; // aca en caso de tratarse de type clase (unica vez o recurrente) podriamos sacar los datos de sysacad

    @Column(name = "cantidad_inscriptos")
    private Integer enrolled; // a chequear si debe seguir funcionado, si nosotros ya tenemos los datos desde sysacad

    @Column(name = "cantidad_estimada", nullable = false)
    private Integer estimated;

    @Column(name = "cantidad_aulas", nullable = false)
    @Builder.Default
    private Integer classroomCount = 1;

    @Column(name = "id_aula_actual")
    private Integer currentClassroomId;

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

    public LocalTime endTime() {
        return (startTime == null || duration == null) ? null : startTime.plus(duration);
    }

    void attachTo(RoomRequest request, int position) {
        this.request = request;
        this.position = position;
    }

    public void decide(RoomRequestStatus target, String decidedBy, String reason, LocalDateTime decidedAt) {
        this.status = target;
        this.decidedBy = decidedBy;
        this.decisionReason = reason;
        this.decidedAt = decidedAt;
    }

    public void addPreferences(List<Integer> classroomIds) {
        classroomIds.forEach(this::addPreference);
    }

    public void addPreference(Integer classroomId) {
        preferences.add(RoomPreference.builder()
                .item(this)
                .classroomId(classroomId)
                .position(preferences.size() + 1)
                .build());
    }
}
