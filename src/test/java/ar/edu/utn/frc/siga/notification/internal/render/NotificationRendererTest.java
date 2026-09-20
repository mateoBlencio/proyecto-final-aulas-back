package ar.edu.utn.frc.siga.notification.internal.render;

import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        model.put("aulasLabel", "Aulas asignadas");
        model.put("aulas", List.of("Aula 108 (Edificio Central)", "Aula 305 (Edificio Norte)"));

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.body())
                .contains("Aula 108 (Edificio Central)")
                .contains("Aula 305 (Edificio Norte)");
        assertThat(occurrences(rendered.body(), "Aulas asignadas")).isEqualTo(1);
    }

    @Test
    void rendersMotivoMenosAulasBoxOnlyWhenPresent() {
        Map<String, Object> model = baseModel();
        model.put("motivoMenosAulas", "Se asignó 1 de las 2 aulas pedidas por falta de disponibilidad.");

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.body())
                .contains("background-color:#f0f4ff")
                .contains("Se asignó 1 de las 2 aulas pedidas por falta de disponibilidad.");

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
        model.put("asuntoTitulo", "Cambio de aula regular");
        model.put("asuntoPartes", List.of("Análisis Matemático II", "4K1"));

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.subject()).isEqualTo("Cambio de aula regular — Análisis Matemático II — 4K1");
    }

    @Test
    void subjectIsOnlyTitleWhenPartsAreAbsent() {
        Map<String, Object> model = new HashMap<>();
        model.put("asuntoTitulo", "Aula confirmada");

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.subject()).isEqualTo("Aula confirmada");
    }

    @Test
    void closingTextHasNoContactAddress() {
        Map<String, Object> model = baseModel();

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.body())
                .contains("No respondas este mail")
                .doesNotContain("mailto:")
                .doesNotContain("@frc")
                .doesNotContain("respondé este mail");
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
        model.put("tituloBanner", "Cambio de aula confirmado");
        model.put("docente", "Ana Gómez");
        model.put("parrafoConfirmacion", "Confirmamos el cambio de aula de tu pedido #158.");
        model.put("materia", "Análisis Matemático II");
        model.put("comisionesLabel", "Comisión");
        model.put("comisiones", "4K1");
        model.put("horario", "18:00 a 22:00");
        model.put("aulasLabel", "Aula");
        model.put("aulas", List.of("Aula 305 (Edificio Central)"));
        return model;
    }

    private Map<String, Object> fullModel() {
        Map<String, Object> model = baseModel();
        model.put("diaSemana", "Lunes");
        model.put("fecha", "03/12/2026");
        model.put("aulaAnterior", "Aula 108 (Edificio Central)");
        model.put("notaAdicional", "Rige para todas las clases de ese día.");
        model.put("motivoMenosAulas", "Se asignó 1 de las 2 aulas pedidas por falta de disponibilidad.");
        model.put("observaciones", "Sin observaciones adicionales.");
        return model;
    }
}
