package ar.edu.utn.frc.siga.sysacad.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sysacad_envio_estado")
@Getter
@Setter
@NoArgsConstructor
public class SysacadWriteState {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "id")
    private Short id = SINGLETON_ID;

    @Column(name = "ultima_corrida_inicio")
    private Instant lastRunStartedAt;

    @Column(name = "ultima_corrida_fin")
    private Instant lastRunFinishedAt;

    @Column(name = "ultimo_resultado", length = 40)
    private String lastResult;

    @Column(name = "ultima_lectura_vista")
    private Instant lastViewReadAt;

    @Column(name = "caida_desde")
    private Instant outageSince;

    @Column(name = "ultimo_aviso_caida")
    private Instant lastOutageAlertAt;

    @Column(name = "pausado_hasta")
    private Instant pausedUntil;

    @Column(name = "ultimo_error", length = 1000)
    private String lastError;
}
