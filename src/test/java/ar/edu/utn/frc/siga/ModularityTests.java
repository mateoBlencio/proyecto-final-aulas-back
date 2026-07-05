package ar.edu.utn.frc.siga;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    static final ApplicationModules modules = ApplicationModules.of(SigaApplication.class);

    @Test
    void printStructure() {
        modules.forEach(System.out::println);
    }

    @Test
    void verifyBoundaries() {
        modules.verify();
    }

    @Test
    void generateDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
