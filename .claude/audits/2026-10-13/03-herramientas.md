# Resultados de herramientas (hilo principal)

## Suite de tests
`./mvnw -B verify jacoco:report` con Docker: 2263 tests, 0 fallas, 0 errores, 0 skipped, 3m30s.

## Cobertura JaCoCo (líneas% / ramas%)
academic 91.2/78.5, allocation 95.4/82.6, audit 97.2/92.8, auth 94.8/85.8, common 95.9/91.7,
dashboard 98.6/95.5, events 93.4/80.7, ingest 95.2/81.6, notification 95.4/78.8, optimizer 94.0/75.6,
preview 99.3/91.9, roomrequest 92.9/82.9, settings 93.7/76.3, space 93.1/82.7.
Total sin sysacad: 94.7% líneas, 85.4% ramas (9035 líneas).

## SpotBugs 4.9.3.0 + FindSecBugs 1.12.0 (effort Max, threshold Low), sin sysacad: 878 avisos

Triage:
- EI_EXPOSE_REP/EI_EXPOSE_REP2 (395) y SPRING_ENDPOINT (124): ruido (entidades/DTOs Lombok; SPRING_ENDPOINT solo marca endpoints). Descartados.
- SQL_INJECTION_SPRING_JDBC (7) en audit.repository.AuditArchiveRepository:150-163: FALSO POSITIVO. El SQL se arma con nombres de tabla/columna del enum ArchivedTable (constantes), no con input externo.
- UNSAFE_HASH_EQUALS (8) en CommissionServiceImpl:277, SpecialtyServiceImpl:88, SubjectServiceImpl:150, AcademicEventServiceImpl:401-411, BuildingServiceImpl:177, ClassroomServiceImpl:370: FALSO POSITIVO. Comparan hashes de contenido para detectar cambios de sync, no secretos; timing attack no aplica.
- SPRING_CSRF_PROTECTION_DISABLED auth.config.SecurityConfig:62: correcto si la API es stateless con JWT en header Authorization y no usa cookies de sesión/refresh. A verificar por seguridad (si el refresh viaja en cookie, CSRF vuelve a aplicar).
- IS2_INCONSISTENT_SYNC common.util.Lazy:29: FALSO POSITIVO, double-checked locking correcto (flag volatile escrito después de value).
- CRLF_INJECTION_LOGS (281): logs con datos de entrada sin neutralizar CR/LF (ej. AuthServiceImpl loguea email e IP). ASVS v5.0.0 V16 (log injection). Arreglo S: patrón de logback que reemplace \r\n en %msg, o encoder JSON. Además: email + IP en logs de login es dato personal (V14/V16).
- UPM_UNCALLED_PRIVATE_METHOD (18): son métodos privados @AssertTrue en DTOs (isRangeValid, isTargetValid, isComputerCountValid...). Hibernate Validator los invoca por reflexión: probable falso positivo. Confirmar con un test que el 400 se dispare.
- NP_BOOLEAN_RETURN_NULL roomrequest.dto.request.CreateRoomRequestItemDto:38,42,50 (requiresProjector/Computers/ExamUsers devuelven Boolean null): riesgo de NPE si alguien hace unboxing. Baja.
- DLS_DEAD_LOCAL_STORE auth.service.impl.AuthServiceImpl:63 y audit.repository.AuditLogQueryRepository:151: variables asignadas y no usadas. Baja, S.
- NP_NULL_ON_SOME_PATH en *MapperImpl (generado por MapStruct) y DM_CONVERT_CASE (toLowerCase sin Locale en Specifications y AuthDomainProperties.isAllowedEmail:15): Baja. toLowerCase sin Locale.ROOT falla con locale turco; S.

## gitleaks 8.24.3 (historial completo, 528 commits)
20 coincidencias: JWT de acceso (rol SUBSECRETARIA, vencidos, exp 20 min, julio-agosto 2026) en bruno/*.bru y bruno/collection.bru en 13 commits; refreshToken en bruno/auth/Refresh.bru y Logout.bru (commits 862b8f91, 89e42a9f); clave JWT de test en JwtServiceTest.java. Working tree: bruno/.env y .idea/workspace.xml tienen secretos pero están en .gitignore.

## OSV-Scanner 2.2.2
Sobre pom.xml solo resolvió 8 dependencias con versión explícita. Con SBOM CycloneDX (cyclonedx-maven-plugin 2.9.1, scope runtime, 167 componentes):
17 paquetes con avisos. Los graves: tomcat-embed-core 11.0.21 (varios CVSS 9.1-9.8), spring-webmvc 7.0.7 (dos 9.8), jackson-databind/core 2.21.2 y 3.1.2 (hasta 8.7), postgresql 42.7.10 (8.2), spring-data-commons 4.0.5 (7.5), micrometer-core 1.16.5 (7.5), spring-expression 7.0.7 (7.5), spring-security-web 7.0.5 (6.1).
Spring Boot 4.0.8 (último 4.0.x en Maven Central) gestiona: Spring Framework 7.0.9, Security 7.0.7, Data 2025.1.7, Tomcat 11.0.24, Jackson 2.21.5/3.1.5, postgresql 42.7.13, micrometer 1.16.7, logback 1.5.38, log4j 2.25.5. Eso cierra todo salvo algunos avisos que piden Tomcat 11.0.25 y Jackson 2.21.7/3.1.7: después del bump, re-escanear y fijar `tomcat.version`/`jackson-bom.version` si siguen abiertos.

## PIT 1.30.0 + pitest-junit5-plugin 1.2.3 (pom temporal, sin tests *IntegrationTest)
Clases objetivo: allocation.service/validator, roomrequest.service/validator/handler, auth.security/service. 4m12s.
884 mutantes, 636 muertos (72%), 181 sin cobertura unitaria, fuerza de tests 90% sobre lo cubierto. Cobertura de línea por tests unitarios en esas clases: 79%.
Ojo: PIT excluyó los tests de integración, así que "sin cobertura" en clases como DirectEventServiceImpl (52), AllocationImpactServiceImpl (41) y JwtAuthenticationFilter (9) significa "solo las prueban tests de integración".
Mutantes sobrevivientes relevantes (detalle en pit-summary.txt):
- auth.security.DefaultBuildingScopeResolver.requireAccess:42, negar `!scope.allows(...)` sobrevive: DefaultBuildingScopeResolverTest solo verifica que lance en el caso denegado; ningún test unitario verifica que NO lance para el edificio permitido. Arreglo S: `assertThatCode(() -> resolver.requireAccess(..., 5L)).doesNotThrowAnyException()`.
- auth.security.SecurityUser.getAuthorities:78 devolviendo lista vacía sobrevive en unitarios.
- allocation.validator.AllocationValidator: validateStrictOverlap:60 (filtro de pasado reemplazado por true), review:71 (borde de comparación), loadOccupancy:78/86 sobreviven. Las reglas de choque toleran cambios de borde sin que falle un test unitario.
- roomrequest.validator.ItemConsistency.requireNoCommissionOverlap:41 (borde), ClassScheduleService.requireClassDate:67.
- auth.security.LoginRateLimitRecord: quitar prune() en recordFailure:28 y retryAfter:18 sobrevive.
- roomrequest.service.impl.RoomRequestResolutionServiceImpl: 16 sobrevivientes, la clase con más.

## Hallazgos

### DEP-01 Dependencias con CVE conocidos por Spring Boot 4.0.6
- Severidad: Alta
- Esfuerzo: S
- Referencia: OWASP Top 10:2025 A03 Software Supply Chain Failures; ASVS v5.0.0 V15 (componentes con vulnerabilidades conocidas)
- Evidencia: `pom.xml` (`spring-boot-starter-parent` 4.0.6); OSV-Scanner 2.2.2 sobre SBOM CycloneDX
- Verificación: Confirmado (versiones con fix tomadas de OSV y del BOM `spring-boot-dependencies` 4.0.8 en Maven Central)
- Problema: Tomcat 11.0.21 y spring-webmvc 7.0.7 tienen avisos con CVSS 9.1-9.8; Jackson, postgresql, spring-data-commons y micrometer, de 7.5 a 8.7. CI no corta el build por CVE (OPS-05).
- Arreglo: Subir el parent a 4.0.8, correr la suite, re-escanear y fijar `tomcat.version`/`jackson-bom.version` si quedan avisos abiertos.
