package ar.edu.utn.frc.siga.sysacad.internal.service;

import ar.edu.utn.frc.siga.sysacad.dto.request.SendCommissionMessageRequest;
import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.exception.CommissionMessageFailedException;
import ar.edu.utn.frc.siga.sysacad.internal.exception.CommissionMessageNotAllowedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("SysacadCommissionMessageService: envío limitado a la comisión habilitada")
class SysacadCommissionMessageServiceTest {

    private SysacadClient client;
    private SysacadCommissionMessageService service;

    @BeforeEach
    void setUp() {
        client = mock(SysacadClient.class);
        SysacadProperties properties = new SysacadProperties();
        properties.setAutogestion(new SysacadProperties.Autogestion(true, 1, 2, 3, 4));
        service = new SysacadCommissionMessageService(client, properties);
    }

    private static SendCommissionMessageRequest request(int specialty, int plan, int subject, int commission) {
        return new SendCommissionMessageRequest(
                100, "", LocalDate.of(2026, 11, 30), 2026, specialty, plan, subject, commission, "prueba");
    }

    @Test
    @DisplayName("el destino configurado ejecuta EnviarMensajeComision con los parámetros de SysAcad")
    void sendsToConfiguredTarget() {
        service.send(request(1, 2, 3, 4));

        verify(client).executeProcedure(eq("EnviarMensajeComision"), eq(Map.of(
                "@LegajoPublica", 100,
                "@IdTipoUsuarioPublica", "",
                "@FechaHasta", "2026-11-30",
                "@AnioAcademico", 2026,
                "@Especialidad", 1,
                "@Plan", 2,
                "@Materia", 3,
                "@Comision", 4,
                "@Nota", "prueba")));
    }

    @Test
    @DisplayName("cualquier id distinto del configurado se rechaza sin llamar a SysAcad")
    void rejectsAnyOtherTarget() {
        for (SendCommissionMessageRequest other : new SendCommissionMessageRequest[] {
                request(9, 2, 3, 4), request(1, 9, 3, 4), request(1, 2, 9, 4), request(1, 2, 3, 9)}) {
            assertThatThrownBy(() -> service.send(other)).isInstanceOf(CommissionMessageNotAllowedException.class);
        }

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("un error HTTP de SysAcad se informa como CommissionMessageFailedException")
    void wrapsSysacadHttpErrors() {
        doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
                .when(client).executeProcedure(any(), any());

        assertThatThrownBy(() -> service.send(request(1, 2, 3, 4)))
                .isInstanceOf(CommissionMessageFailedException.class);
    }
}
