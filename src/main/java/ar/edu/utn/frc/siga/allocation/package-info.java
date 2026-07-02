@ApplicationModule(allowedDependencies = {
        "space :: service", "space :: dto", "space :: model", "space :: mapper",
        "academic :: service", "academic :: model"
})
package ar.edu.utn.frc.siga.allocation;

import org.springframework.modulith.ApplicationModule;
