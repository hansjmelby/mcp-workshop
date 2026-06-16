# MCP Workshop — Ferie-booking-server

Lær å bygge en **MCP-server** (Model Context Protocol) ved å bygge ut en ekte liten
tjeneste: en **ferie-booking-server** som lar en LLM (f.eks. Claude) søke etter reisemål,
sjekke tilgjengelighet og priser, og opprette bookinger.

Du starter med et **kjørbart skall** og jobber deg gjennom en **[backlog](BACKLOG.md)** av
oppgaver som steg for steg legger til verktøy (tools), ressurser (resources) og prompts.

> **Fokuset er MCP, ikke forretningskode.** Hele forretnings- og datalaget (validering,
> prisberegning, booking-logikk, databaseaksess) er **ferdig implementert og testet**.
> Din jobb er å *eksponere* disse tjenestene som MCP-funksjonalitet. Se
> [Tjenestene du bygger på](#tjenestene-du-bygger-på).

---

## Forutsetninger

- **JDK 21** (`java -version` skal vise 21).
- Ingen lokal Gradle nødvendig — `./gradlew` laster den ned selv.
- **Node/npx** for MCP Inspector (`npx @modelcontextprotocol/inspector`).
- En host for å bruke serveren «på ekte»: **Claude Desktop** eller **Claude Code**.

## Kom i gang

```bash
# 1. Bygg og kjør testene
./gradlew build

# 2. Bygg en kjørbar jar
./gradlew bootJar

# 3. Kjør serveren som en stdio-MCP-server
java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar
```

Serveren snakker JSON-RPC over **stdin/stdout** og venter på en MCP-klient. Når du starter
den fra IntelliJ/konsoll ser du Spring-logoen og oppstartslogg (rutet til stderr), inkludert
en linje som lister registrerte verktøy — deretter «står» den og venter på en klient. Det er
riktig oppførsel for en stdio-server.

Skallet starter med ett eksempel-verktøy, `about_application`, som svarer på hva
applikasjonen er.

### Verifiser med MCP Inspector

Kjør fra prosjektroten (så den relative jar-stien stemmer):

```bash
npx @modelcontextprotocol/inspector java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar
```

Inspector skriver ut en URL i terminalen med et **auth-token**
(`http://localhost:6274/?MCP_PROXY_AUTH_TOKEN=…`). Åpne *akkurat den* URL-en — åpner du bare
`http://localhost:6274` får du «connection error». Inspector lister så verktøyene; kall
`about_application` og se svaret.

### Koble til Claude (stdio)

Legg serveren inn i host-konfigurasjonen som en stdio-server (Claude Desktop-eksempel). Bruk
**absolutt sti** til jar-en:

```json
{
  "mcpServers": {
    "vacation-booking": {
      "command": "java",
      "args": ["-jar", "/absolutt/sti/til/build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar"]
    }
  }
}
```

Spør deretter Claude: «hva er denne applikasjonen?» — den skal bruke verktøyet.

---

## Arkitektur

```
   ┌─────────────┐   JSON-RPC over stdio    ┌──────────────────────────────────────┐
   │ MCP-host    │ ◀──────────────────────▶ │ vacation-booking-mcp (Spring Boot)     │
   │ (Claude /   │                          │                                        │
   │  Inspector) │                          │  MCP-lag  ← DU BYGGER DETTE             │
   └─────────────┘                          │   @McpTool / @McpResource / @McpPrompt │
                                            │        │                               │
                                            │        ▼                               │
                                            │  Service-lag  (gitt)                   │
                                            │   DestinationService / PricingService  │
                                            │   / BookingService  (+ validering)     │
                                            │        │                               │
                                            │        ▼                               │
                                            │  Repository-lag  (gitt, JdbcTemplate)  │
                                            │        │                               │
                                            │        ▼                               │
                                            │  SQLite  (vacation.db)                 │
                                            └──────────────────────────────────────┘
```

- **MCP-laget** er det du implementerer i workshopen: `@McpTool`-, `@McpResource`- og
  `@McpPrompt`-annoterte metoder på Spring-beans. Spring AI sin annotasjons-scanner oppdager
  dem automatisk — ingen manuell registrering.
- **Service-laget** inneholder all forretningslogikk (validering, prisberegning med
  sesongpris-fallback, booking-tilstandsmaskin). Ferdig og testet.
- **Repository-laget** er dataaksess via `JdbcTemplate`. Ferdig.
- **SQLite** (`vacation.db`) opprettes og seedes automatisk ved oppstart fra `schema.sql`
  + `data.sql`. Den ligger i prosjektroten og er git-ignorert; slett den for å nullstille.

### Teknologi

| Komponent     | Valg                                                              |
|---------------|-------------------------------------------------------------------|
| Språk         | Java 21                                                           |
| Rammeverk     | Spring Boot 4.1                                                   |
| MCP           | Spring AI 2.0 (`spring-ai-starter-mcp-server`)                   |
| Byggeverktøy  | Gradle (Kotlin DSL), wrapper følger med                          |
| Database      | SQLite (`sqlite-jdbc`) + `JdbcTemplate`                          |
| Transport     | stdio (→ Streamable HTTP i Epic 7)                              |

---

## Domenemodell

| Tabell          | Innhold                                                            |
|-----------------|-------------------------------------------------------------------|
| `destinations`  | Reisemål: navn, land, beskrivelse, pris pr. natt, tilgjengelig    |
| `availability`  | Perioder pr. reisemål: fra/til-dato, kapasitet, valgfri sesongpris |
| `bookings`      | Bookinger: kunde, reisemål, datoer, antall reisende, pris, status |

**Pris pr. natt** = `season_price` for perioden hvis satt, ellers destinasjonens
`price_per_night`. Totalpris = pris pr. natt × netter × antall reisende.

**Booking-status (tilstandsmaskin):**

```
PENDING ──▶ CONFIRMED ──▶ PAID ──▶ COMPLETED
   │            │           │
   └────────────┴───────────┴──▶ CANCELLED
```

Domeneobjektene er Java-`record`-er i `domain/`: `Destination`, `Availability`, `Booking`,
og enum-en `BookingStatus` (med `canTransitionTo(...)`).

---

## Tjenestene du bygger på

Injiser tjenesten i en `@Component` og deleger til den. Ved feil kaster tjenestene
`ValidationException` (ugyldig input / brutt forretningsregel) eller `NotFoundException`.

### `DestinationService`
| Metode | Beskrivelse |
|--------|-------------|
| `List<Destination> listAvailable()` | Alle tilgjengelige reisemål. |
| `List<Destination> search(String query, String country, Double maxPricePerNight)` | Søk; alle parametre valgfrie (`null` = ikke filtrer). Avviser negativ pris. |

### `PricingService`
| Metode | Beskrivelse |
|--------|-------------|
| `Quote quote(long destinationId, LocalDate from, LocalDate to, int numTravelers)` | Validerer datoer/antall/reisemål, finner dekkende periode, beregner pris. Returnerer `Quote` (pris pr. natt, netter, totalpris). |
| `Availability findCoveringPeriod(long destinationId, LocalDate from, LocalDate to)` | Perioden som dekker datoene, ellers `ValidationException`. |

### `BookingService`
| Metode | Beskrivelse |
|--------|-------------|
| `Booking createBooking(String customerName, long destinationId, LocalDate from, LocalDate to, int numTravelers)` | Full validering + kapasitetssjekk + prisberegning; lagrer som `PENDING`. |
| `Booking get(long id)` | Henter én booking (`NotFoundException` hvis ukjent). |
| `List<Booking> list(BookingStatus status)` | Alle, eller filtrert på status (`null` = alle). |
| `Booking updateStatus(long id, BookingStatus target)` | Statusendring etter tilstandsmaskinen; avviser ulovlige overganger. |
| `Booking cancel(long id)` | Kansellerer og frigjør kapasitet. |

Trenger du finkornet aksess finnes også repositoriene direkte: `DestinationRepository`,
`AvailabilityRepository` (`findOverlapping`, `findCovering`, `findByDestinationId`),
`BookingRepository`.

---

## Slik lager du et MCP-verktøy

```java
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class DestinationTools {

    private final DestinationService destinations;

    public DestinationTools(DestinationService destinations) {
        this.destinations = destinations;
    }

    @McpTool(name = "list_destinations", description = "Lister alle tilgjengelige reisemål.")
    public List<Destination> listDestinations() {
        return destinations.listAvailable();
    }
}
```

Eksempel på det enkleste verktøyet finnes i
[`AboutTool.java`](src/main/java/no/computas/vacationmcp/tools/AboutTool.java). Tilsvarende
annotasjoner finnes for ressurser (`@McpResource`) og prompts (`@McpPrompt`).

> **Viktig (stdio):** Protokollen eier `stdout`. Ikke skriv til `System.out` fra
> verktøykode — bruk en logger (logg går til stderr + fil). Ellers korrumperes JSON-RPC-en.

---

## Prosjektstruktur

```
src/main/java/no/computas/vacationmcp/
  VacationBookingMcpApplication.java   # @SpringBootApplication
  tools/AboutTool.java                 # eksempel-@McpTool
  domain/                              # records + BookingStatus (tilstandsmaskin)
  repository/                          # JdbcTemplate-dataaksess (gitt)
  service/                             # forretningslogikk + Quote + exceptions (gitt)
  config/RegisteredToolsLogger.java    # logger registrerte tools ved oppstart
src/main/resources/
  application.properties               # MCP stdio + SQLite-config
  schema.sql / data.sql                # skjema + seed (5 reisemål, 6 perioder)
src/test/java/.../service/             # tester for forretningslaget
BACKLOG.md                             # workshop-oppgavene
```

---

## Oppgavene

Alt arbeidet er beskrevet i **[BACKLOG.md](BACKLOG.md)**, gruppert i epics fra «kom i gang»
til remote HTTP-transport. Ta oppgavene i rekkefølge.

## Feilsøking

- **«Appen henger» / ingen output:** Forventet — stdio-serveren venter på en klient. Logg
  går til konsollet (stderr) og til `logs/vacation-booking-mcp.log`.
- **Inspector «connection error»:** Bruk den tokeniserte URL-en, og kjør fra prosjektroten.
- **Nullstille data:** Slett `vacation.db` i prosjektroten; den gjenskapes ved neste oppstart.
- **Se registrerte verktøy ved oppstart:** styres av `workshop.log-registered-tools` i
  `application.properties` (default `true`).
