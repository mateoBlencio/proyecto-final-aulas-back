@ApplicationModule(allowedDependencies = {
        "academic :: service", "academic :: model",
        "space :: service", "space :: model",
        "allocation :: service", "allocation :: dto"
})
package ar.edu.utn.frc.siga.excelimport;

import org.springframework.modulith.ApplicationModule;
