# CLAUDE.md

Veiledning for Claude Code (og andre LLM-agenter) som jobber i dette repoet.

> **Denne filen er selvstendig.** All informasjon en agent trenger for å jobbe i repoet
> ligger her — ikke anta at `README.md` er lest. (`README.md` er for de menneskelige
> workshop-deltakerne og kan dupliseres herfra, men er ikke en kilde denne filen avhenger av.)

## Hva dette er

Et **workshop-skall** for å lære å bygge en **MCP-server** (Model Context Protocol).
Domenet er **ferie-booking**: en LLM skal kunne søke etter reisemål, sjekke
tilgjengelighet/priser og opprette bookinger mot en SQLite-database.

Skallet kjører allerede og eksponerer ett eksempel-verktøy (`about_application`).
Selve læringen skjer ved å jobbe gjennom **[BACKLOG.md](BACKLOG.md)** — en liste med
oppgaver som gradvis bygger ut serveren.

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

Røyktest av protokollen uten en host (initialize + capability-forhandling + tools/list).
Meldingene må komme i denne rekkefølgen; `notifications/initialized` er en notifikasjon og
har ingen respons:

```bash
{ \
  printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}'; \
  sleep 2; \
  printf '%s\n' '{"jsonrpc":"2.0","method":"notifications/initialized"}'; \
  sleep 1; \
  printf '%s\n' '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'; \
  sleep 2; \
} | java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar
```

Se README-seksjonen «Før annotasjonene: MCP under panseret» for en faktisk, kommentert trace
og forklaring av `inputSchema` (JSON Schema). Dette er læringssteg T-00 i BACKLOG.md og skal
komme før arbeid med `@McpTool`.

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

## Forretningstjenester (ferdig — deleger hit)

Forretnings- og datalaget er implementert og testet. Oppgaver skal *eksponere* disse som
MCP, ikke skrive ny forretnings-/DB-kode. Injiser tjenesten i en `@Component`. Feil
signaliseres med `ValidationException` (ugyldig input/regelbrudd) og `NotFoundException`.

Domene (`domain/`): records `Destination`, `Availability`, `Booking` + enum `BookingStatus`
(med `canTransitionTo(...)`; flyt `PENDING → CONFIRMED → PAID → COMPLETED`, alle → `CANCELLED`).

`DestinationService`
- `List<Destination> listAvailable()`
- `List<Destination> search(String query, String country, Double maxPricePerNight)` — alle params valgfrie; avviser negativ pris

`PricingService`
- `Quote quote(long destinationId, LocalDate from, LocalDate to, int numTravelers)` — validerer + beregner (sesongpris ?: pris/natt × netter × reisende)
- `Availability findCoveringPeriod(long destinationId, LocalDate from, LocalDate to)`

`BookingService`
- `Booking createBooking(String customerName, long destinationId, LocalDate from, LocalDate to, int numTravelers)` — validering + kapasitet + pris; lagrer som `PENDING`
- `Booking get(long id)` · `List<Booking> list(BookingStatus status)` (null = alle)
- `Booking updateStatus(long id, BookingStatus target)` — håndhever tilstandsmaskinen
- `Booking cancel(long id)`

Lavnivå ved behov: `DestinationRepository`, `AvailabilityRepository`
(`findOverlapping`/`findCovering`/`findByDestinationId`), `BookingRepository`.

## Viktige konvensjoner / fallgruver

- **stdio eier stdout.** I stdio-modus går JSON-RPC over stdout, så **all logging må til
  fil** (`logging.threshold.console=OFF`, `logging.file.name=…`) og banner er av. Ikke skriv
  til `System.out` fra verktøykode — det korrumperer protokollen.
- **Databasen** opprettes og seedes ved oppstart fra `src/main/resources/schema.sql` +
  `data.sql` (`spring.sql.init.mode=always`). Fila `vacation.db` ligger i prosjektroten og
  er git-ignorert; slett den for å nullstille. Skjemaet er idempotent (`IF NOT EXISTS`).
- **Tester** kjører med MCP-serveren avskrudd (`src/test/resources/application.properties`)
  så `@SpringBootTest` ikke blokkerer på stdin.
- **Oppstartslogg av verktøy:** `workshop.log-registered-tools=true` (default) skriver
  navnene på alle `@McpTool` til konsollet ved oppstart (se `RegisteredToolsLogger`). Sett
  `false` for å skru av. Nyttig for å bekrefte at et nytt verktøy ble plukket opp.
- **Gradle-wrapperen** er committet (`gradle/wrapper/gradle-wrapper.jar`) — `./gradlew`
  fungerer uten lokal Gradle-installasjon.

## Struktur

```
build.gradle.kts                     # avhengigheter (Spring Boot, Spring AI MCP, sqlite-jdbc)
src/main/java/no/computas/vacationmcp/
  VacationBookingMcpApplication.java # @SpringBootApplication
  tools/AboutTool.java               # eksempel-@McpTool
  domain/                            # records: Destination, Availability, Booking, BookingStatus
  repository/                        # JdbcTemplate-dataaksess (gitt — ikke skriv om)
  service/                           # forretningslogikk: Destination/Pricing/BookingService (gitt)
  config/RegisteredToolsLogger.java  # logger registrerte tools ved oppstart
src/main/resources/
  application.properties             # MCP stdio + SQLite-config
  schema.sql / data.sql              # ferie-booking-skjema + seed
src/test/java/.../service/           # tester for forretningslaget
BACKLOG.md                           # workshop-oppgavene
```
