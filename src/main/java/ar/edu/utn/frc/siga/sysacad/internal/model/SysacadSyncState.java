package ar.edu.utn.frc.siga.sysacad.internal.model;

import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "sysacad_sync_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysacadSyncState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_sync")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "vista", nullable = false, unique = true, length = 32)
    private SysacadView view;

    @Column(name = "ultimo_sync_ok")
    private Instant lastSuccessAt;

    @Column(name = "filas_afectadas")
    private Integer lastRowsAffected;

    @Column(name = "ultimo_error", length = 1000)
    private String lastError;

    @Column(name = "ultimo_error_en")
    private Instant lastErrorAt;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant updatedAt;
}
