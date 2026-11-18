package ar.edu.utn.frc.siga.roomrequest.dto.response;

import java.util.List;

/**
 * {@code computerCount}: "Cantidad de PC" del aula; {@code null} si el pedido no pide computadoras o el aula no
 * tiene el recurso. {@code missingRequirements}: {@code PROJECTOR} o {@code COMPUTERS} que el aula no cumple;
 * vacío si cumple todo y siempre vacío fuera del alta directa. {@code current}: el aula donde la comisión cursa hoy
 * la clase que el pedido quiere cambiar; siempre {@code false} en los pedidos que crean un evento nuevo y en el
 * alta directa.
 */
public record AllowedClassroomDto(
        Long id,
        Integer roomNumber,
        Long buildingId,
        String buildingName,
        Integer capacity,
        boolean available,
        Integer computerCount,
        List<String> missingRequirements,
        boolean current,
        boolean enabledInSysacad
) {}
