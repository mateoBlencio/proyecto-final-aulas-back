# Herramientas: comandos que funcionaron y sus trampas

Probado el 2026-10-13 con Docker Desktop en el host. El sandbox de Claude Code no arranca
Docker: pedirle al usuario que lo levante.

```bash
export DOCKER_HOST=unix://$HOME/.docker/desktop/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

Sin esas variables los `*IntegrationTest` quedan skipped y eso no cuenta como verificación.

Docker Desktop no comparte `/tmp` con su VM: un bind mount desde el scratchpad monta un
directorio vacío y la herramienta escribe en la nada. Montar carpetas bajo `$HOME`
(por ejemplo `~/.cache/siga-audit`).

## Suite y cobertura

```bash
./mvnw -B verify jacoco:report        # ~3m30s, 2263 tests en 2026-10-13
```

Cobertura por módulo desde `target/site/jacoco/jacoco.csv`: agrupar por el sexto segmento del
paquete (`ar.edu.utn.frc.siga.<módulo>`) y sumar `LINE_*` y `BRANCH_*`. Copiar el CSV fuera de
`target/` antes de correr otra herramienta Maven.

## PIT (mutation testing)

Fuente: https://pitest.org/quickstart/maven/

Necesita `pitest-junit5-plugin` como dependencia del plugin, así que va con un pom temporal sin
commitear (copiar `pom.xml` a `pom-audit.xml` e insertar el plugin; borrarlo al terminar):

```xml
<plugin>
  <groupId>org.pitest</groupId><artifactId>pitest-maven</artifactId><version>1.30.0</version>
  <dependencies><dependency>
    <groupId>org.pitest</groupId><artifactId>pitest-junit5-plugin</artifactId><version>1.2.3</version>
  </dependency></dependencies>
</plugin>
```

Trampas:
- El `argLine` de surefire es `@{argLine} -Duser.timezone=UTC`. Sin JaCoCo, PIT recibe
  `{argLine}` literal y falla con `NoSuchFileException: {argLine}`. `-DparseSurefireArgLine=false`
  no alcanzó: reemplazarlo en el pom temporal por `-Duser.timezone=UTC`.
- Excluir los tests de integración (`-DexcludedTestClasses=*IntegrationTest,...`) para que
  corra en minutos. Entonces "sin cobertura" significa "solo lo prueban tests de integración".

```bash
B=ar.edu.utn.frc.siga
./mvnw -B -f pom-audit.xml org.pitest:pitest-maven:1.30.0:mutationCoverage \
  -DtargetClasses="$B.allocation.validator.*,$B.roomrequest.validator.*,$B.auth.security.*" \
  -DtargetTests="$B.*" -DexcludedTestClasses="*IntegrationTest,$B.ModularityTests,$B.SecurityAnnotationsArchTest" \
  -Dthreads=8 -DoutputFormats=XML,HTML -DtimestampedReports=false
```

Leer `target/pit-reports/mutations.xml` y mirar los `SURVIVED` en validadores y seguridad: ahí
están los tests que no verifican el caso positivo o los bordes.

## SpotBugs + FindSecBugs

Fuentes: https://spotbugs.readthedocs.io/en/latest/maven.html, https://find-sec-bugs.github.io/

```bash
./mvnw -B -q dependency:get -Dartifact=com.h3xstream.findsecbugs:findsecbugs-plugin:1.12.0
J=~/.m2/repository/com/h3xstream/findsecbugs/findsecbugs-plugin/1.12.0/findsecbugs-plugin-1.12.0.jar
./mvnw -B com.github.spotbugs:spotbugs-maven-plugin:4.9.3.0:spotbugs \
  -Dspotbugs.pluginList=$J -Dspotbugs.effort=Max -Dspotbugs.threshold=Low -Dspotbugs.xmlOutput=true
```

Resultado en `target/spotbugsXml.xml`. Triage ya hecho en este repo (no re-reportar):

| Tipo | Veredicto |
|---|---|
| `EI_EXPOSE_REP`, `EI_EXPOSE_REP2`, `SPRING_ENDPOINT` | Ruido (Lombok, entidades, marca de endpoint) |
| `SQL_INJECTION_SPRING_JDBC` en `AuditArchiveRepository` | Falso positivo: SQL armado con el enum `ArchivedTable` |
| `UNSAFE_HASH_EQUALS` en servicios de sync | Falso positivo: hashes de contenido, no secretos |
| `SPRING_CSRF_PROTECTION_DISABLED` | Correcto mientras la API sea stateless sin cookies |
| `IS2_INCONSISTENT_SYNC` en `common.util.Lazy` | Falso positivo: double-checked locking con `volatile` |
| `UPM_UNCALLED_PRIVATE_METHOD` en DTOs | Probable falso positivo: `@AssertTrue` privados que invoca Hibernate Validator |
| `CRLF_INJECTION_LOGS` | Real pero Baja: neutralizar `\r\n` en el patrón de logback |

## OSV-Scanner (dependencias con CVE)

Fuente: https://google.github.io/osv-scanner/

Sobre `pom.xml` solo resuelve las dependencias con versión explícita. Para las transitivas,
generar un SBOM CycloneDX:

```bash
./mvnw -B -q org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeBom -DincludeTestScope=false -DoutputFormat=json -DoutputName=bom
O=~/.cache/siga-audit; mkdir -p $O; cp target/bom.json $O/bom.cdx.json
docker run --rm -v "$O:/out" ghcr.io/google/osv-scanner:v2.2.2 scan source -L /out/bom.cdx.json --format json --output /out/osv.json
```

Para decidir el arreglo, sacar de `osv.json` las versiones `fixed` por paquete y compararlas con
lo que gestiona el BOM de la siguiente versión de Spring Boot
(`spring-boot-dependencies-<v>.pom` en Maven Central). Casi siempre alcanza con subir el parent.

## gitleaks (secretos en el historial)

Fuente: https://github.com/gitleaks/gitleaks

```bash
O=~/.cache/siga-audit
docker run --rm -v "$PWD:/repo:ro" -v "$O:/out" ghcr.io/gitleaks/gitleaks:v8.24.3 \
  git /repo --report-format json --report-path /out/gitleaks.json --no-banner --redact
```

Con `dir` en vez de `git` escanea el working tree; filtrar lo que está en `.gitignore`. Para
un JWT encontrado, decodificar solo el payload y ocultar `sub` para saber si está vencido, sin
imprimir el token.
