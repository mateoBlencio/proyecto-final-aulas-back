package ar.edu.utn.frc.classroom_allocation.schedule.model;

import ar.edu.utn.frc.classroom_allocation.course.model.SubjectCommission;
import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "asignacion_aula")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassroomAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_asignacion")
    private Long id;

    @Column(name = "estado", nullable = false)
    private String status;

    @Column(name = "fecha_creacion", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "fecha_desde")
    private LocalDate startDate;

    @Column(name = "fecha_hasta")
    private LocalDate endDate;

    @ManyToOne
    @JoinColumn(name = "id_aula", nullable = false)
    private Classroom classroom;

    @ManyToOne
    @JoinColumn(name = "id_franja", nullable = false)
    private TimeSlot timeSlot;

    @ManyToOne
    @JoinColumn(name = "id_materia_comision", nullable = false)
    private SubjectCommission subjectCommission;

    @Column(name = "id_usuario_creador")
    private Long createdBy;

    @Column(name = "id_usuario_aprobador")
    private Long approvedBy;

    @Column(name = "observaciones")
    private String comments;

    @Column(name = "tipo_asignacion", nullable = false)
    private String assignmentType;
}
