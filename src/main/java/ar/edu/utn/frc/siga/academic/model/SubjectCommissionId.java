package ar.edu.utn.frc.siga.academic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SubjectCommissionId implements Serializable {

    @Column(name = "id_materia")
    private Long subjectId;

    @Column(name = "id_comision")
    private Long commissionId;
}
