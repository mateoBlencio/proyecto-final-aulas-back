package ar.edu.utn.frc.siga.roomrequest.model;

import ar.edu.utn.frc.siga.common.converter.DurationMinutesConverter;
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

/**
 * Un pedido concreto dentro de una solicitud: una comisión, una fecha, un horario.
 * El formulario permite cargar varios ("cantidad de pedidos").
 *
 * <p><b>Es la unidad de decisión.</b> Cada pedido lleva su propio estado, porque
 * los pedidos de una misma solicitud pueden estar separados por meses: se puede
 * pre-aprobar el parcial de abril y dejar pendiente el recuperatorio de julio,
 * que todavía no se puede resolver.
 *
 * <p>Guarda duración y no hora de fin, para que traducirlo a un evento académico
 * sea directo ({@code AcademicEvent} también guarda inicio + duración). La
 * conversión desde la hora de fin que carga el docente se hace en el DTO.
 */
@Entity
@Table(name = "solicitud_aula_item",
       uniqueConstraints = @UniqueConstraint(name = "uq_solicitud_item_orden",
                                             columnNames = {"id_solicitud", "orden"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RoomRequestItem {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_solicitud", nullable = false)
    private RoomRequest request;

    /** Posición dentro de la solicitud, arrancando en 1. */
    @Column(name = "orden", nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    @Builder.Default
    private RoomRequestStatus status = RoomRequestStatus.PENDING;

    /** Email del usuario que decidió. No es FK: {@code auth} no expone fachada de usuarios. */
    @Column(name = "decidido_por", length = 150)
    private String decidedBy;

    @Column(name = "fecha_decision")
    private LocalDateTime decidedAt;

    @Column(name = "motivo_decision")
    private String decisionReason;

    /** ID plano: la comisión vive en {@code academic}. Null para conferencias. */
    @Column(name = "id_comision")
    private Long commissionId;

    @Column(name = "fecha", nullable = false)
    private LocalDate date;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime startTime;

    @Convert(converter = DurationMinutesConverter.class)
    @Column(name = "duracion_minutos", nullable = false)
    private Duration duration; // aca en caso de tratarse de type clase (unica vez o recurrente) podriamos sacar los datos de sysacad

    @Column(name = "cantidad_inscriptos", nullable = false)
    private Integer enrolled; // a chequear si debe seguir funcionado, si nosotros ya tenemos los datos desde sysacad

    @Column(name = "cantidad_estimada", nullable = false)
    private Integer estimated;

    /** Aulas necesarias en simultáneo para la misma franja. */
    @Column(name = "cantidad_aulas", nullable = false)
    @Builder.Default
    private Integer classroomCount = 1;

    /** ID plano: el aula vive en {@code space}. Dónde cursa hoy. */
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

    /** Listado corto ("Office, MATLAB"), no un párrafo: alcanza con el default de 255. */
    @Column(name = "software_requerido", length = 255)
    private String requiredSoftware;

    /**
     * Texto libre del docente. El largo va explícito y no con el default de 255
     * porque para las solicitudes de tipo {@code OTHER} es el <b>único</b> campo
     * que describe el pedido, así que un párrafo entero es el caso esperado.
     * El {@code @Size} espejo en el DTO es el que convierte pasarse de largo en
     * un 400 en vez de un 500 al insertar.
     */
    @Column(name = "observaciones", length = 1000)
    private String observations;

    /**
     * No entra en el entity graph de la solicitud: fetchear dos bags en la
     * misma query rompe con {@code MultipleBagFetchException}. El
     * {@code @BatchSize} hace que cargarlas para todos los ítems de una
     * solicitud sea una query y no una por ítem.
     */
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<RoomPreference> preferences = new ArrayList<>();

    public LocalTime endTime() {
        return startTime.plus(duration);
    }

    void attachTo(RoomRequest request, int position) {
        this.request = request;
        this.position = position;
    }

    /**
     * Registra una decisión sobre este pedido. La legalidad de la transición
     * la valida {@code RoomRequestValidator} antes de llamar acá.
     */
    public void decide(RoomRequestStatus target, String decidedBy, String reason, LocalDateTime decidedAt) {
        this.status = target;
        this.decidedBy = decidedBy;
        this.decisionReason = reason;
        this.decidedAt = decidedAt;
    }

    /**
     * Agrega varias aulas de preferencia respetando el orden recibido, que es
     * el orden de prioridad que declaró el docente. "Sin preferencias" es una
     * lista vacía, no {@code null}: normalizarlo es tarea del DTO de entrada.
     */
    public void addPreferences(List<Integer> classroomIds) {
        classroomIds.forEach(this::addPreference);
    }

    /** Agrega un aula de preferencia al final del orden de prioridad. */
    public void addPreference(Integer classroomId) {
        preferences.add(RoomPreference.builder()
                .item(this)
                .classroomId(classroomId)
                .position(preferences.size() + 1)
                .build());
    }
}
