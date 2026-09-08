package ar.edu.utn.frc.siga;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@DisplayName("Arquitectura de seguridad: todo endpoint declara @PreAuthorize salvo los permitAll")
class SecurityAnnotationsArchTest {

    // Endpoints deliberadamente sin @PreAuthorize: los abre SecurityConfig con permitAll.
    private static final Set<String> PERMITTED_WITHOUT_PREAUTHORIZE = Set.of(
            "ar.edu.utn.frc.siga.auth.controller.AuthController",
            "ar.edu.utn.frc.siga.roomrequest.controller.RoomRequestCatalogController",
            "ar.edu.utn.frc.siga.roomrequest.controller.RoomRequestController.create");

    @Test
    void everyEndpointMethodIsGuarded() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages("ar.edu.utn.frc.siga");

        List<String> unguarded = classes.stream()
                .filter(c -> c.isAnnotatedWith(RestController.class))
                .flatMap(c -> c.getMethods().stream())
                .filter(SecurityAnnotationsArchTest::isMappingMethod)
                .filter(m -> !m.getOwner().isAnnotatedWith(PreAuthorize.class))
                .filter(m -> !m.isAnnotatedWith(PreAuthorize.class))
                .filter(m -> !isPermitAll(m))
                .map(m -> m.getOwner().getName() + "." + m.getName())
                .sorted()
                .toList();

        assertThat(unguarded)
                .as("endpoints sin @PreAuthorize (ni en el método ni en la clase) y fuera del allow-list permitAll")
                .isEmpty();
    }

    private static boolean isMappingMethod(JavaMethod method) {
        return method.isAnnotatedWith(RequestMapping.class)
                || method.isAnnotatedWith(GetMapping.class)
                || method.isAnnotatedWith(PostMapping.class)
                || method.isAnnotatedWith(PutMapping.class)
                || method.isAnnotatedWith(DeleteMapping.class)
                || method.isAnnotatedWith(PatchMapping.class);
    }

    private static boolean isPermitAll(JavaMethod method) {
        String owner = method.getOwner().getName();
        return PERMITTED_WITHOUT_PREAUTHORIZE.contains(owner)
                || PERMITTED_WITHOUT_PREAUTHORIZE.contains(owner + "." + method.getName());
    }
}
