package ar.edu.utn.frc.siga.notification.internal.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.util.Set;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfiguration {

    private static final String TEMPLATES_PREFIX = "notifications/";

    @Bean
    public ITemplateResolver notificationHtmlResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(TEMPLATES_PREFIX);
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setOrder(1);
        resolver.setResolvablePatterns(Set.of("*-body"));
        resolver.setCheckExistence(true);
        return resolver;
    }

    @Bean
    public ITemplateResolver notificationTextResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(TEMPLATES_PREFIX);
        resolver.setSuffix(".txt");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setOrder(2);
        resolver.setResolvablePatterns(Set.of("*-subject"));
        resolver.setCheckExistence(true);
        return resolver;
    }

    @Bean
    public SpringTemplateEngine notificationTemplateEngine(
            @Qualifier("notificationHtmlResolver") ITemplateResolver notificationHtmlResolver,
            @Qualifier("notificationTextResolver") ITemplateResolver notificationTextResolver) {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.addTemplateResolver(notificationHtmlResolver);
        engine.addTemplateResolver(notificationTextResolver);
        return engine;
    }
}
