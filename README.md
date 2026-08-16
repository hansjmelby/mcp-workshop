# MCP Workshop — Ferie-booking-server

Lær å bygge en **MCP-server** (Model Context Protocol) ved å bygge ut en ekte liten
tjeneste: en **ferie-booking-server** som lar en LLM (f.eks. Claude) søke etter reisemål,
sjekke tilgjengelighet og priser, og opprette bookinger.

Du starter med et **kjørbart skall**, ser først MCP-protokollen på ledningen, og jobber deg
deretter gjennom en **[backlog](BACKLOG.md)** av oppgaver som steg for steg legger til verktøy
(tools), ressurser (resources) og prompts.

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

> **På Windows:** Kommandoene under er skrevet for bash/zsh. I PowerShell og cmd bruker du
> `.\gradlew.bat` i stedet for `./gradlew` — alt annet fungerer likt, og
> `npx @modelcontextprotocol/inspector …` kan limes inn uendret. Unntaket er
> [rå-tracen i «MCP under panseret»](#før-annotasjonene-mcp-under-panseret), som er ren bash og
> krever **Git Bash** eller **WSL**. Windows-spesifikke varianter er notert der de trengs.

## Kom i gang

```bash
# 1. Bygg og kjør testene            (Windows: .\gradlew.bat build)
./gradlew build

# 2. Bygg en kjørbar jar             (Windows: .\gradlew.bat bootJar)
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

### Før annotasjonene: MCP under panseret

Før du lager et Spring-verktøy, se hva en MCP-klient og server faktisk utveksler. MCP bruker
**JSON-RPC 2.0**, én JSON-melding per linje over stdio. Start alltid med `initialize`:
klienten foreslår protokollversjon og beskriver egne capabilities; serveren velger/svarer med
sin protokollversjon og capabilities. Deretter sender klienten den id-løse notifikasjonen
`notifications/initialized` før den ber om `tools/list`.

Denne tracen er tatt mot prosjektets nåværende skall (oppstartslogg på stderr er utelatt):

```jsonc
// klient → server
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"workshop-trace","version":"1.0"}}}

// server → klient: capability-forhandling
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-25","capabilities":{"completions":{},"logging":{},"prompts":{"listChanged":true},"resources":{"subscribe":false,"listChanged":true},"tools":{"listChanged":true}},"serverInfo":{"name":"vacation-booking-mcp","version":"0.0.1"}}}

// klient → server (notifikasjon har ingen id og får ikke svar)
{"jsonrpc":"2.0","method":"notifications/initialized"}

// klient → server
{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}

// server → klient
{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"about_application","title":"about_application","description":"Forklarer hva denne applikasjonen er og hva den brukes til.","inputSchema":{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{},"required":[]},"annotations":{"title":"","readOnlyHint":false,"destructiveHint":true,"idempotentHint":false,"openWorldHint":true}}]}}
```

Det siste svaret er kontrakten klienten bruker for å kalle et verktøy. `inputSchema` er
**JSON Schema**: `type: object` betyr at argumentene sendes som et JSON-objekt;
`properties` beskriver feltene, og `required` angir obligatoriske felt. Her er begge tomme
fordi `about_application` ikke har parametere. Når du senere legger til
`@McpToolParam`, er det Spring AI som av annotasjonen genererer feltene i denne kontrakten.
Det er derfor verdt å inspisere `tools/list` igjen etter hver ny tool-oppgave.

Du kan gjenskape tracen uten Inspector. Vent mellom meldingene: serveren må ha svart på
`initialize` før neste forespørsel sendes.

```bash
{ \
  printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"trace","version":"1.0"}}}'; \
  sleep 2; \
  printf '%s\n' '{"jsonrpc":"2.0","method":"notifications/initialized"}'; \
  sleep 1; \
  printf '%s\n' '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'; \
  sleep 2; \
} | java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar
```

> **Windows:** Denne kommandoen er ren bash — kjør den i **Git Bash** eller **WSL**. Alternativt
> (og det fungerer i PowerShell og cmd): start serveren med `java -jar …`, og lim inn de tre
> klient-meldingene én og én med Enter etter hver. Serveren leser stdin linje for linje, så du
> ser håndtrykket skje interaktivt.

I en vanlig host gjør Inspector eller Claude denne håndtrykksekvensen for deg. Verktøyene er
fortsatt bare JSON-RPC-metoder; Spring-annotasjonene er den praktiske måten å deklarere
serverens del av kontrakten på.

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

Serveren registreres som en stdio-server med **absolutt sti** til jar-en. Bygg den først
(`./gradlew bootJar`). Skal du i stedet koble til den kjørende HTTP-serveren, se
[Koble til Claude (HTTP)](#koble-til-claude-http) lenger ned.

#### Claude Code (CLI)

Kjør fra prosjektroten — `$(pwd)` fyller inn den absolutte stien for deg:

```bash
claude mcp add-json vacation-booking \
  "{\"command\":\"java\",\"args\":[\"-jar\",\"$(pwd)/build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar\"]}"
```

Sjekk at den svarer:

```bash
claude mcp get vacation-booking     # skal vise "Status: ✔ Connected"
```

Serveren legges i **local scope** — privat for deg i dette prosjektet. Vil du dele
oppsettet med de andre deltakerne, bruk `-s project` (skriver til `.mcp.json` i repoet),
men da må stien være **relativ** (`build/libs/…`), siden en absolutt sti ikke fungerer på
andre maskiner.

Fjern den igjen med `claude mcp remove vacation-booking -s local`.

#### Claude Desktop

Åpne konfigurasjonsfila via **Settings → Developer → Edit Config**. Den ligger i
`~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) eller
`%APPDATA%\Claude\claude_desktop_config.json` (Windows). Legg inn:

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

> **Windows:** Stien må ha **doble backslash** i JSON — `"C:\\Users\\deg\\mcp-workshop\\build\\libs\\vacation-booking-mcp-0.0.1-SNAPSHOT.jar"`.
> Enkelt backslash gjør JSON-en ugyldig, og Claude feiler stille uten en tydelig melding.
> (Forward slashes fungerer også: `"C:/Users/deg/mcp-workshop/build/libs/…"`.)
> `add-json`-kommandoen over er bash — kjør den i **Git Bash** eller **WSL**.

Stien **må være absolutt**. Claude Desktop starter jar-en fra sin egen arbeidskatalog, ikke
fra prosjektroten, så `build/libs/…` gir «server disconnected» uten noen tydelig forklaring.

Spør deretter Claude: «hva er denne applikasjonen?» — den skal bruke verktøyet.

#### Tre ting som ofte forvirrer

- **Serveren kobles opp ved oppstart.** Legger du den til mens Claude Code / Claude Desktop
  kjører, dukker ikke verktøyene opp før du starter på nytt. (`/mcp` viser status.)
- **Jar-en er et øyeblikksbilde.** Nye `@McpTool`-er blir ikke synlige før du har kjørt
  `./gradlew bootJar` på nytt *og* restartet hosten. Dette er den vanligste grunnen til at
  «verktøyet mitt vises ikke».
- **`./gradlew clean` sletter jar-en under føttene på en kjørende host.** Hosten holder
  serverprosessen i live fra den startet den, så et `clean build` river bort fila den peker på
  og tilkoblingen faller ut. Bygg ferdig først, og koble så til igjen: `/mcp` i Claude Code,
  eller restart av Claude Desktop.

### Kjør serveren over HTTP i stedet (Streamable HTTP)

Samme jar, samme verktøy — men som en vanlig webtjeneste i stedet for en prosess hosten
starter. Slås på med Spring-profilen **`http`**:

```bash
WORKSHOP_HTTP_AUTH_TOKEN=hemmelig-token \
  java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar --spring.profiles.active=http
# eller: SPRING_PROFILES_ACTIVE=http java -jar build/libs/…jar
# (PowerShell: $env:SPRING_PROFILES_ACTIVE="http"; $env:WORKSHOP_HTTP_AUTH_TOKEN="hemmelig-token"; java -jar build\libs\…jar)
```

Endepunktet er **`http://localhost:8080/mcp`**. Uten profilen er serveren nøyaktig som før —
en stdio-server som ikke starter noen webserver. Alt som er profilspesifikt ligger i
[`application-http.properties`](src/main/resources/application-http.properties).

Tre ting skiller en Streamable HTTP-klient fra en stdio-klient, og alle er lette å gå på:

1. **`Accept` må inneholde *begge* typene** — `application/json, text/event-stream`. Sender du
   bare `application/json`, svarer serveren `400`.
2. **Sesjons-id-en fra `initialize` må sendes med videre.** Svaret på `initialize` har headeren
   `Mcp-Session-Id: <uuid>`; alle senere kall må ha den samme headeren tilbake. Uten den får du
   `Session ID missing`.
3. **Hvert kall må ha `Authorization: Bearer <token>`** — også `GET` (SSE-strømmen) og `DELETE`
   (avslutt sesjon). Uten, eller med feil token, får du `401` (se under).

#### Tokenet

En stdio-server er like utsatt som prosessen hosten startet; et HTTP-endepunkt kan alle som når
porten kalle. Derfor krever `http`-profilen et **bearer-token** ([`BearerTokenAuthFilter`](src/main/java/no/computas/vacationmcp/config/BearerTokenAuthFilter.java)):

| Hvordan | Kommando |
|---------|----------|
| Miljøvariabel (anbefalt) | `WORKSHOP_HTTP_AUTH_TOKEN=hemmelig-token java -jar …` |
| Kommandolinje | `java -jar … --spring.profiles.active=http --workshop.http.auth.token=hemmelig-token` |
| Ingenting | serveren **genererer** et token ved oppstart og skriver det i loggen |

Setter du ingenting, ser oppstartsloggen slik ut — kopier tokenet derfra:

```
WARN … BearerTokenAuthFilter : Ingen workshop.http.auth.token er satt — genererte et tilfeldig token for denne kjøringen:

    Authorization: Bearer 408f457c-1953-4af9-944d-06317c78ff0c
```

Endepunktet er altså aldri åpent ved et uhell. Vil du likevel kjøre uten (demo/feilsøking):
`--workshop.http.auth.enabled=false`, som gir et tydelig WARN om at hvem som helst kan kalle
`create_booking`. **stdio-modus er helt uberørt** — der finnes ingen nettverksflate å beskytte,
og filteret opprettes ikke i det hele tatt.

> **Dette er ikke OAuth.** Et delt, statisk token sier bare «du kjenner hemmeligheten»: ingen
> bruker, ingen scopes, ingen utløpstid. MCP-spesifikasjonen har en egen autorisasjonsmodell for
> remote-servere (OAuth 2.1 + Protected Resource Metadata) — se
> [MCP · Authorization](https://modelcontextprotocol.io/specification/draft/basic/authorization).

En hel MCP-økt med `curl` (bash — Git Bash/WSL på Windows):

```bash
TOKEN=hemmelig-token   # samme verdi som serveren ble startet med

# 1) initialize — plukk sesjons-id-en ut av responsheaderne
SID=$(curl -sS -D - -o /dev/null -X POST http://localhost:8080/mcp \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}' \
  | tr -d '\r' | sed -n 's/^Mcp-Session-Id: //p')

# 2) notifications/initialized — ingen id, ingen respons (svarer 202 Accepted)
curl -sS -X POST http://localhost:8080/mcp \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $SID" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

# 3) tools/list — svaret kommer som SSE; `sed -n 's/^data://p'` plukker ut JSON-en
curl -sS -X POST http://localhost:8080/mcp \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $SID" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' | sed -n 's/^data://p'

# 4) tools/call
curl -sS -X POST http://localhost:8080/mcp \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $SID" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"search_destinations","arguments":{"country":"Norge"}}}' \
  | sed -n 's/^data://p'

# 5) avslutt sesjonen (valgfritt) — også DELETE krever token
curl -sS -X DELETE http://localhost:8080/mcp \
  -H "Authorization: Bearer $TOKEN" -H "Mcp-Session-Id: $SID"
```

Slik ser et avvist kall ut. Merk `401` (ikke `403`) og `WWW-Authenticate`-headeren, som er det
som forteller klienten *hvordan* den skal autentisere seg:

```http
# uten token
HTTP/1.1 401
WWW-Authenticate: Bearer realm="vacation-booking-mcp"

# med feil token
HTTP/1.1 401
WWW-Authenticate: Bearer realm="vacation-booking-mcp", error="invalid_token", error_description="ugyldig token"

{"error":"unauthorized","message":"Send 'Authorization: Bearer <token>'. …"}
```

Svaret er ren HTTP, ikke JSON-RPC: sjekken skjer *før* protokollen, og en klient som ikke
slipper inn har ingen sesjon å feile innenfor.

> **Merk formatet:** svaret på `initialize` er rå `application/json`, mens svarene *etter*
> håndtrykket kommer som `text/event-stream` (`event:message` + `data:{…}`). Det er derfor
> `sed`-en står der.

I HTTP-modus eier ikke protokollen stdout lenger — JSON-RPC går over HTTP-body-en. Derfor
logger serveren til **stdout** i denne profilen (og fortsatt til
`logs/vacation-booking-mcp.log`), i motsetning til stdio-modus der alt må til stderr.

### Koble til Claude (HTTP)

Samme server, men registrert som en **remote** server i stedet for en kommando. Den store
forskjellen: **du starter serveren, ikke hosten.** Start den først (se over), og la den kjøre —
registrerer du en adresse ingenting lytter på, får du `ConnectionRefused` med en gang.

#### Claude Code (CLI)

```bash
claude mcp add --transport http vacation-booking-http http://localhost:8080/mcp \
  --header "Authorization: Bearer hemmelig-token"
```

`--transport http` (kort: `-t http`) velger Streamable HTTP, og `--header` (kort: `-H`) sendes
med på **hvert** kall — også `GET` (SSE-strømmen) og `DELETE`. Sjekk at den svarer:

```bash
claude mcp list                       # … (HTTP) - ✔ Connected
claude mcp get vacation-booking-http  # viser Type, URL og headere
```

Samme registrering i JSON-form — nyttig hvis du allerede bruker `add-json` for stdio-varianten:

```bash
claude mcp add-json vacation-booking-http \
  '{"type":"http","url":"http://localhost:8080/mcp","headers":{"Authorization":"Bearer hemmelig-token"}}'
```

> **Hold tokenet ute av git.** `${MILJØVARIABEL}` i header-verdien ekspanderes fra miljøet der
> Claude Code startes, så dette virker — og er det du vil ha med `-s project`, siden `.mcp.json`
> havner i repoet:
>
> ```bash
> claude mcp add-json vacation-booking-http -s project \
>   '{"type":"http","url":"http://localhost:8080/mcp","headers":{"Authorization":"Bearer ${WORKSHOP_HTTP_AUTH_TOKEN}"}}'
> ```
>
> Da må hver deltaker ha `WORKSHOP_HTTP_AUTH_TOKEN` satt i skallet *før* `claude` startes.

Fjern den igjen med `claude mcp remove vacation-booking-http -s local`.

#### Claude Desktop

Claude Desktop tar **ikke** remote-servere i `claude_desktop_config.json` — den fila er for
kommandoer den selv skal starte (stdio). Remote-servere legges inn under **Settings →
Connectors → Add custom connector**, der du oppgir en URL og deretter autentiserer deg slik
serveren ber om — i praksis OAuth. Det finnes ikke noe felt for «send denne headeren», så det
statiske tokenet fra T-18 passer ikke inn i den flyten.

To veier videre:

1. **Bruk stdio-oppskriften i Desktop** (den over), og HTTP-varianten i Claude Code. Det er det
   enkleste under workshopen.
2. **Legg en stdio→HTTP-bro imellom.** `mcp-remote` er en liten proxy Desktop *kan* starte som
   en vanlig kommando, og den kan sende en fast header:

   ```json
   {
     "mcpServers": {
       "vacation-booking-http": {
         "command": "npx",
         "args": [
           "-y", "mcp-remote",
           "http://localhost:8080/mcp",
           "--header", "Authorization: Bearer hemmelig-token"
         ]
       }
     }
   }
   ```

   Broen er verifisert mot denne serveren (fra Claude Code, som kan starte nøyaktig samme
   kommando) — `✔ Connected`. Selve Desktop-oppsettet er *ikke* klikket gjennom her.

#### Hva som er annerledes enn stdio

| | stdio | HTTP |
|---|---|---|
| Hvem starter serveren | hosten, når *den* starter | **du** — den må kjøre før du registrerer den |
| Adresse | sti til jar-en | `http://localhost:8080/mcp` |
| Auth | ingen — prosessen arver dine rettigheter | `Authorization: Bearer …` på hvert kall |
| Antall klienter | én prosess per host | én prosess, mange klienter samtidig |
| Ved kodeendring | `bootJar` **+ restart av hosten** | `bootJar` + restart av **serveren**; hosten kobler seg opp igjen selv |
| Sesjon | prosessens levetid | `Mcp-Session-Id`-header per tilkobling; hosten forhandler ny ved behov |
| Når serveren er nede | hosten starter den på nytt | `✘ Failed to connect — ConnectionRefused` til du starter den igjen |

**Reconnect:** hosten holder ikke på én evig sesjon. Hver helsesjekk (`claude mcp list`,
`claude mcp get`) og hver nye Claude-økt kjører sitt eget `initialize` og får sin egen
sesjons-id. Restarter du serveren, blir gamle sesjons-id-er ugyldige (`-32603 Session not
found`), men **konfigurasjonen er uendret** — neste kall kobler seg opp på nytt av seg selv.
Det er den store praktiske gevinsten mot stdio, der en ny jar krever at hele hosten restartes.

#### Feilsøking

| Symptom | Årsak | Fiks |
|---------|-------|------|
| `ConnectionRefused: Unable to connect` | Serveren kjører ikke, eller ikke med `http`-profilen | Start den, og se etter `Tomcat started on port 8080` i loggen |
| `Server rejected the configured Authorization header (HTTP 401)` | Feil token — typisk fordi serveren ble restartet uten `WORKSHOP_HTTP_AUTH_TOKEN` og genererte et nytt | Sett et fast token, eller oppdater registreringen |
| `Dynamic Client Registration rejected (HTTP 401)` | Du glemte `--header`. Uten en Authorization-header tolker hosten `401`-et vårt som en OAuth-utfordring og prøver seg på `/.well-known/…` og `POST /register` | Legg på `--header "Authorization: Bearer …"` — da slås OAuth-fallbacken av |
| Verktøyene mangler i en åpen Claude-økt | Serveren ble registrert etter at økten startet | Start økten på nytt (`/mcp` viser status) |
| Serveren vil ikke stoppe med Ctrl+C | En host holder SSE-strømmen (`GET /mcp`) åpen, så Spring sin graceful shutdown venter | Porten frigis med én gang uansett; trykk en gang til, eller `kill -9` |

> **Bare for localhost.** Skal andre nå serveren, må du binde til noe annet enn loopback
> (`--server.address=0.0.0.0`), åpne porten i brannmuren — og helst legge en TLS-terminerende
> proxy foran, slik at URL-en blir `https://…`. Et statisk token over **ren HTTP** sendes i
> klartekst og kan leses og gjenbrukes av hvem som helst på veien; det holder på en
> workshop-maskin og ingen andre steder. Utenfor workshopen er svaret MCP-spesifikasjonens
> OAuth 2.1-modell, ikke en delt hemmelighet.

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
> verktøykode — bruk en logger (logg går til stderr + fil, se
> [Hvor finner jeg loggen?](#hvor-finner-jeg-loggen)). Ellers korrumperes JSON-RPC-en.

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

## Hvor finner jeg loggen?

Fordi protokollen eier `stdout`, går **all logg til `stderr` + fil**. Terminalen tegner begge
strømmene i samme vindu, så de *ser* ut som én — men de er separate, og det er nettopp derfor
stdio-serveren virker. Vil du se dem hver for seg, kast den andre:

```bash
java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar > /dev/null   # kun loggen (stderr)
java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar 2>/dev/null   # kun JSON-RPC (stdout)
```

```powershell
# PowerShell — samme to kommandoer (cmd bruker > NUL og 2> NUL)
java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar > $null
java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar 2> $null
```

Den andre kommandoen viser hva en MCP-klient faktisk mottar: ingenting annet enn protokollen.

**Loggfila er det mest praktiske under workshopen** — den skrives uansett hvem som startet
serveren. La den stå åpen i et eget terminalvindu:

```bash
tail -f logs/vacation-booking-mcp.log                        # bash/zsh
Get-Content -Wait logs\vacation-booking-mcp.log              # PowerShell
```

Dette blir viktig fra og med **T-02**: når Claude Desktop starter jar-en som subprosess, har du
ingen terminal å se i, og loggen er det eneste som forklarer hvorfor et verktøy feilet.

- **MCP Inspector** viser serverens stderr i panelet «Error output from MCP server».
- **Claude Desktop** skriver den til `~/Library/Logs/Claude/mcp-server-vacation-booking.log`
  (macOS) eller `%APPDATA%\Claude\logs\mcp-server-vacation-booking.log` (Windows).

## Feilsøking

- **«Appen henger» / ingen output:** Forventet — stdio-serveren venter på en klient. Se
  [Hvor finner jeg loggen?](#hvor-finner-jeg-loggen) for å følge med på hva den gjør.
- **`WARN … BeanPostProcessorChecker` ved oppstart:** Ufarlig. Kommer fra Spring AI sin egen
  autokonfigurasjon av annotasjons-scanneren, ikke fra din kode. Serveren starter normalt.
- **Inspector «connection error»:** Bruk den tokeniserte URL-en, og kjør fra prosjektroten.
- **Nullstille data:** Slett `vacation.db` i prosjektroten; den gjenskapes ved neste oppstart.
- **Se registrerte verktøy ved oppstart:** styres av `workshop.log-registered-tools` i
  `application.properties` (default `true`).
