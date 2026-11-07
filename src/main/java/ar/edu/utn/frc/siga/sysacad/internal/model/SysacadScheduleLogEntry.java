package ar.edu.utn.frc.siga.sysacad.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "sysacad_horario_registro")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SysacadScheduleLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_registro")
    private Long id;

    @Column(name = "id_horario", nullable = false, updatable = false)
    private Long scheduleId;

    @Column(name = "fecha", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40, updatable = false)
    private ScheduleLogType type;

    @Column(name = "resultado", length = 40)
    private String result;

    @Column(name = "motivo", length = 255)
    private String reason;

    @Column(name = "edificio_anterior")
    private Integer previousBuilding;

    @Column(name = "aula_anterior")
    private Integer previousRoom;

    @Column(name = "edificio_nuevo")
    private Integer newBuilding;

    @Column(name = "aula_nuevo")
    private Integer newRoom;

    @Column(name = "hora_enviada")
    private LocalTime sentStartTime;

    @Column(name = "duracion_enviada")
    private Integer sentDurationMinutes;

    @Column(name = "hora_leida")
    private LocalTime readStartTime;

    @Column(name = "duracion_leida")
    private Integer readDurationMinutes;

    @Column(name = "hora_fin_leida")
    private LocalTime readEndTime;

    @Column(name = "hora_releida")
    private LocalTime rereadStartTime;

    @Column(name = "duracion_releida")
    private Integer rereadDurationMinutes;

    @Column(name = "hora_fin_releida")
    private LocalTime rereadEndTime;

    @Column(name = "intentos")
    private Integer attempts;

    @Column(name = "error_id", length = 100)
    private String errorId;

    @Column(name = "respuesta", columnDefinition = "text")
    private String response;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ocurrencias", columnDefinition = "jsonb")
    private String occurrences;

    @Column(name = "id_ocurrencia")
    private Long occurrenceId;

    @Column(name = "id_item")
    private Long itemId;

    @Column(name = "usuario_revision", length = 150)
    private String revisionUser;

    @Column(name = "operacion_revision", length = 100)
    private String revisionOperation;

    @Column(name = "resuelto_por", length = 150)
    private String resolvedBy;
}
