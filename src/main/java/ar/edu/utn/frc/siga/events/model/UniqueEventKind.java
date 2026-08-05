package ar.edu.utn.frc.siga.events.model;

import org.springframework.modulith.NamedInterface;

/** Tipo de actividad de un {@link UniqueEvent}: parcial, trabajo práctico, examen final u otro. */
@NamedInterface("api")
public enum UniqueEventKind {
    PARCIAL, TRABAJO_PRACTICO, EXAMEN_FINAL, OTRO
}
