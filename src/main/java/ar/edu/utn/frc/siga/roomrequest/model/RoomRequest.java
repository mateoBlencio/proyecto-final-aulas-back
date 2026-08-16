package ar.edu.utn.frc.siga.roomrequest.model;

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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cabecera de una solicitud de aula: quién pide, de qué tipo y sobre qué materia.
 * El detalle concreto (fechas, comisiones, cantidades) vive en {@link RoomRequestItem}.
 *
 * <p>La cabecera existe para que el docente no repita sus datos en cada pedido:
 * es contexto compartido, no una unidad de decisión. <b>No tiene estado</b> —
 * cada {@link RoomRequestItem} se decide por separado, porque los pedidos de una
 * misma solicitud pueden ocurrir con meses de diferencia (parcial 1 en abril,
 * recuperatorio en julio) y subsecretaría no puede resolverlos al mismo tiempo.
 */
@Entity
@Table(name = "solicitud_aula")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RoomRequest {

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

    /** ID plano: la materia vive en {@code academic} (ADR-004). Null para conferencias. */
    @Column(name = "id_materia")
    private Long subjectId;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime createdAt;

    /**
     * ID del ticket que representa esta solicitud en GLPI. Null hasta que el
     * ticket se crea con éxito; una solicitud vive perfectamente sin él.
     * <p>
     * Es {@code Long} y no {@code Integer} porque {@code glpi_tickets.id} es
     * {@code int unsigned} en GLPI, cuyo tope (4.294.967.295) no entra en un
     * {@code Integer} de Java. El estado técnico del envío (intentos, errores,
     * reintentos) <b>no</b> vive acá: ver {@code plans/roomRequest/05-glpi.md}.
     */
    @Column(name = "id_glpi", unique = true)
    private Long glpiTicketId;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @Builder.Default
    private List<RoomRequestItem> items = new ArrayList<>();

    /** Agrega un ítem al final y le asigna la posición que le corresponde. */
    public void addItem(RoomRequestItem item) {
        item.attachTo(this, items.size() + 1);
        items.add(item);
    }

    /**
     * Busca un pedido propio por ID. La cabecera es la raíz del agregado: los
     * ítems se alcanzan siempre a través de ella, nunca por su cuenta.
     */
    public Optional<RoomRequestItem> findItem(Long itemId) {
        return items.stream().filter(item -> item.getId().equals(itemId)).findFirst();
    }
}
