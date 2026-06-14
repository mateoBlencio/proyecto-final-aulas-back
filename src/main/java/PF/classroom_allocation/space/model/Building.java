package PF.classroom_allocation.space.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "edificio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_edificio")
    private Integer id;

    @Column(name = "nombre", unique = true, nullable = false, length = 100)
    private String name;

    @Column(name = "cantidad_pisos", nullable = false)
    private Integer floorCount;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(name = "eliminado", nullable = false)
    private Boolean deleted = false;

    @JsonIgnore
    @OneToMany(mappedBy = "building", fetch = FetchType.LAZY)
    private List<Classroom> classrooms;

}
