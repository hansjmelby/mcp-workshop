# MCP Workshop — Ferie-booking-server

Lær å bygge en **MCP-server** (Model Context Protocol) ved å bygge ut en ekte liten
tjeneste: en **ferie-booking-server** som lar en LLM (f.eks. Claude) søke etter reisemål,
sjekke tilgjengelighet og priser, og opprette bookinger.

Du starter med et **kjørbart skall** og jobber deg gjennom en **[backlog](BACKLOG.md)** av
oppgaver som steg for steg legger til verktøy (tools), ressurser (resources) og prompts.

## Forutsetninger

- **JDK 21** (`java -version` skal vise 21).
- Ingen lokal Gradle nødvendig — `./gradlew` laster den ned selv.
- For å koble til en host: **Claude Desktop** eller **Claude Code**, og gjerne
  **MCP Inspector** (`npx @modelcontextprotocol/inspector`) til feilsøking.

## Kom i gang

```bash
# 1. Bygg og kjør testene
./gradlew build

# 2. Bygg en kjørbar jar
./gradlew bootJar

# 3. Kjør serveren som en stdio-MCP-server
java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar
```

Serveren snakker JSON-RPC over **stdin/stdout** og venter på en MCP-klient. Den starter med
ett eksempel-verktøy, `about_application`, som svarer på hva applikasjonen er.

### Verifiser med MCP Inspector

```bash
npx @modelcontextprotocol/inspector java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar
```

Inspector lister verktøyene — kall `about_application` og se svaret.

### Koble til Claude (stdio)

Legg serveren inn i host-konfigurasjonen som en stdio-server, f.eks. (Claude Desktop):

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

## Hva som ligger i skallet

- **Spring Boot 4.1 + Spring AI 2.0** MCP-server-starter (Java 21).
- **SQLite-database** som opprettes og seedes automatisk ved oppstart
  (`schema.sql` + `data.sql`) med 5 reisemål og tilgjengelighetsperioder — alt på plass,
  du trenger ikke sette opp database selv.
- Ett eksempel-verktøy (`about_application`) som viser `@McpTool`-mønsteret.

## Domenemodell

| Tabell          | Innhold                                                            |
|-----------------|-------------------------------------------------------------------|
| `destinations`  | Reisemål: navn, land, beskrivelse, pris pr. natt, tilgjengelig    |
| `availability`  | Perioder pr. reisemål: fra/til-dato, kapasitet, sesongpris        |
| `bookings`      | Bookinger: kunde, reisemål, datoer, antall reisende, pris, status |

Booking-status: `PENDING → CONFIRMED → PAID → COMPLETED` (+ `CANCELLED`).

## Oppgavene

Alt arbeidet er beskrevet i **[BACKLOG.md](BACKLOG.md)**, gruppert i epics fra «kom i gang»
til remote HTTP-transport. Ta oppgavene i rekkefølge. Fasit finnes på `solution`-branchen —
men prøv selv først!

For tekniske detaljer, kommandoer og konvensjoner, se **[CLAUDE.md](CLAUDE.md)**.
