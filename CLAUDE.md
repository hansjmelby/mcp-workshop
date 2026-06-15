# CLAUDE.md

Veiledning for Claude Code (og andre LLM-agenter) som jobber i dette repoet.

## Hva dette er

Et **workshop-skall** for å lære å bygge en **MCP-server** (Model Context Protocol).
Domenet er **ferie-booking**: en LLM skal kunne søke etter reisemål, sjekke
tilgjengelighet/priser og opprette bookinger mot en SQLite-database.

Skallet kjører allerede og eksponerer ett eksempel-verktøy (`about_application`).
Selve læringen skjer ved å jobbe gjennom **[BACKLOG.md](BACKLOG.md)** — en liste med
oppgaver som gradvis bygger ut serveren. Fasit ligger på `solution`-branchen.

## Tech stack

| Komponent        | Valg                                                                 |
|------------------|----------------------------------------------------------------------|
| Språk            | Java 21                                                              |
| Rammeverk        | Spring Boot 4.1.0                                                    |
| MCP              | Spring AI 2.0.0 — `org.springframework.ai:spring-ai-starter-mcp-server` |
| Byggeverktøy     | Gradle (Kotlin DSL), wrapper 9.5.1 (`./gradlew`)                     |
| Database         | SQLite via `org.xerial:sqlite-jdbc` + Spring `JdbcTemplate`         |
| Transport        | stdio (skallet) → Streamable HTTP i Epic 7                          |

Versjonene henger sammen via Spring AI BOM (`spring-ai-bom:2.0.0`) og Spring Boot sin
dependency management — **ikke** pinn enkeltversjoner manuelt uten grunn.

## Vanlige kommandoer

```bash
./gradlew build        # kompiler + kjør tester
./gradlew test         # kun tester (kontekst + DB lastes; MCP-server av i test)
./gradlew bootJar      # bygg kjørbar jar -> build/libs/
./gradlew bootRun      # kjør appen (stdio-server; leser JSON-RPC fra stdin)
```

Kjør den ferdige serveren som en stdio-MCP-server:

```bash
java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar
```

Røyktest av protokollen uten en host (initialize + tools/list + tools/call):

```bash
printf '%s\n' \
'{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}' \
'{"jsonrpc":"2.0","method":"notifications/initialized"}' \
'{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' \
| java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar
```

## Slik lager du et MCP-verktøy

Annoter en metode på en Spring-bean med `@McpTool`. Spring AI sin annotasjons-scanner
oppdager den automatisk — ingen manuell registrering nødvendig.

```java
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ExampleTool {

    @McpTool(name = "verktoy_navn", description = "Hva verktøyet gjør.")
    public String run(@McpToolParam(description = "...", required = true) String arg) {
        return "...";
    }
}
```

Mønsteret finnes i [`AboutTool.java`](src/main/java/no/computas/vacationmcp/tools/AboutTool.java).
Tilsvarende annotasjoner finnes for ressurser (`@McpResource`) og prompts (`@McpPrompt`).

## Viktige konvensjoner / fallgruver

- **stdio eier stdout.** I stdio-modus går JSON-RPC over stdout, så **all logging må til
  fil** (`logging.threshold.console=OFF`, `logging.file.name=…`) og banner er av. Ikke skriv
  til `System.out` fra verktøykode — det korrumperer protokollen.
- **Databasen** opprettes og seedes ved oppstart fra `src/main/resources/schema.sql` +
  `data.sql` (`spring.sql.init.mode=always`). Fila `vacation.db` ligger i prosjektroten og
  er git-ignorert; slett den for å nullstille. Skjemaet er idempotent (`IF NOT EXISTS`).
- **Tester** kjører med MCP-serveren avskrudd (`src/test/resources/application.properties`)
  så `@SpringBootTest` ikke blokkerer på stdin.
- **Gradle-wrapperen** er committet (`gradle/wrapper/gradle-wrapper.jar`) — `./gradlew`
  fungerer uten lokal Gradle-installasjon.

## Struktur

```
build.gradle.kts                     # avhengigheter (Spring Boot, Spring AI MCP, sqlite-jdbc)
src/main/java/no/computas/vacationmcp/
  VacationBookingMcpApplication.java # @SpringBootApplication
  tools/AboutTool.java               # eksempel-@McpTool
src/main/resources/
  application.properties             # MCP stdio + SQLite-config
  schema.sql / data.sql              # ferie-booking-skjema + seed
BACKLOG.md                           # workshop-oppgavene
```
