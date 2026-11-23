package ar.edu.utn.frc.siga.notification.internal.render;

import ar.edu.utn.frc.siga.notification.InformalRegister;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRendererTest {

    private static final String PLAZO = "Si no recibimos respuesta antes del lunes 23/11/2026 a las 18:00, "
            + "consideraremos aceptado el cambio y quedará efectuado.";
    private static final String PEDIDO_RESPUESTA = "Si el aula propuesta es adecuada, no es necesario que responda. "
            + "En caso contrario, responda este correo e indique el motivo.";
    private static final String MOTIVO_MENOS_AULAS =
            "Solicitó 2 aulas y le proponemos 1: no había otra aula libre en ese horario.";
    private static final String NOTA_ADICIONAL =
            "El cambio rige para todas las clases de ese día hasta el fin del cuatrimestre.";
    private static final String AULAS_NO_DISPONIBLES =
            "El aula 204 (Edificio Central) que solicitó no está disponible por el momento.";

    private static final String RESET_LINK = "https://siga.frc.utn.edu.ar/restablecer-contrasena#token=abc_DEF-123";

    private NotificationRenderer renderer;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver htmlResolver = new ClassLoaderTemplateResolver();
        htmlResolver.setPrefix("notifications/");
        htmlResolver.setSuffix(".html");
        htmlResolver.setTemplateMode(TemplateMode.HTML);
        htmlResolver.setOrder(1);
        htmlResolver.setResolvablePatterns(Set.of("*-body"));

        ClassLoaderTemplateResolver textResolver = new ClassLoaderTemplateResolver();
        textResolver.setPrefix("notifications/");
        textResolver.setSuffix(".txt");
        textResolver.setTemplateMode(TemplateMode.TEXT);
        textResolver.setOrder(2);
        textResolver.setResolvablePatterns(Set.of("*-subject"));

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.addTemplateResolver(htmlResolver);
        engine.addTemplateResolver(textResolver);

        renderer = new NotificationRenderer(engine);
    }

    @Test
    void escapesValueInterpolatedFromModel() {
        Map<String, Object> model = baseModel();
        model.put("observaciones", "<script>alert(1)</script>");

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.body())
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .doesNotContain("<script>alert(1)</script>");
    }

    @Test
    void omitsOptionalFieldsWhenAbsentFromModel() {
        Map<String, Object> model = baseModel();
        model.remove("materia");
        model.remove("comisiones");
        model.remove("comisionesLabel");
        model.remove("horario");

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.body())
                .doesNotContain("Materia")
                .doesNotContain("Comisión")
                .doesNotContain("Horario")
                .doesNotContain("null");
    }

    @Test
    void rendersRowWhenValueIsLiteralStringFalse() {
        Map<String, Object> model = baseModel();
        model.put("materia", "false");

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.body()).contains("Materia").contains("false");
    }

    @Test
    void rendersOneRowPerClassroomAndLabelOnlyOnce() {
        Map<String, Object> model = baseModel();
        model.put("aulasLabel", "Aulas propuestas");
        model.put("aulas", List.of("Aula 108 (Edificio Central)", "Aula 305 (Edificio Norte)"));

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.body())
                .contains("Aula 108 (Edificio Central)")
                .contains("Aula 305 (Edificio Norte)");
        assertThat(occurrences(rendered.body(), "Aulas propuestas")).isEqualTo(1);
    }

    @Test
    void rendersMotivoMenosAulasBoxOnlyWhenPresent() {
        Map<String, Object> model = baseModel();
        model.put("motivoMenosAulas", MOTIVO_MENOS_AULAS);

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.body())
                .contains("background-color:#f0f4ff")
                .contains(MOTIVO_MENOS_AULAS);

        Map<String, Object> modelWithout = baseModel();
        RenderedNotification renderedWithout = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, modelWithout);

        assertThat(renderedWithout.body()).doesNotContain("background-color:#f0f4ff");
    }

    @Test
    void rendersAllKeysWithoutLeftoverPlaceholders() {
        Map<String, Object> model = fullModel();

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.body()).doesNotContain("{{").doesNotContain("}}");
    }

    @Test
    void subjectJoinsTitleAndPartsWithDash() {
        Map<String, Object> model = new HashMap<>();
        model.put("asuntoTitulo", "Propuesta de cambio de aula");
        model.put("asuntoPartes", List.of("Análisis Matemático II", "4K1"));

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.subject()).isEqualTo("Propuesta de cambio de aula — Análisis Matemático II — 4K1");
    }

    @Test
    void subjectIsOnlyTitleWhenPartsAreAbsent() {
        Map<String, Object> model = new HashMap<>();
        model.put("asuntoTitulo", "Propuesta de aula");

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.subject()).isEqualTo("Propuesta de aula");
    }

    @Test
    void subjectEndsWithRequestNumberWhenPresent() {
        Map<String, Object> model = new HashMap<>();
        model.put("asuntoTitulo", "Propuesta de aula");
        model.put("asuntoPartes", List.of("Parcial de Redes"));
        model.put("pedidoId", 158L);

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.subject()).isEqualTo("Propuesta de aula — Parcial de Redes — pedido #158");
    }

    @Test
    void bodyShowsDeadlineAndHowToReplyWhenPresent() {
        Map<String, Object> model = baseModel();
        model.put("plazoRespuesta", PLAZO);
        model.put("pedidoRespuesta", PEDIDO_RESPUESTA);

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.body())
                .contains(PLAZO)
                .contains(PEDIDO_RESPUESTA);
    }

    @Test
    void bodyOmitsDeadlineBoxWhenDeadlineIsAbsent() {
        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, baseModel());

        assertThat(rendered.body())
                .doesNotContain("background-color:#fff7e6")
                .doesNotContain("Plazo de respuesta");
    }

    @Test
    void deadlineGoesBeforeDetailAndHowToReplyGoesAfterTheNotesAndBeforeObservations() {
        String body = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, fullModel()).body();

        int plazo = indexOfPresent(body, PLAZO);
        int materiaLabel = indexOfPresent(body, ">Materia<");
        int aulasLabel = indexOfPresent(body, ">Aula propuesta<");
        int ultimaAula = indexOfPresent(body, "Aula 305 (Edificio Central)");
        int notaAdicional = indexOfPresent(body, NOTA_ADICIONAL);
        int motivoMenosAulas = indexOfPresent(body, MOTIVO_MENOS_AULAS);
        int aulasNoDisponibles = indexOfPresent(body, AULAS_NO_DISPONIBLES);
        int pedidoRespuesta = indexOfPresent(body, PEDIDO_RESPUESTA);
        int observaciones = indexOfPresent(body, "Observaciones:");

        assertThat(plazo).isLessThan(materiaLabel).isLessThan(aulasLabel);
        assertThat(ultimaAula).isLessThan(notaAdicional);
        assertThat(notaAdicional).isLessThan(motivoMenosAulas);
        assertThat(motivoMenosAulas).isLessThan(aulasNoDisponibles);
        assertThat(aulasNoDisponibles).isLessThan(pedidoRespuesta);
        assertThat(pedidoRespuesta).isLessThan(observaciones);
    }

    @Test
    void greetsTheTeacherFormally() {
        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, baseModel());

        assertThat(rendered.body())
                .contains("Estimado/a <span>Ana Gómez</span>:")
                .doesNotContain("Hola,");
    }

    @Test
    void closingTextAsksToReplyWithoutShowingAnAddress() {
        Map<String, Object> model = baseModel();

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.body())
                .contains("Ante cualquier consulta, responda este correo.")
                .doesNotContain("respondé")
                .doesNotContain("Si hay algún problema con tu pedido")
                .doesNotContain("No respondas este mail")
                .doesNotContain("mailto:")
                .doesNotContain("@frc");
    }

    @Test
    void rendersSysacadAlertWithSubjectSummaryAndLines() {
        Map<String, Object> model = new HashMap<>();
        model.put("asunto", "SysAcad: novedades en los envíos de aula");
        model.put("resumen", "2 horarios necesitan atención de Subsecretaría tras la última ejecución del envío a SysAcad.");
        model.put("lineas", List.of("curso 1K1, materia 115, lunes: el aula que figura en SysAcad está ocupada en SIGA",
                "curso 2K2, materia 120, martes: se agotaron los intentos de envío"));
        model.put("restantes", 3);

        RenderedNotification rendered = renderer.render(NotificationTemplate.SYSACAD_ALERT, model);

        assertThat(rendered.subject()).isEqualTo("SysAcad: novedades en los envíos de aula");
        assertThat(rendered.body())
                .contains("2 horarios necesitan atención")
                .contains("curso 1K1, materia 115, lunes")
                .contains("se agotaron los intentos de envío")
                .contains("y 3 más, que puede ver en SIGA");
    }

    @Test
    void sysacadAlertWithoutRemainingOmitsMoreLineAndEscapesLines() {
        Map<String, Object> model = new HashMap<>();
        model.put("asunto", "SysAcad: el envío de aulas no puede comunicarse");
        model.put("resumen", "El proceso de envío de aulas no logra comunicarse con SysAcad desde el 10/03/2026 11:00.");
        model.put("lineas", List.of("<b>x</b>"));
        model.put("restantes", 0);

        RenderedNotification rendered = renderer.render(NotificationTemplate.SYSACAD_ALERT, model);

        assertThat(rendered.subject()).isEqualTo("SysAcad: el envío de aulas no puede comunicarse");
        assertThat(rendered.body()).contains("desde el 10/03/2026 11:00").contains("&lt;b&gt;x&lt;/b&gt;")
                .doesNotContain("que puede ver en SIGA");
    }

    @Test
    void sysacadAlertGreetsFormallyAndDoesNotInviteToReply() {
        String body = renderer.render(NotificationTemplate.SYSACAD_ALERT, sysacadModel()).body();

        assertThat(body)
                .contains("Estimado/a:")
                .contains("El detalle de cada horario está en la sección de revisión de SysAcad de SIGA.")
                .doesNotContainIgnoringCase("respond")
                .doesNotContain("Hola:");
    }

    @Test
    void buildingActivityGreetsFormallyAndInvitesToReply() {
        RenderedNotification rendered = renderer.render(NotificationTemplate.BUILDING_ACTIVITY, buildingActivityModel());

        assertThat(rendered.subject()).isEqualTo("Movimientos de aula en Edificio Central");
        assertThat(rendered.body())
                .contains("Estimado/a:")
                .contains("y 4 más, que puede ver en SIGA")
                .contains("Ante cualquier consulta, responda este correo. "
                        + "Los movimientos completos están en la sección Movimientos de SIGA.")
                .doesNotContain("Hola:")
                .doesNotContain("No respondas");
    }

    @Test
    void takenOverGreetsFormallyAndOnlyInvitesToReply() {
        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_TAKEN_OVER, takenOverModel());

        assertThat(rendered.subject()).isEqualTo("Pedido de aula retomado por Subsecretaría en Edificio Central");
        assertThat(rendered.body())
                .contains("Estimado/a:")
                .contains("Ante cualquier consulta, responda este correo.")
                .doesNotContain("Hola:")
                .doesNotContain("No respondas")
                .doesNotContain("consultá con Subsecretaría");
    }

    @ParameterizedTest
    @EnumSource(NotificationTemplate.class)
    void noTemplateUsesTuteoVoseoOrTheWordMail(NotificationTemplate template) {
        Map<String, Object> model = switch (template) {
            case ROOM_REQUEST_RESOLVED -> fullModel();
            case ROOM_REQUEST_TAKEN_OVER -> takenOverModel();
            case BUILDING_ACTIVITY -> buildingActivityModel();
            case SYSACAD_ALERT -> sysacadModel();
            case PASSWORD_RESET -> passwordResetModel();
            case PASSWORD_CHANGED -> passwordChangedModel();
        };

        RenderedNotification rendered = renderer.render(template, model);
        String visible = InformalRegister.visibleText(rendered.body());

        assertThat(visible).contains("Estimado/a");
        assertThat(InformalRegister.find(rendered.subject())).as("asunto: %s", rendered.subject()).isEmpty();
        assertThat(InformalRegister.find(visible)).as("cuerpo de %s", template).isEmpty();
    }

    @Test
    void passwordResetBodyContainsTheLinkAndTheExpiry() {
        RenderedNotification rendered = renderer.render(NotificationTemplate.PASSWORD_RESET, passwordResetModel());

        assertThat(rendered.subject()).isEqualTo("Restablecimiento de contraseña de SIGA");
        assertThat(rendered.body())
                .contains("href=\"" + RESET_LINK + "\"")
                .contains("Estimado/a <span>Ana Gómez</span>:")
                .contains("vence en <span>30</span> minutos");
    }

    @Test
    void passwordChangedBodyShowsNameAndDateTime() {
        RenderedNotification rendered = renderer.render(NotificationTemplate.PASSWORD_CHANGED, passwordChangedModel());

        assertThat(rendered.subject()).isEqualTo("Su contraseña de SIGA fue modificada");
        assertThat(rendered.body())
                .contains("Estimado/a <span>Ana Gómez</span>:")
                .contains("se modificó el <span>22/11/2026 10:30</span>");
    }

    private Map<String, Object> passwordResetModel() {
        Map<String, Object> model = new HashMap<>();
        model.put("nombre", "Ana Gómez");
        model.put("enlace", RESET_LINK);
        model.put("minutosVencimiento", 30);
        return model;
    }

    private Map<String, Object> passwordChangedModel() {
        Map<String, Object> model = new HashMap<>();
        model.put("nombre", "Ana Gómez");
        model.put("fechaHora", "22/11/2026 10:30");
        return model;
    }

    private Map<String, Object> sysacadModel() {
        Map<String, Object> model = new HashMap<>();
        model.put("asunto", "SysAcad: novedades en los envíos de aula");
        model.put("resumen", "1 horario necesita atención de Subsecretaría tras la última ejecución del envío a SysAcad.");
        model.put("lineas", List.of("curso 1K1, materia 115, lunes: el horario figura sin aula en SysAcad"));
        model.put("restantes", 2);
        return model;
    }

    private Map<String, Object> buildingActivityModel() {
        Map<String, Object> model = new HashMap<>();
        model.put("edificio", "Edificio Central");
        model.put("resumen", "Subsecretaría registró 6 movimientos de aula en Edificio Central.");
        model.put("motivo", "obra");
        model.put("lineas", List.of("Análisis Matemático II, lunes 18:00: Aula 108 a Aula 305",
                "Física I, martes 08:00: Aula 110 a Aula 210"));
        model.put("restantes", 4);
        return model;
    }

    private Map<String, Object> takenOverModel() {
        Map<String, Object> model = new HashMap<>();
        model.put("edificio", "Edificio Central");
        model.put("resumen", "Subsecretaría retomó el pedido #158 de Ana Gómez (Análisis Matemático II), que estaba "
                + "en evaluación en Edificio Central. Ya no aparece en su bandeja.");
        model.put("motivo", "se resuelve desde Subsecretaría");
        return model;
    }

    private static int indexOfPresent(String text, String needle) {
        assertThat(text).contains(needle);
        return text.indexOf(needle);
    }

    private static int occurrences(String text, String needle) {
        Matcher matcher = Pattern.compile(Pattern.quote(needle)).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private Map<String, Object> baseModel() {
        Map<String, Object> model = new HashMap<>();
        model.put("tituloBanner", "Propuesta de cambio de aula");
        model.put("docente", "Ana Gómez");
        model.put("parrafoConfirmacion",
                "Para el cambio de aula de su pedido #158 le proponemos la siguiente aula. "
                        + "Le solicitamos que confirme si resulta adecuada.");
        model.put("materia", "Análisis Matemático II");
        model.put("comisionesLabel", "Comisión");
        model.put("comisiones", "4K1");
        model.put("horario", "18:00 a 22:00");
        model.put("aulasLabel", "Aula propuesta");
        model.put("aulas", List.of("Aula 305 (Edificio Central)"));
        return model;
    }

    private Map<String, Object> fullModel() {
        Map<String, Object> model = baseModel();
        model.put("diaSemana", "Lunes");
        model.put("fecha", "03/12/2026");
        model.put("aulaAnterior", "Aula 108 (Edificio Central)");
        model.put("notaAdicional", NOTA_ADICIONAL);
        model.put("motivoMenosAulas", MOTIVO_MENOS_AULAS);
        model.put("aulasPedidasNoDisponibles", AULAS_NO_DISPONIBLES);
        model.put("observaciones", "Sin observaciones adicionales.");
        model.put("plazoRespuesta", PLAZO);
        model.put("pedidoRespuesta", PEDIDO_RESPUESTA);
        return model;
    }
}
