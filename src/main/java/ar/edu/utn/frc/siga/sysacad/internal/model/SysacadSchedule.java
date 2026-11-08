package ar.edu.utn.frc.siga.sysacad.internal.model;

import ar.edu.utn.frc.siga.common.model.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "sysacad_horario")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SysacadSchedule extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_horario")
    private Long id;

    @Column(name = "anio", nullable = false)
    private Integer year;

    @Column(name = "especialidad", nullable = false)
    private Integer specialty;

    @Column(name = "plan", nullable = false)
    private Integer plan;

    @Column(name = "materia", nullable = false)
    private Integer subject;

    @Column(name = "comision", nullable = false)
    private Integer commission;

    @Column(name = "dia", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "cuatrimestre", nullable = false)
    private Integer semester;

    @Column(name = "curso")
    private String courseCode;

    @Column(name = "hora_comienzo")
    private LocalTime startTime;

    @Column(name = "hora_fin")
    private LocalTime endTime;

    @Column(name = "duracion")
    private Integer durationMinutes;

    @Column(name = "edificio_conocido")
    private Integer knownBuilding;

    @Column(name = "aula_conocida")
    private Integer knownRoom;

    @Builder.Default
    @Column(name = "presente_en_vista", nullable = false)
    private boolean presentInView = true;

    @Column(name = "ultima_lectura")
    private Instant lastReadAt;

    @Column(name = "edificio_base")
    private Integer baseBuilding;

    @Column(name = "aula_base")
    private Integer baseRoom;

    @Column(name = "id_evento_1")
    private Long eventId1;

    @Column(name = "id_evento_2")
    private Long eventId2;

    @Builder.Default
    @Column(name = "enviable", nullable = false)
    private boolean sendable = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_no_enviable", length = 40)
    private NotSendableReason notSendableReason;

    @Column(name = "edificio_diferente")
    private Integer differentBuilding;

    @Column(name = "aula_diferente")
    private Integer differentRoom;

    @Column(name = "diferencia_detectada_en")
    private Instant differenceDetectedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private SysacadScheduleStatus status = SysacadScheduleStatus.SYNCED;

    @Column(name = "edificio_en_envio")
    private Integer sendingBuilding;

    @Column(name = "aula_en_envio")
    private Integer sendingRoom;

    @Builder.Default
    @Column(name = "intentos", nullable = false)
    private int attempts = 0;

    @Column(name = "proximo_intento")
    private Instant nextAttemptAt;

    @Column(name = "ultimo_error", length = 1000)
    private String lastError;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_revision", length = 40)
    private SysacadReviewReason reviewReason;

    @Column(name = "revision_desde")
    private Instant reviewSince;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
