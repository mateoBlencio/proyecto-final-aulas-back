package ar.edu.utn.frc.siga.sysacad.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "aviso_autogestion_aula")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AutogestionNotifiedRoom {

    @Id
    @Column(name = "id_ocurrencia")
    private Long occurrenceId;

    @Column(name = "id_aula")
    private Long classroomId;

    @Column(name = "actualizado_en", nullable = false)
    private Instant updatedAt;
}
