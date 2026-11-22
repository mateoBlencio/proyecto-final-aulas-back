@ApplicationModule(allowedDependencies = {
        "common", "academic :: api", "space :: api", "events :: api", "allocation :: api", "audit :: api",
        "roomrequest :: api", "notification :: api", "auth :: api", "settings :: api" })
package ar.edu.utn.frc.siga.sysacad;

import org.springframework.modulith.ApplicationModule;
