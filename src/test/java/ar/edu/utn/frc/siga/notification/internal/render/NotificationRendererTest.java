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
    void omitsLineWhenOptionalFieldIsAbsentFromModel() {
        Map<String, Object> model = baseModel();
        model.remove("horario");

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.body()).doesNotContain("Horario").doesNotContain("null");
    }

    @Test
    void rendersOneRowPerClassroomInModelList() {
        Map<String, Object> model = baseModel();
        model.put("aulas", List.of("Aula 108 (Edificio Central)", "Aula 305 (Edificio Norte)"));

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.body())
                .contains("Aula 108 (Edificio Central)")
                .contains("Aula 305 (Edificio Norte)");
    }

    @Test
    void subjectInterpolatesTypeAndOmitsMissingSubject() {
        Map<String, Object> model = baseModel();
        model.remove("materia");
        model.remove("comision");

        RenderedNotification rendered = renderer.render(NotificationTemplate.ROOM_REQUEST_RESOLVED, model);

        assertThat(rendered.subject())
                .isEqualTo("Aula confirmada — Cambio de aula — regular")
                .doesNotContain("null");
    }

    private Map<String, Object> baseModel() {
        Map<String, Object> model = new HashMap<>();
        model.put("docente", "Ana Gómez");
        model.put("pedidoId", "#158");
        model.put("tipoTexto", "Cambio de aula — regular");
        model.put("materia", "Análisis Matemático II");
        model.put("comision", "4K1");
        model.put("horario", "18:00 a 22:00");
        model.put("aulas", List.of("Aula 305 (Edificio Central)"));
        return model;
    }
}
