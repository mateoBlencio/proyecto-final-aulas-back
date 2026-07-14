/**
 * Módulo transversal (OPEN) con configuración, manejo global de excepciones, converters JPA
 * y utilidades de auditoría compartidas por el resto de los módulos. Al ser {@code OPEN} no
 * declara fronteras Modulith: cualquier módulo puede depender de él sin necesidad de una
 * interfaz pública {@code api}.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package ar.edu.utn.frc.siga.common;

import org.springframework.modulith.ApplicationModule;
