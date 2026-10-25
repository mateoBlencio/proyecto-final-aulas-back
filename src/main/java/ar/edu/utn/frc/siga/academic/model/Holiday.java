package ar.edu.utn.frc.siga.academic.model;

import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Audited
@Table(name = "feriado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holiday {

    @Id
    @Column(name = "fecha")
    private LocalDate date;

    @Column(name = "descripcion", length = 255)
    private String description;

    @NotAudited
    @Column(name = "sincronizado_en", nullable = false)
    private Instant syncedAt;
}
