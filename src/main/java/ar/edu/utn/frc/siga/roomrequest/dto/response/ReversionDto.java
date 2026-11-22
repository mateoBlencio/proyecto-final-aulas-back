package ar.edu.utn.frc.siga.roomrequest.dto.response;

import java.util.List;

/** Resultado de volver las clases al aula original al reabrir un pedido: qué fechas volvieron y cuáles quedaron. */
public record ReversionDto(List<RevertedDateDto> reverted, List<KeptDateDto> kept) {}
