package ar.edu.utn.frc.siga.roomrequest.model;

import ar.edu.utn.frc.siga.common.model.TimestampedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "solicitud_aula")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class RoomRequest extends TimestampedEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_solicitud", nullable = false, length = 40)
    private RoomRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "ambito", nullable = false, length = 30)
    private AcademicScope scope;

    @Column(name = "docente_nombre", nullable = false, length = 150)
    private String teacherName;

    @Column(name = "docente_email", nullable = false, length = 150)
    private String teacherEmail;

    @Column(name = "docente_telefono", nullable = false, length = 40)
    private String teacherPhone;

    @Column(name = "id_materia")
    private Long subjectId;

    @Column(name = "id_glpi", unique = true)
    private Long glpiTicketId;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @Builder.Default
    private List<RoomRequestItem> items = new ArrayList<>();

    public void addItem(RoomRequestItem item) {
        item.attachTo(this, items.size() + 1);
        items.add(item);
    }

    public Optional<RoomRequestItem> findItem(Long itemId) {
        return items.stream().filter(item -> item.getId().equals(itemId)).findFirst();
    }
}
