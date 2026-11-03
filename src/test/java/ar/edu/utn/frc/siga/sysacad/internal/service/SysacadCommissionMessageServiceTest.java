package ar.edu.utn.frc.siga.sysacad.internal.service;

import ar.edu.utn.frc.siga.sysacad.dto.request.SendCommissionMessageRequest;
import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.CommissionViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.exception.CommissionMessageFailedException;
import ar.edu.utn.frc.siga.sysacad.internal.exception.CommissionMessageNotAllowedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("SysacadCommissionMessageService: envío limitado a la comisión TEST de una especialidad")
class SysacadCommissionMessageServiceTest {

    private static final RawCommission TARGET = new RawCommission("TEST", 5, 2008, 581, 2026, 77);

    private SysacadClient client;
    private CommissionViewFetcher fetcher;
    private SysacadCommissionMessageService service;

    @BeforeEach
    void setUp() {
        client = mock(SysacadClient.class);
        fetcher = mock(CommissionViewFetcher.class);
        SysacadProperties properties = new SysacadProperties();
        properties.setAutogestion(new SysacadProperties.Autogestion(true, "TEST", 5, 581));
        service = new SysacadCommissionMessageService(client, fetcher, properties);
    }

    private static SendCommissionMessageRequest request(String courseCode, int specialty, int subject) {
        return new SendCommissionMessageRequest(
                100, "", LocalDate.of(2026, 11, 30), 2026, specialty, subject, courseCode, "prueba");
    }

    @Test
    @DisplayName("el curso y especialidad habilitados resuelven plan, materia y comisión desde SysAcad y envían")
    void sendsWithIdsResolvedFromSysacad() {
        when(fetcher.fetch()).thenReturn(List.of(
                new RawCommission("TEST", 27, 2008, 581, 2026, 78),
                new RawCommission("TEST", 5, 2008, 600, 2026, 79),
                new RawCommission("1K1", 5, 2008, 519, 2026, 1),
                TARGET));

        service.send(request(" test ", 5, 581));

        verify(client).executeProcedure(eq("EnviarMensajeComision"), eq(Map.of(
                "@LegajoPublica", 100,
                "@IdTipoUsuarioPublica", "",
                "@FechaHasta", "2026-11-30",
                "@AnioAcademico", 2026,
                "@Especialidad", 5,
                "@Plan", 2008,
                "@Materia", 581,
                "@Comision", 77,
                "@Nota", "prueba")));
    }

    @Test
    @DisplayName("otro curso u otra especialidad se rechaza sin consultar ni escribir en SysAcad")
    void rejectsOtherTargetWithoutCallingSysacad() {
        assertThatThrownBy(() -> service.send(request("1K1", 5, 581)))
                .isInstanceOf(CommissionMessageNotAllowedException.class);
        assertThatThrownBy(() -> service.send(request("TEST", 27, 581)))
                .isInstanceOf(CommissionMessageNotAllowedException.class);
        assertThatThrownBy(() -> service.send(request("TEST", 5, 600)))
                .isInstanceOf(CommissionMessageNotAllowedException.class);

        verifyNoInteractions(client, fetcher);
    }

    @Test
    @DisplayName("sin coincidencia o con más de una comisión en SysAcad no envía")
    void rejectsWhenNotExactlyOneMatch() {
        when(fetcher.fetch()).thenReturn(List.of());
        assertThatThrownBy(() -> service.send(request("TEST", 5, 581)))
                .isInstanceOf(CommissionMessageNotAllowedException.class);

        when(fetcher.fetch()).thenReturn(List.of(TARGET, new RawCommission("TEST", 5, 2009, 581, 2026, 78)));
        assertThatThrownBy(() -> service.send(request("TEST", 5, 581)))
                .isInstanceOf(CommissionMessageNotAllowedException.class);

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("un error HTTP de SysAcad se informa como CommissionMessageFailedException")
    void wrapsSysacadHttpErrors() {
        when(fetcher.fetch()).thenReturn(List.of(TARGET));
        doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
                .when(client).executeProcedure(any(), any());

        assertThatThrownBy(() -> service.send(request("TEST", 5, 581)))
                .isInstanceOf(CommissionMessageFailedException.class);
    }
}
