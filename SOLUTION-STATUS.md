# Løsningsstatus — `solution`-branchen

Fasit for [BACKLOG.md](BACKLOG.md). Denne fila er **den delte statustavla**: hver oppgave
løses av en egen agent som leser denne fila først, implementerer, og oppdaterer raden sin
etterpå. Én commit per oppgave.

**Statuskoder:** ⬜ ikke startet · 🟡 pågår · ✅ ferdig · 📝 dokumentert (manuell/interaktiv oppgave) · ⏭️ hoppet over

## Oversikt

| Oppgave | Hva | Status | Leveranse |
|---------|-----|--------|-----------|
| T-00 | MCP-protokollen under panseret | 📝 | Verifisert trace + capability-analyse + svar ([se under](#t-00--se-mcp-protokollen-før-du-bruker-spring-annotasjoner)) |
| T-01 | Bygg, kjør og inspiser skallet | 📝 | Grønn `clean build` + jar; `about_application` verifisert via stdio og Inspector-CLI; web-UI-et dokumentert ([se under](#t-01--bygg-kjør-og-inspiser-skallet)) |
| T-02 | Koble serveren til Claude | 📝 | Registrert som stdio-server i Claude Code (`✔ Connected`) og `about_application` kalt gjennom hosten; README-oppskriften verifisert + presisert ([se under](#t-02--koble-serveren-til-claude)) |
| T-03 | `list_destinations` | ✅ | `tools/DestinationTools.java` → verktøyet `list_destinations`; test `tools/DestinationToolsTest.java` ([se under](#t-03--list_destinations)) |
| T-04 | `search_destinations` | ✅ | `search_destinations` med tre valgfrie parametere i `tools/DestinationTools.java` (+ krysshenvisning fra `list_destinations`); felles feilhåndtering etablert; 6 nye tester ([se under](#t-04--search_destinations)) |
| T-05 | `check_availability` | ✅ | `tools/AvailabilityTools.java` → verktøyet `check_availability` (`LocalDate`-parametere + `AvailabilityResult`-konvolutt); felles datobeslutning etablert; test `tools/AvailabilityToolsTest.java` med 9 tester ([se under](#t-05--check_availability)) |
| T-06 | `get_quote` | ✅ | `tools/PricingTools.java` → verktøyet `get_quote` (fire obligatoriske parametere — første ikke-tomme `required`; `Quote` returneres uendret); test `tools/PricingToolsTest.java` med 13 tester ([se under](#t-06--get_quote)) |
| T-07 | `create_booking` | ⬜ | — |
| T-08 | `get_booking` | ⬜ | — |
| T-09 | `update_booking_status` | ⬜ | — |
| T-10 | `list_bookings` | ⬜ | — |
| T-11 | Avvis overbooking | ⬜ | — |
| T-12 | `cancel_booking` | ⬜ | — |
| T-13 | Destinasjoner som Resource | ⬜ | — |
| T-14 | Bookinger som Resource | ⬜ | — |
| T-15 | Prompts | ⬜ | — |
| T-16 | `bookings_report` | ⬜ | — |
| T-17 | Streamable HTTP-transport | ⬜ | — |
| T-18 | Bearer-token-auth | ⬜ | — |
| T-19 | Koble Claude til remote-serveren | ⬜ | — |
| T-20 | Elicitation (bonus) | ⬜ | — |
| T-21 | Sampling (bonus) | ⬜ | — |

## Beslutninger som gjelder hele løsningen

- **stdio er fortsatt default.** T-17/T-18 legges bak Spring-profilen `http`, slik at
  `java -jar …` uten profil fortsatt er en stdio-server og T-01/T-02/README holder seg gyldige.
- **Verktøykode skal delegere** til `service/`-laget — ingen ny forretnings- eller SQL-kode.
- **Feilhåndtering (avgjort i T-04 — følg denne):** `ValidationException`/`NotFoundException`
  fra tjenestelaget **får boble ut av verktøymetoden**. Ingen `try/catch` i `tools/`-laget.
  Spring AI fanger exception-en i `SyncMcpToolMethodCallback.apply(...)` og returnerer et
  `CallToolResult` med `isError: true` og feilmeldingen som tekst — protokollen svarer altså
  `result`, ikke JSON-RPC `error`, og klienten får aldri en stacktrace. Detaljer, den faktiske
  JSON-en og hvorfor alternativene ble valgt bort: [T-04-seksjonen](#t-04--search_destinations).
- Hver oppgave = én commit med meldingsprefiks `T-xx: …`.

### Struktur for verktøyklasser (etablert i T-03 — følg denne)

- **Én klasse per domeneområde**, ikke én per verktøy, i `no.computas.vacationmcp.tools`
  med suffikset `Tools`. Planlagt fordeling:
  `DestinationTools` (T-03 ✅, T-04 ✅) · `AvailabilityTools` (T-05 ✅) · `PricingTools` (T-06 ✅) ·
  `BookingTools` (T-07–T-12). `AboutTool` (entall) står igjen som eksempel-klassen fra skallet.
- **Konstruktørinjeksjon** av tjenesten fra `service/`. Klassen er en fasade: den kaller
  tjenesten og returnerer resultatet — ingen mapping-, formaterings- eller regel-logikk.
- **Metodenavnet er camelCase av verktøynavnet** (`list_destinations` → `listDestinations`),
  og `name` settes alltid eksplisitt i `@McpTool` så snake_case-navnet i protokollen ikke
  avhenger av Java-metodenavnet.
- **Returtype = domene-record (eller `List<…>` av dem)**, ikke håndformatert tekst. Spring AI
  serialiserer alt som ikke er `String` til JSON i tekstblokken
  (`AbstractMcpToolMethodCallback.convertValueToCallToolResult`). Begrunnelse i T-03-seksjonen.
- **`annotations`-hintene settes bevisst.** Lesende verktøy:
  `readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false`.
  Skrivende verktøy (T-07, T-09, T-12) skal *ikke* arve dette — sett `readOnlyHint = false` og
  vurder `destructiveHint` selv. Merk at `destructiveHint` og `idempotentHint` bare er
  meningsbærende når `readOnlyHint == false`; vi setter dem likevel eksplisitt for å unngå at
  Spring AI sin default (`destructiveHint = true`) står igjen i katalogen.
- **`description` er prompt-engineering**: si *hva* verktøyet gir, *når* modellen skal velge
  det framfor naboverktøyet, hvilke felt som kommer tilbake, og hvilke forbehold som gjelder.
  Legg til krysshenvisning begge veier når nabo-verktøyet finnes (gjort i T-04:
  `list_destinations` og `search_destinations` peker på hverandre).
- **Valgfrie parametere må merkes eksplisitt** med `@McpToolParam(required = false, …)`, og
  Java-typen må være bokset (`Double`/`Integer`/`Long`) der `null` skal bety «ikke oppgitt».
  Se T-04 for den faktiske `inputSchema`-en dette gir.

### Datoer over MCP-grensen (avgjort i T-05 — følg denne)

**Bruk `java.time.LocalDate` som parametertype, ikke `String` med egen parsing.** Gjelder alle
verktøy som tar imot datoer: T-05 ✅, T-06 ✅, og videre T-07 (`create_booking`) og
T-12 (`cancel_booking`) dersom de trenger datoer.

Begge premissene er verifisert empirisk mot den ekte JSON-en (se
[T-05-seksjonen](#t-05--check_availability) for tracen):

1. **Skjemaet blir riktig av seg selv.** Spring AI utleder
   `{"type":"string","format":"date"}` for en `LocalDate` — nøyaktig den standardiserte
   JSON Schema-måten å si «ISO-8601-dato» på. Med `String` hadde modellen fått et bart
   `{"type":"string"}`, altså *mindre* informasjon, og formatkravet ville bare eksistert som
   prosa i `description`.
2. **Deserialiseringen virker.** `"2026-07-01"` blir en `LocalDate` gjennom protokollen uten
   noen konfigurasjon (Jackson sin `JavaTimeModule` er på klassestien via Spring Boot).

Konsekvensene å kjenne til:

- **ISO-valideringen skjer før metoden kalles.** En ugyldig streng blir aldri en `LocalDate`,
  så verktøykoden trenger ingen `try/catch` rundt parsing. Feilen kommer ut som
  `isError: true` med teksten `Conversion from JSON to java.time.LocalDate failed` +
  `Text 'i morgen' could not be parsed at index 0`.
- **Meldingen kan vi ikke styre.** Det er prisen for valget, og den er den samme kosmetiske
  støyen som `Error invoking method: …`-linja T-04 allerede aksepterte: Java-navn lekker,
  men innholdet er lesbart og handlingsbart for modellen. Kompenser i
  `@McpToolParam(description = …)` — skriv formatet (`yyyy-MM-dd`), et eksempel, og hvilke
  skrivemåter som *ikke* virker.
- **`format: "date"` håndheves ikke av skjemavalideringen** (JSON Schema behandler `format`
  som en annotasjon, ikke en begrensning), så «01.07.2026» slipper gjennom validatoren og
  stoppes først av Jackson. Feil *JSON-type* stoppes derimot av validatoren, før metoden:
  `{"from": 20260701}` → `input validation failed: … [/from: integer funnet, string forventet]`.
- **Datoregler som skjemaet ikke kan uttrykke** (`from < to`, `numTravelers ≥ 1`) hører hjemme
  i tjenestelaget der det finnes et — `PricingService.quote(...)` og `BookingService` gjør
  dette allerede, så T-06/T-07 skal *ikke* duplisere det i verktøyet. T-05 er unntaket:
  `AvailabilityRepository` er rent dataaksess, så verktøyet validerer selv, med
  `ValidationException` og **ordrett samme meldinger** som `PricingService`
  (`"fra-dato må være før til-dato"`, `"fra- og til-dato må oppgis"`).

---

## Detaljer og svar på oppgavenes spørsmål

<!-- Hver agent legger til sin egen seksjon her, i rekkefølge. -->

### T-00 · Se MCP-protokollen før du bruker Spring-annotasjoner

Ingen kodeendring — dette er en observasjonsoppgave. Tracen under er **faktisk kjørt** mot
`build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar` (bygget med `./gradlew bootJar`), med
stdout og stderr fanget hver for seg, slik at JSON-RPC-linjene er rene:

```bash
{ \
  printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"workshop-trace","version":"1.0"}}}'; \
  sleep 2; \
  printf '%s\n' '{"jsonrpc":"2.0","method":"notifications/initialized"}'; \
  sleep 1; \
  printf '%s\n' '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'; \
  sleep 2; \
} | java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar > stdout.jsonl 2> stderr.log
```

#### Den faktiske tracen

```jsonc
// 1) klient → server: initialize (request, har id)
//    Klienten foreslår protokollversjon og oppgir sine egne capabilities (her: ingen).
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"workshop-trace","version":"1.0"}}}

// 2) server → klient: initialize-respons (samme id=1) — capability-forhandlingen
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-25","capabilities":{"completions":{},"logging":{},"prompts":{"listChanged":true},"resources":{"subscribe":false,"listChanged":true},"tools":{"listChanged":true}},"serverInfo":{"name":"vacation-booking-mcp","version":"0.0.1"}}}

// 3) klient → server: notifications/initialized — ingen id, og INGEN linje kommer tilbake.
//    Håndtrykket er ferdig; nå er det lov å kalle tools/resources/prompts.
{"jsonrpc":"2.0","method":"notifications/initialized"}

// 4) klient → server: tools/list (request, id=2)
{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}

// 5) server → klient: verktøykatalogen med JSON Schema-kontrakten
{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"about_application","title":"about_application","description":"Forklarer hva denne applikasjonen er og hva den brukes til.","inputSchema":{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{},"required":[]},"annotations":{"title":"","readOnlyHint":false,"destructiveHint":true,"idempotentHint":false,"openWorldHint":true}}]}}
```

Stdout inneholdt **nøyaktig to linjer** — svaret på `initialize` og svaret på `tools/list`.
Det er den enkleste mulige empiriske bekreftelsen på at en notifikasjon ikke gir respons.
Hele Spring-oppstartsloggen (banner, Hikari, `McpServerAutoConfiguration`,
`RegisteredToolsLogger: Tilgjengelige MCP-tools (1): [about_application]`) lå på stderr og
forurenset ikke protokollen — akkurat slik `logback-spring.xml` er satt opp.

#### Capabilities: hva serveren annonserer vs. hva skallet faktisk har

Serveren annonserer fem capabilities. En ekstra sondering (samme håndtrykk, men med
`resources/list`, `resources/templates/list`, `prompts/list` og et `tools/call`) ga:

```jsonc
{"jsonrpc":"2.0","id":2,"result":{"resources":[]}}
{"jsonrpc":"2.0","id":3,"result":{"resourceTemplates":[]}}
{"jsonrpc":"2.0","id":4,"result":{"prompts":[]}}
{"jsonrpc":"2.0","id":5,"result":{"content":[{"type":"text","text":"Dette er en ferie-booking MCP-server …"}],"isError":false}}
```

| Capability | Annonsert | Faktisk innhold i skallet | Kommentar |
|------------|-----------|---------------------------|-----------|
| `tools` | `{"listChanged":true}` | 1 verktøy: `about_application` | Eneste capability med reelt innhold i dag. `listChanged` = serveren lover å varsle hvis lista endrer seg. |
| `resources` | `{"subscribe":false,"listChanged":true}` | `resources: []`, `resourceTemplates: []` | Annonsert, men tomt til T-13/T-14. `subscribe:false` = ingen abonnement på endringer i én enkelt ressurs. |
| `prompts` | `{"listChanged":true}` | `prompts: []` | Annonsert, men tomt til T-15. |
| `logging` | `{}` | Ingen `@McpLogging`-bruk; ingen `notifications/message` sendes | Slått på fordi Spring AI aktiverer den som default. |
| `completions` | `{}` | Ingen `@McpComplete`-metoder | Samme: default-på fra autokonfigurasjonen. |

**Poenget:** capability-blokken sier hvilke *metode-familier* serveren svarer på — ikke at
det finnes innhold i dem. Spring AI slår på tools/resources/prompts/completions/logging
uavhengig av om du har annotert noe (se `McpServerAutoConfiguration`-linjene i stderr:
«Enable resources capabilities», «Enable prompts capabilities», «Enable completions
capabilities»). En klient som ser `prompts` i capabilities må derfor fortsatt kalle
`prompts/list` for å oppdage at lista er tom. Etter hvert som backloggen jobbes gjennom
fylles disse listene ut uten at capability-blokken endrer seg.

#### Svar på oppgavens spørsmål

**1. Hvorfor har `notifications/initialized` ingen `id` eller respons?**

Fordi den er en **notifikasjon**, ikke en request. JSON-RPC 2.0 skiller på nettopp `id`:
en melding med `id` er en request som *skal* få nøyaktig ett svar med samme `id`
(`result` eller `error`); en melding uten `id` er en notifikasjon, og spesifikasjonen sier
eksplisitt at mottakeren ikke skal svare på den — det finnes ingen `id` å korrelere et svar
mot. `id`-en er altså ikke bare metadata: den *er* mekanismen som lar flere forespørsler
være i luften samtidig over én stream og fortsatt pares med riktig svar.

Semantisk passer det: `notifications/initialized` er klienten som sier «jeg har mottatt og
akseptert capability-svaret ditt, håndtrykket er ferdig». Det er ren enveis-informasjon —
serveren har ingenting å rapportere tilbake, og klienten venter ikke på noe. Meldingen
markerer overgangen fra initialiseringsfasen til normal drift; først etter den er det lov å
sende `tools/list`, `tools/call` osv. Det er også derfor røyktest-kommandoen har `sleep`
mellom linjene: uten `id` finnes det ikke noe svar å vente på, så vi må vente på klokka i
stedet. Samme mønster gjelder de andre `notifications/*`-meldingene (`tools/list_changed`,
`message`, `cancelled`, `progress`) — alle er id-løse og ubesvarte.

**2. Hvorfor har `about_application` tomme `properties` og `required`?**

Fordi verktøyet ikke tar noen argumenter. Metoden i
[`AboutTool.java`](src/main/java/no/computas/vacationmcp/tools/AboutTool.java) er
`public String aboutApplication()` — parameterløs. Spring AI genererer `inputSchema` ved
å reflektere over metodesignaturen (`JsonSchemaGenerator.generateForMethodInput`): den
itererer over `method.getParameters()`, legger hver parameter inn i `properties` og hvert
obligatoriske parameternavn i `required`. Med null parametere blir begge tomme.

Merk at feltene fortsatt er *til stede*, ikke utelatt. `inputSchema` er en obligatorisk del
av tool-kontrakten i MCP, og et gyldig JSON Schema for «et objekt uten felt» er nettopp
`{"type":"object","properties":{},"required":[]}`. Klienten kaller derfor verktøyet med
`"arguments":{}` — som verifisert i sonderingen over, der `tools/call` med tomt
argument-objekt ga `isError:false` og teksten fra verktøyet. Skjemaet er skrevet i JSON
Schema draft 2020-12 (`$schema`-feltet), og det er dette skjemaet LLM-en får se når den
skal bestemme *om* og *hvordan* verktøyet skal kalles — beskrivelsen alene er ikke nok.

**3. Hva endrer seg i `inputSchema` når et tool får en obligatorisk parameter?**

Med en `@McpToolParam(description = "…", required = true) String query` på metoden vil
`inputSchema` for det verktøyet se omtrent slik ut:

```jsonc
"inputSchema": {
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "query": { "type": "string", "description": "Fritekstsøk mot navn og beskrivelse." }
  },
  "required": ["query"]
}
```

Konkret:

- `properties` får en nøkkel per parameter, oppkalt etter **parameternavnet i Java**
  (Spring Boot-plugin-en kompilerer med `-parameters`, så navnet overlever til bytekoden —
  ellers hadde det blitt `arg0`). Verdien er et delskjema utledet av Java-typen:
  `String → "type":"string"`, `int → "integer"`, `LocalDate → "type":"string"` med
  dato-format, enum → `"enum":[…]`, en record → et nøstet `object` med egne `properties`,
  `List<T> → "type":"array"` med `items`.
- `description` fra `@McpToolParam` legges inn *inne i* delskjemaet for parameteren. Dette
  er ren prompt-engineering mot modellen — det er her du forklarer format og forventning
  (f.eks. «ISO-8601, `YYYY-MM-DD`»), og det er ofte forskjellen på at LLM-en gjetter riktig
  eller feil.
- `required` får parameternavnet lagt til. Vær obs: i Spring AI er parametere
  **obligatoriske som default** (`PROPERTY_REQUIRED_BY_DEFAULT = true` i
  `JsonSchemaGenerator`) — for et valgfritt argument må du eksplisitt skrive
  `@McpToolParam(required = false)`, ellers havner det i `required` selv om
  tjenestelaget tåler `null`. Det er direkte relevant for T-04 (`search_destinations`),
  der alle tre parameterne til `DestinationService.search(...)` er valgfrie.
- Selve verktøyoppføringen ellers (`name`, `description`, `annotations`) er uendret; det er
  bare `inputSchema` som vokser.

Det som *ikke* endrer seg: `"type":"object"`, `$schema`, capability-blokken fra
`initialize` og protokollen for øvrig. Kontrakten utvides, den byttes ikke ut.

Verifiseringssteget fra oppgaven («kjør `tools/list` på nytt etter T-03/T-04») krever at man
kjører `./gradlew bootJar` først — jar-en er et øyeblikksbilde, og et nytt `@McpTool` dukker
ikke opp i `tools/list` før den er bygget på nytt.

### T-01 · Bygg, kjør og inspiser skallet

Ingen kodeendring — dette er en verifikasjons-/verktøyoppgave. Web-UI-et i MCP Inspector er
interaktivt og krever nettleser, så **UI-delen er dokumentert, ikke klikket gjennom**. Alt det
UI-et ville vist er derimot verifisert programmatisk, både med rå stdio og med Inspector sin
egen `--cli`-modus.

#### 1. Bygget er grønt

```
$ ./gradlew clean build
BUILD SUCCESSFUL in 2s
9 actionable tasks: 9 executed
```

18 tester kjørte grønt (`DestinationServiceTest` 5, `PricingServiceTest` 6,
`BookingServiceTest` 6, `VacationBookingMcpApplicationTests` 1) på Java 21 (`21.0.12`).
Artefaktene ligger i `build/libs/`:

| Fil | Størrelse | Hva |
|-----|-----------|-----|
| `vacation-booking-mcp-0.0.1-SNAPSHOT.jar` | ~35 MB | den kjørbare fat-jar-en fra `bootJar` — **denne** skal Inspector peke på |
| `vacation-booking-mcp-0.0.1-SNAPSHOT-plain.jar` | ~27 kB | bare klassene fra `jar`-tasken; **ikke** kjørbar |

#### 2. Det Inspector ville vist — verifisert med rå stdio

Samme håndtrykk som i T-00, men med et `tools/call` på slutten:

```bash
{ … initialize … ; notifications/initialized ; tools/list ;
  printf '%s\n' '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"about_application","arguments":{}}}'; }
  | java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar
```

Stdout ga nøyaktig tre linjer (initialize-svar, `tools/list`, `tools/call`):

```jsonc
{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"about_application","title":"about_application","description":"Forklarer hva denne applikasjonen er og hva den brukes til.","inputSchema":{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{},"required":[]},"annotations":{…}}]}}
{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"Dette er en ferie-booking MCP-server bygget i Spring Boot med Spring AI. …"}],"isError":false}}
```

Stderr inneholdt `RegisteredToolsLogger: Tilgjengelige MCP-tools (1): [about_application]` —
den raskeste sanity-sjekken på at annotasjons-scanneren fant verktøyet.

#### 3. Samme sjekk gjennom Inspector sin CLI-modus (ingen nettleser)

Inspector v2 har en skriptbar `--cli`-modus. Den bruker samme klientkode som web-UI-et, så
dette er en ekte Inspector-verifikasjon:

```bash
npx @modelcontextprotocol/inspector --cli \
  java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar \
  -- --method tools/list

npx @modelcontextprotocol/inspector --cli \
  java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar \
  -- --method tools/call --tool-name about_application --format json
```

Begge svarte som forventet (`tools/list` ga `about_application` med tomt `inputSchema`,
`tools/call` ga `"isError":false` og about-teksten). Serveren identifiserte klienten som
`Implementation[name=inspector-cli, version=2.2.0]` i loggen.

> **Argument-rekkefølgen i `--cli` er stiv:** serverkommandoen kommer **først**, deretter `--`,
> og så Inspector-flaggene. Snur du på det (`--method … -- java -jar …`) svarer den
> «Method is required». Merk også at `npx` spiser det første `--`-et hvis det kommer rett
> etter pakkenavnet.

#### 4. Kommandoen i BACKLOG/README er gyldig

- `npm view @modelcontextprotocol/inspector version` → **2.2.0** (pakkenavnet finnes; bin-en
  heter `mcp-inspector`).
- Kommandoen fra BACKLOG ble startet kortvarig med auto-open avskrudd, og banneret kom opp:

  ```
  MCP Inspector Web is up and running at:
     http://localhost:6274?MCP_INSPECTOR_API_TOKEN=d314994eb1…

     Sandbox (MCP Apps): http://localhost:62345/sandbox

     Auth token: d314994eb1…
  ```

  Den relative jar-stien ble altså akseptert som ad-hoc serverkommando. Prosessen ble
  avsluttet igjen — UI-et selv er ikke gjennomgått.

**To avvik mot v1 som README/BACKLOG-teksten er skrevet for** (ikke rettet her, siden T-01
ikke skal endre workshop-tekstene — meldes videre):

1. **Token-parameteren heter `MCP_INSPECTOR_API_TOKEN` i v2**, ikke `MCP_PROXY_AUTH_TOKEN`
   (som fortsatt er riktig for `npx @modelcontextprotocol/inspector@v1-latest`). Poenget i
   dokumentasjonen — «åpne akkurat den URL-en, ellers får du connection error» — står seg;
   bare navnet på query-parameteren er nytt.
2. **Fallback-oppskriften («Transport Type: STDIO / Command / Arguments»)** er v1-UI-et. I v2
   legger du i stedet til en server via **Add Server**-flyten; feltene *Command* og
   *Arguments* finnes fortsatt der.

I tillegg krever Inspector v2 **Node ≥ 22.19.0** (verifisert her med Node 26.7.0). Deltakere
med eldre Node må enten oppgradere eller kjøre `npx @modelcontextprotocol/inspector@v1-latest`.

#### 5. Hva deltakeren skal se i Inspector

1. Kjør `npx @modelcontextprotocol/inspector java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar`
   **fra prosjektroten**. Terminalen skriver ut URL-en med auth-token.
2. Åpne den tokeniserte URL-en og klikk **Connect**. Først *da* startes `java`-prosessen —
   Inspector eier serverens livssyklus, du skal ikke starte jar-en selv i tillegg.
3. Statusen slår om til **Connected**, og serveren presenterer seg som
   `vacation-booking-mcp 0.0.1` med capabilities `tools`, `resources`, `prompts`, `logging`,
   `completions` (se T-00 for hvorfor de tre siste er tomme i skallet).
4. Under **Tools → List Tools** kommer det **ett** verktøy: `about_application`, med
   beskrivelsen «Forklarer hva denne applikasjonen er og hva den brukes til.» Skjemaet er tomt
   (ingen input-felt), så kall-knappen står alene.
5. Klikk **Run Tool**. Svaret er et `content`-array med én tekstblokk som starter «Dette er en
   ferie-booking MCP-server bygget i Spring Boot med Spring AI…», og `isError: false`.
6. Panelet **Error output from MCP server** viser Spring-oppstartsloggen (banner, Hikari,
   `RegisteredToolsLogger`). Det er **stderr, ikke feil** — det er nettopp dit
   `logback-spring.xml` sender loggen for å holde stdout rent.

#### 6. Vanlige fallgruver

| Symptom | Årsak | Fiks |
|---------|-------|------|
| «Connection error» i nettleseren | Åpnet `http://localhost:6274` uten token-parameteren | Bruk *nøyaktig* URL-en fra terminalen — kopier hele linja, inkludert `?…TOKEN=…` |
| «Connect» feiler med `Unable to access jarfile` | Inspector arver *sitt eget* arbeidskatalog, og den relative stien `build/libs/…` peker feil | Kjør `npx …` fra prosjektroten, eller bruk absolutt sti til jar-en |
| Ingen jar å peke på | Bare `./gradlew test` er kjørt | `./gradlew build` (eller `bootJar`) — jar-en havner i `build/libs/` |
| Serveren starter, men `tools/list` er tom | Pekt på `…-plain.jar` | Bruk fat-jar-en uten `-plain` |
| Nytt verktøy dukker ikke opp | Jar-en er et øyeblikksbilde | Bygg på nytt (`./gradlew bootJar`) og **Reconnect** i Inspector |
| Uforståelig JSON-parsefeil i Inspector | Noe har skrevet til `System.out` | All logging skal gå gjennom loggeren; stdout tilhører JSON-RPC |
| `npx` finner ikke pakken / krasjer ved oppstart | For gammel Node | Inspector v2 krever Node ≥ 22.19.0 |

### T-02 · Koble serveren til Claude

Ingen kodeendring — dette er en oppsett-/verifikasjonsoppgave. Hosten som ble brukt er
**Claude Code**; Claude Desktop er ikke installert på maskinen, så den delen er verifisert som
dokumentasjon (JSON-syntaks og sti-krav), ikke klikket gjennom.

#### 1. Serveren er registrert og tilkoblet

Oppskriften i README ble fulgt som den står — `add-json` fra prosjektroten, med `$(pwd)` som
fyller inn absolutt sti:

```bash
claude mcp add-json vacation-booking \
  "{\"command\":\"java\",\"args\":[\"-jar\",\"$(pwd)/build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar\"]}"
```

Les-verifikasjon etterpå:

```
$ claude mcp list
vacation-booking: java -jar /Users/hjm/dev/mcp-workshop/build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar - ✔ Connected

$ claude mcp get vacation-booking
vacation-booking:
  Scope: Local config (private to you in this project)
  Status: ✔ Connected
```

Det registrerte oppsettet (i `~/.claude.json`, under `projects` → prosjektstien) er nøyaktig
`{"command":"java","args":["-jar","<absolutt sti>/…-SNAPSHOT.jar"]}`, og jar-en på den stien
finnes (~35 MB fat-jar fra `bootJar`). README-teksten «skal vise `Status: ✔ Connected`» stemmer
altså ordrett med det CLI-en skriver.

#### 2. Akseptkriteriet: Claude lister og kaller verktøyet

`about_application` er tilgjengelig for hosten og ble kalt gjennom den — svaret var about-teksten
fra `AboutTool` («Dette er en ferie-booking MCP-server bygget i Spring Boot med Spring AI …»),
ikke en modell-gjetning. **Slik tester deltakeren:** spør hosten *«hva er denne applikasjonen?»*.
Riktig oppførsel er at Claude ber om å få kalle `vacation-booking · about_application` (verktøyet
tar ingen argumenter, så kallet er `arguments: {}`) og svarer på grunnlag av returverdien.
`/mcp` i Claude Code viser samme ting fra listesiden: serveren som *connected* med ett verktøy.

#### 3. Oppskriftene er kontrollert, ikke bare lest

- **`add-json`-JSON-en** ble parset som JSON etter skall-ekspansjon — gyldig. Merk at
  argumentet er *ett* JSON-objekt i doble hermetegn, så `$(pwd)` ekspanderes, mens de indre
  hermetegnene må escapes (`\"`). Skriver du enkle hermetegn rundt i stedet, ekspanderes ikke
  `$(pwd)` og du får en literal `$(pwd)` i stien.
- **Claude Desktop-JSON-en** i README ble også parset — gyldig.
- Serveren havner i **local scope** (privat for deg i dette prosjektet), som README lover.
  `claude mcp remove vacation-booking -s local` fjerner den igjen.

#### 4. To README-presiseringer (gjort her)

1. **Hvor Claude Desktop-konfigurasjonen ligger** manglet. Lagt til: *Settings → Developer →
   Edit Config*, `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) /
   `%APPDATA%\Claude\claude_desktop_config.json` (Windows).
2. **Kravet om absolutt sti** er nå eksplisitt i Desktop-avsnittet: Desktop starter jar-en fra
   *sin egen* arbeidskatalog, ikke prosjektroten, så en relativ `build/libs/…` gir «server
   disconnected» uten tydelig forklaring. (For Claude Code sin `-s project`-variant er relativ
   sti derimot riktig — den skal fungere på andres maskiner.)

#### 5. Fallgruven som faktisk inntraff

**`./gradlew clean` river jar-en bort under en kjørende host.** Hosten starter serverprosessen
når *den* starter og holder den i live; den peker på en konkret fil. Under arbeidet med T-01 ble
`./gradlew clean build` kjørt, og i vinduet der `build/libs/` var tomt mistet Claude Code
tilkoblingen til `vacation-booking`. Dette er *ikke* en feil i konfigurasjonen — fiksen er å
bygge ferdig og deretter koble til på nytt (`/mcp` i Claude Code, eller restart av Claude
Desktop). Lagt inn som tredje kulepunkt i README-seksjonen «Tre ting som ofte forvirrer», ved
siden av de to eksisterende (serveren kobles opp ved oppstart; jar-en er et øyeblikksbilde).

| Symptom | Årsak | Fiks |
|---------|-------|------|
| Serveren forsvinner midt i en økt | `./gradlew clean` slettet jar-en hosten kjører | Bygg på nytt, så `/mcp` → reconnect (Desktop: restart) |
| `✘ Failed to connect` rett etter `add-json` | Jar-en er ikke bygget ennå | `./gradlew bootJar` først |
| Nytt verktøy vises ikke i `/mcp` | Jar-en er et øyeblikksbilde | `./gradlew bootJar` + reconnect |
| Claude Desktop: «server disconnected», ingen detaljer | Relativ sti, eller enkelt backslash på Windows | Absolutt sti, `\\` eller `/` i JSON |
| Verktøyet finnes, men Claude bruker det ikke | Spørsmålet traff ikke beskrivelsen | Spør konkret: «hva er denne applikasjonen?» — eller be eksplisitt om verktøyet |

Loggen for host-startede servere: Claude Desktop skriver serverens stderr til
`~/Library/Logs/Claude/mcp-server-vacation-booking.log`. Uansett host skriver serveren selv
til `logs/vacation-booking-mcp.log` i prosjektroten — den er den mest praktiske under workshopen,
siden den finnes uavhengig av hvem som startet prosessen.

### T-03 · `list_destinations`

Første ekte verktøy etter eksempelet. To nye filer, ingen endringer i eksisterende kode:

| Fil | Hva |
|-----|-----|
| `src/main/java/no/computas/vacationmcp/tools/DestinationTools.java` | `@Component` med `@McpTool(name = "list_destinations")` → `DestinationService.listAvailable()` |
| `src/test/java/no/computas/vacationmcp/tools/DestinationToolsTest.java` | `@SpringBootTest` som verifiserer de 5 seedede reisemålene |

Metoden er tre linjer inkludert `return` — all logikk ligger allerede i tjenestelaget, og
verktøyet gjør ingenting annet enn å delegere. Klassen heter `DestinationTools` (flertall)
fordi T-04 (`search_destinations`) skal inn i **samme** klasse; se «Struktur for
verktøyklasser» over for resten av fordelingen.

#### Designvalg: domene-record som JSON, ikke formatert tekst

Metoden returnerer `List<Destination>` — domene-recorden rett fra tjenesten. Spring AI ser at
returtypen ikke er `String` og serialiserer den til JSON i tekstblokken av `CallToolResult`
(`AbstractMcpToolMethodCallback.convertValueToCallToolResult`: `String` går uendret ut, alt
annet gjennom `jsonHelper.toJson`). Modellen får dermed:

```json
[{"id":1,"name":"Lofoten Rorbuer","country":"Norge","description":"Tradisjonelle rorbuer med utsikt over fjorden.","pricePerNight":1850.0,"available":true}, …]
```

Alternativet — en håndformatert tabell/punktliste i en `String` — ble valgt bort:

- **`id` overlever.** T-05/T-06/T-07 tar alle `destinationId`. I JSON er id-en et felt modellen
  kan lese av og sende videre; i en pen tekstlinje («Lofoten Rorbuer, Norge — 1850 kr/natt»)
  må den enten utelates eller pakkes inn i prosa som modellen må parse tilbake.
- **Feltnavn er selvdokumenterende.** `"pricePerNight":1850.0` er entydig; «1850» i en
  kolonne krever at modellen husker kolonneoverskriften. Det gjør også formatet robust for
  klienter som ikke er en LLM (Inspector viser det som JSON).
- **Ingen kode å vedlikeholde.** Formateringskode er et sted der domenet og presentasjonen kan
  gli fra hverandre. Her finnes ingen mapping — legges det til et felt i `Destination`, er det
  med i svaret.

En egen DTO (f.eks. `DestinationSummary` uten `description`/`available`) ble også vurdert og
valgt bort: `description` er nettopp det som lar modellen svare på «hvor er det fint å gå på
ski?», og `available` er billig og ærlig (feltet er alltid `true` her, siden `listAvailable()`
filtrerer). En DTO ville duplisert domenet uten å gi modellen noe.

> **Knapp for senere:** `@McpTool(generateOutputSchema = true)` gjør at Spring AI også legger
> ved `outputSchema` og fyller `structuredContent` i stedet for tekstblokken. Ikke slått på her
> — fasiten holder seg til tekst-JSON, som er det alle klienter forstår.

#### `annotations`: tool-hint som er verdt å sette

T-00-tracen viste at `about_application` arver Spring AI sine defaults —
`readOnlyHint:false, destructiveHint:true` — altså at eksempel-verktøyet *ser ut som* det kan
ødelegge noe. Her settes hintene eksplisitt (`readOnlyHint = true`, `destructiveHint = false`,
`idempotentHint = true`, `openWorldHint = false`), så en host som har «spør før destruktive
kall» skrudd på kan kjøre dette uten å plage brukeren.

#### Verifisering

**1. `./gradlew build` er grønt** — 20 tester (18 fra før + 2 nye i `DestinationToolsTest`).
Testen kaller beanen direkte, siden MCP-serveren er avskrudd i test
(`spring.ai.mcp.server.enabled=false`); den sjekker at alle 5 seedede reisemål kommer i
id-rekkefølge (repository-et har `ORDER BY id`) og at navn/land/pris stemmer for Lofoten.

**2. Gjennom protokollen** — samme håndtrykk som i T-00, men med `tools/call`:

```bash
… initialize ; notifications/initialized ; tools/list ;
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_destinations","arguments":{}}}
```

Stdout ga nøyaktig tre linjer. `tools/list` inneholder nå **to** verktøy, og oppføringen for
det nye er:

```jsonc
"inputSchema": {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{},"required":[]},
"title": "Tilgjengelige reisemål",
"annotations": {"title":"Tilgjengelige reisemål","readOnlyHint":true,"destructiveHint":false,"idempotentHint":true,"openWorldHint":false}
```

`inputSchema` er altså **tomt** — `properties: {}` og `required: []` — akkurat som
`about_application`, og av samme grunn: metoden er parameterløs (se svar 2 i T-00). Det er
først i T-04 at skjemaet får innhold.

`tools/call` ga `"isError":false` og en tekstblokk med JSON-arrayet over — **5 reisemål**
(Lofoten 1850, Santorini 2400, Kyoto 1600, Toscana 1400, Tromsø 2100), som er akseptkriteriet.
Stderr bekreftet registreringen: `Tilgjengelige MCP-tools (2): [about_application, list_destinations]`.

> Husk `./gradlew bootJar` før røyktesten — jar-en er et øyeblikksbilde, og et nytt `@McpTool`
> dukker ikke opp i `tools/list` før den er bygget på nytt (samme fallgruve som i T-01/T-02).

### T-04 · `search_destinations`

Andre verktøy i samme klasse — ingen nye filer:

| Fil | Endring |
|-----|---------|
| `src/main/java/no/computas/vacationmcp/tools/DestinationTools.java` | nytt `@McpTool(name = "search_destinations")` → `DestinationService.search(query, country, maxPricePerNight)`; `description` på `list_destinations` utvidet med krysshenvisning |
| `src/test/java/no/computas/vacationmcp/tools/DestinationToolsTest.java` | 6 nye tester (fritekst, land, pris, kombinasjon, ingen argumenter, negativ pris) |

Metoden er fortsatt ett `return`-kall. All filtrering ligger i `DestinationRepository.search`
(`LIKE` mot `name`/`description`, eksakt `country`, `price_per_night <= ?`, alltid
`available = 1`, `ORDER BY id`), og valideringen i `DestinationService`.

#### Den faktiske `inputSchema`-en — svaret på T-00 sitt tredje spørsmål

Dette er verifisert med et nytt `tools/list` mot den nybygde jar-en, slik BACKLOG ber om i
«Slik tester du» under [T-00](#t-00--se-mcp-protokollen-før-du-bruker-spring-annotasjoner):

```jsonc
"inputSchema": {
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "query": {
      "type": "string",
      "description": "Fritekst som må forekomme i navnet eller beskrivelsen til reisemålet, f.eks. «nordlys», «rorbu» eller «vingård». Delvis treff holder. Bruk ett stikkord om gangen — hele setninger matcher sjelden. Utelat for å ikke filtrere på tekst."
    },
    "country": {
      "type": "string",
      "description": "Land, skrevet nøyaktig slik det står i dataene (norsk landnavn, f.eks. «Norge», «Hellas», «Japan», «Italia»). Dette er et eksakt treff, ikke et delvis søk — er du usikker på skrivemåten, kall `list_destinations` først. Utelat for å søke i alle land."
    },
    "maxPricePerNight": {
      "type": "number",
      "format": "double",
      "description": "Øvre grense for pris per natt i norske kroner; bare reisemål med pris lik eller lavere kommer med. Må være null eller positiv — et negativt tall avvises som feil. Utelat for å ikke filtrere på pris."
    }
  },
  "required": []
}
```

Sammenlignet med T-03, der skjemaet var `{"properties":{},"required":[]}`, er det nøyaktig det
[T-00 forutsa](#t-00--se-mcp-protokollen-før-du-bruker-spring-annotasjoner) som har skjedd:

- **`properties` har én nøkkel per Java-parameter**, navngitt etter parameternavnet i bytekoden
  (`-parameters`-flagget fra Spring Boot-plugin-en — ellers `arg0`, `arg1`, `arg2`).
- **Typen er utledet av Java-typen:** `String → "type":"string"`, og `Double` blir
  `"type":"number"` **med `"format":"double"`** i tillegg. Formatet er et hint, ikke en
  begrensning — `2000` (heltall) blir godtatt like godt som `2000.0`.
- **`description` fra `@McpToolParam` havner inne i delskjemaet** for hver parameter. Det er
  her, ikke i tool-beskrivelsen, du forteller modellen at `country` er et eksakt treff.
- **`required` er tom** — det er hele poenget med oppgaven. Uten
  `@McpToolParam(required = false)` ville alle tre stått i lista, siden Spring AI har
  `PROPERTY_REQUIRED_BY_DEFAULT = true` (`JsonSchemaGenerator`). Da måtte modellen sendt alle
  tre argumentene ved hvert kall, og et søk «på alt i Norge» ville krevd en oppdiktet verdi
  for `query` og `maxPricePerNight`.
- `"type":"object"`, `$schema`, `name`, `description`, `annotations` og capability-blokken fra
  `initialize` er uendret. Kontrakten *vokser*, den byttes ikke ut.

`Double` og ikke `double`: med primitiv type ville et utelatt argument blitt `0.0`, som er en
gyldig verdi tjenesten ville filtrert på (og alltid gitt tomt resultat). Boksede typer er det
eneste som lar «ikke oppgitt» overleve helt fram til `DestinationService`. Samme regel gjelder
`Integer`/`Long` i senere oppgaver.

#### Feilhåndtering: exception-en får boble (mønsteret T-05–T-12 skal arve)

Første gang en `ValidationException` kan nå ut til klienten. **Valget er å ikke gjøre noe** —
ingen `try/catch`, ingen egen `CallToolResult`-bygging i verktøykoden. Begrunnelsen er hva
Spring AI 2.0 allerede gjør i `SyncMcpToolMethodCallback.apply(...)`:

```java
catch (Exception e) {
    if (this.toolCallExceptionClass.isInstance(e)) {   // default: Exception.class
        return this.createSyncErrorResult(e);          // isError(true) + e.getMessage() + rot-årsakens melding
    }
    throw e;
}
```

Verifisert gjennom protokollen (`tools/call` med `{"maxPricePerNight":-100}`):

```jsonc
{"jsonrpc":"2.0","id":5,"result":{"content":[{"type":"text","text":"Error invoking method: searchDestinations\nmaxPricePerNight kan ikke være negativ"}],"isError":true}}
```

Tre ting å merke seg:

1. **Det er et `result`, ikke et JSON-RPC `error`.** MCP skiller bevisst: protokollfeil (ukjent
   metode, ugyldige `params`) blir `error`-objekter som klientkoden håndterer, mens *verktøyet
   som feiler* er et normalt resultat med `isError: true` — nettopp fordi modellen skal få se
   feilen og kunne prøve på nytt med bedre argumenter.
2. **Ingen stacktrace.** `createSyncErrorResult` bruker bare `getMessage()` på exception-en og
   på rot-årsaken. Stacktracen finnes ikke i svaret i det hele tatt; den ligger i
   `logs/vacation-booking-mcp.log` for utvikleren.
3. **Første linje er Spring AI sin egen innpakning.** `callMethod` pakker
   `InvocationTargetException` i `RuntimeException("Error invoking method: searchDestinations", cause)`,
   så teksten blir to linjer: innpakningens melding, og vår egen melding fra rot-årsaken.
   Java-metodenavnet lekker altså ut (camelCase, ikke tool-navnet). Det er kosmetisk støy —
   linje to er den som betyr noe, og den er ordrett meldingen fra `DestinationService`.

Alternativet som ble vurdert og valgt bort: la verktøymetoden returnere `CallToolResult` og
selv fange `ValidationException` (`convertValueToCallToolResult` slipper en `CallToolResult`
rett gjennom, så det *virker*). Det ville fjernet «Error invoking method:»-linja, men til en
høy pris: hvert av verktøyene i T-05–T-12 måtte fått en `try/catch`, og returtypen ville
sluttet å være domene-recorden — som er selve konvensjonen fra T-03. Meldingen er allerede
lesbar; prisen er for høy for en kosmetisk gevinst. **Konklusjon: kast videre, ikke fang.**

> **Bonus:** argumenter som bryter selve skjemaet stoppes *før* metoden kalles, av serverens
> egen JSON Schema-validering. `{"maxPricePerNight":"gratis"}` gir
> `"Tool (search_destinations) input validation failed: … [/maxPricePerNight: string funnet, number forventet]"`
> med `isError: true` — merk at meldingen er lokalisert etter JVM-ens locale. Det er derfor
> `required = false` er nok for de valgfrie feltene: valideringen håndhever skjemaet, og
> tjenestelaget håndhever reglene skjemaet ikke kan uttrykke (som «ikke negativ»).

#### Verifisering

**1. `./gradlew build` er grønt** — 26 tester (20 fra før + 6 nye). De nye dekker fritekst mot
både navn og beskrivelse (`rorbu` → Lofoten, `nordlys` → Tromsø), landsfilter (`Norge` → to
treff), pristak (inklusiv grense: 1850 tar med Lofoten), kombinasjonen av alle tre (`rorbu` +
`Norge` + 2000 → Lofoten; samme med tak 1000 → tomt), at tre `null` gir samme svar som
`list_destinations`, og at negativ pris kaster `ValidationException` med meldingen fra
tjenesten.

**2. Gjennom protokollen** — samme håndtrykk som i T-00, deretter `tools/list` og fire
`tools/call`. `tools/list` viser nå **tre** verktøy
(`Tilgjengelige MCP-tools (3): [about_application, list_destinations, search_destinations]` i
stderr), og kallene ga:

| Argumenter | Resultat |
|------------|----------|
| `{"query":"nordlys"}` | `isError:false`, 1 treff: Tromsø Nordlys-lodge (fritekst mot *beskrivelsen*) |
| `{"country":"Norge","maxPricePerNight":2000}` | `isError:false`, 1 treff: Lofoten (Tromsø faller ut på pris) |
| `{"maxPricePerNight":-100}` | `isError:true` med meldingen over |
| `{}` | `isError:false`, alle 5 — tomt argument-objekt er lovlig når `required` er tom |

**3. Småting som ble bekreftet på veien:** `LIKE` i SQLite er case-insensitiv for ASCII, så
`"NORDLYS"` gir samme treff som `"nordlys"` (men *ikke* for `æ/ø/å` — der er `LIKE`
case-sensitiv med mindre ICU er kompilert inn). Verktøybeskrivelsen lover derfor ikke noe om
store/små bokstaver.

### T-05 · `check_availability`

Første verktøy i Epic 2, og første gang datoer krysser MCP-grensen. To nye filer:

| Fil | Hva |
|-----|-----|
| `src/main/java/no/computas/vacationmcp/tools/AvailabilityTools.java` | `@Component` med `@McpTool(name = "check_availability")` → `AvailabilityRepository.findOverlapping(...)`, pluss den nøstede recorden `AvailabilityResult` |
| `src/test/java/no/computas/vacationmcp/tools/AvailabilityToolsTest.java` | `@SpringBootTest` med 9 tester mot de seedede periodene |

Unntaket fra «deleger til `service/`»: det finnes **ingen** `AvailabilityService`. Verktøyet
går derfor rett på repository-et, som er et rent dataaksesslag uten validering — og det er
grunnen til at akkurat dette verktøyet har litt validering selv (se under).

#### Datovalget: `LocalDate`, ikke `String` + parsing

Dette er hovedspørsmålet i oppgaven, og det ble avgjort empirisk: en probe-versjon med
`LocalDate`-parametere ble bygget, kjørt og målt gjennom protokollen **før** koden ble
skrevet ferdig. Den faktiske `inputSchema`-en for de to datofeltene:

```jsonc
"from": {
  "type": "string",
  "format": "date",
  "description": "Ønsket startdato (innsjekk) på ISO-8601-formatet yyyy-MM-dd, f.eks. «2026-07-01». Må være før til-datoen. Andre skrivemåter, som «01.07.2026» eller «i morgen», avvises — regn om til en konkret dato først."
},
"to": {
  "type": "string",
  "format": "date",
  "description": "Ønsket sluttdato (utsjekk) på ISO-8601-formatet yyyy-MM-dd, f.eks. «2026-07-10». Må være etter fra-datoen; datoen regnes som utsjekksdag, så et opphold fra 1. til 10. er ni netter."
}
```

Og `destinationId` (primitiv `long`, obligatorisk) ble
`{"type":"integer","format":"int64"}` med alle tre navnene i `required`.

**Det T-00 gjettet stemmer:** `LocalDate → "type":"string"` med dato-format. Konkret er det
`"format":"date"` — JSON Schema sitt standardnavn for en ISO-8601-kalenderdato. Skjemaet er
altså verken dårlig eller misvisende; det er det beste vi kunne skrevet for hånd. Med
`String`-parametere hadde vi fått et bart `{"type":"string"}` og mistet `format`-hintet,
altså gitt modellen *mindre* å gå på. Det avgjorde valget.

**Deserialiseringen virker gjennom protokollen** — verifisert, ikke antatt:
`{"from":"2026-07-01"}` kom fram som `LocalDate.of(2026, 7, 1)` uten noe oppsett. Jackson sin
`JavaTimeModule` ligger på klassestien via Spring Boot, og Spring AI sin argument-konvertering
bruker den.

Fire sonderinger som kartla hvor formatvalideringen faktisk skjer:

| Argument for `from` | Svar | Hvem stoppet det |
|---------------------|------|------------------|
| `"2026-07-01"` | `isError:false`, riktig periode | — (gyldig) |
| `20260701` (tall) | `isError:true`, `Tool (check_availability) input validation failed: … [/from: integer funnet, string forventet]` | **skjemavalideringen**, før metoden |
| `"01.07.2026"` | `isError:true`, `Conversion from JSON to java.time.LocalDate failed` / `Text '01.07.2026' could not be parsed at index 0` | **deserialiseringen**, før metoden |
| `"2026-13-45"` | `isError:true`, `… failed` / `Invalid value for MonthOfYear (valid values 1 - 12): 13` | **deserialiseringen**, før metoden |

Merk at `"format":"date"` **ikke** håndheves av skjemavalideringen — «01.07.2026» er en gyldig
`string` og slipper forbi validatoren; det er Jackson som stopper den. JSON Schema behandler
`format` som en annotasjon, ikke en begrensning. Praktisk betydning: `format`-hintet er til for
*modellen*, mens den faktiske håndhevingen ligger et lag lenger inn.

**Prisen for valget** er at feilmeldingen ved ugyldig format ikke kan styres — vi får
Spring AI/Jackson sin tekst, med `java.time.LocalDate` synlig i den. Alternativet `String` +
egen `LocalDate.parse` i en `try/catch` ville gitt en penere melding
(«Ugyldig dato … bruk yyyy-MM-dd»), men til en pris som ble vurdert for høy:

- Skjemaet mister `"format":"date"` — det modellen faktisk styres av.
- Hver av T-06/T-07/T-12 måtte fått samme `try/catch` + konvertering før kallet til
  `PricingService`/`BookingService`, som uansett tar `LocalDate`. Ren duplisert kjeleplate.
- Det bryter med T-04-konklusjonen «kast videre, ikke fang» — vi hadde innført nettopp den
  `try/catch`-en i `tools/`-laget som ble valgt bort der.

Meldingen vi *får* er dessuten handlingsbar: den sier at strengen ikke lot seg tolke som en
dato, og for feil måned til og med hvorfor. Kombinert med `format: "date"` og en
`@McpToolParam(description = …)` som staver ut `yyyy-MM-dd`, har modellen alt den trenger for
å prøve på nytt. **Konklusjon: `LocalDate`, og kompenser i beskrivelsen.** Dette er nå den
felles beslutningen T-06/T-07/T-12 skal følge (se [seksjonen over](#datoer-over-mcp-grensen-avgjort-i-t-05--følg-denne)).

#### Valideringen som ble igjen i verktøylaget

`AvailabilityRepository` validerer ingenting — den er ren SQL. Sjekket etter, og det stemmer:
`findOverlapping` bygger bare en `WHERE start_date < ? AND end_date > ?`. Med `to` før `from`
ville spørringen gitt et vilkårlig (som regel tomt) resultat uten å si fra.

Derfor gjør verktøyet to sjekker selv — det eneste stedet i løsningen der det er riktig:

```java
if (from == null || to == null) throw new ValidationException("fra- og til-dato må oppgis");
if (!from.isBefore(to))        throw new ValidationException("fra-dato må være før til-dato");
```

Begge meldingene er **ordrett kopiert fra `PricingService.quote(...)`**, ikke nyskrevet. Poenget
er at modellen skal møte samme feilspråk enten den bommet på datoene i `check_availability`
eller i `get_quote`. ISO-formatdelen av akseptkriteriet er dekket av deserialiseringen (tabellen
over), så den trengte ingen kode. `ValidationException` er samme klasse som tjenestelaget bruker,
og den får boble ut etter T-04-mønsteret — ingen `try/catch`.

#### Svaret: en konvolutt rundt lista, ikke en bar `[]`

Returtypen er ikke `List<Availability>`, men en nøstet record:

```java
public record AvailabilityResult(
        long destinationId, LocalDate from, LocalDate to,
        int matchingPeriods, List<Availability> periods) {}
```

Dette er et bevisst avvik fra T-03-konvensjonen «returtype = domene-record eller `List<…>` av
dem», og grunnen er nettopp det oppgaven peker på: **et bart `[]` er tvetydig for en modell.**
Den ser ikke forskjell på «reisemålet er ikke åpent i disse datoene», «jeg spurte om feil
periode» og «noe gikk galt» — og en LLM som er i tvil har lett for å melde tilbake at verktøyet
feilet. Konvolutten gjentar spørringen og teller treffene, så `matchingPeriods: 0` leses som et
svar, ikke som en feil:

```json
{"destinationId":1,"from":"2026-12-01","to":"2026-12-10","matchingPeriods":0,"periods":[]}
```

Avviket er *innpakning*, ikke mapping: `periods` inneholder domene-recorden `Availability`
uendret, med `id`, `capacity` og `seasonPrice` intakt. Ingen formatering, ingen regler, ingen
DTO-duplisering av domenet — konvensjonen fra T-03 gjelder fortsatt for innholdet.

En ukjent `destinationId` gir også tom liste (verifisert med id 999). Å slå opp reisemålet og
kaste `NotFoundException` ble vurdert, men valgt bort: det ville krevd `DestinationRepository`
inn i en klasse som ellers har én avhengighet, og T-05 sitt akseptkriterium nevner bare
datovalidering. Begrensningen er i stedet skrevet eksplisitt inn i verktøybeskrivelsen, slik at
modellen vet at den bør sjekke id-en mot reisemåls-verktøyene når svaret er tomt.

`description` sier også to ting til som ellers ville blitt gjettet feil: at en treffende periode
bare betyr **overlapp** (ikke at hele oppholdet er dekket, eller at det er plass igjen — det
avgjør `get_quote`/`create_booking`), og at `seasonPrice: null` betyr at reisemålets ordinære
pris per natt gjelder.

#### Verifisering

**1. `./gradlew build` er grønt** — 35 tester (26 fra før + 9 nye). De nye dekker overlappende
periode med kapasitet og sesongpris, at konvolutten gjentar spørringen, en spørring som spenner
over *to* perioder (2026-08-15→2026-09-15 treffer begge Lofoten-periodene, sortert på startdato,
og den andre har `seasonPrice = null`), ingen overlapp (tom liste), ukjent destinasjon, `from`
etter `to`, `from == to`, manglende datoer, og at ikke-ISO-strenger aldri blir en `LocalDate`.

**2. Gjennom protokollen** — samme håndtrykk som i T-00, deretter `tools/list` og fem
`tools/call` mot den nybygde jar-en. Stderr bekreftet registreringen:
`Tilgjengelige MCP-tools (4): [about_application, check_availability, list_destinations, search_destinations]`.

| Argumenter | Resultat |
|------------|----------|
| `{"destinationId":1,"from":"2026-08-15","to":"2026-09-15"}` | `isError:false`, `matchingPeriods:2` — begge Lofoten-periodene, den ene med `seasonPrice:2200.0`, den andre `null` |
| `{"destinationId":1,"from":"2026-12-01","to":"2026-12-10"}` | `isError:false`, `matchingPeriods:0`, `periods:[]` — tom liste er et gyldig svar |
| `{"destinationId":5,"from":"2026-11-20","to":"2026-11-27"}` | `isError:false`, 1 treff: Tromsø 2026-11-01→2027-02-28, kapasitet 4, sesongpris 2600 |
| `{"destinationId":1,"from":"2026-07-10","to":"2026-07-01"}` | `isError:true`: `Error invoking method: checkAvailability` / `fra-dato må være før til-dato` |
| `{"destinationId":1,"from":"i morgen","to":"2026-07-10"}` | `isError:true`: `Conversion from JSON to java.time.LocalDate failed` / `Text 'i morgen' could not be parsed at index 0` |

Treffet på 2026-08-15→2026-09-15 er verdt å merke seg: `findOverlapping` bruker `start_date < to
AND end_date > from`, altså **halvåpent** intervall, så en periode som bare delvis dekker
spørringen kommer med. Det er riktig oppførsel for «hva er ledig rundt disse datoene?», men
understreker hvorfor beskrivelsen må si at overlapp ikke er det samme som at oppholdet kan bookes.

### T-06 · `get_quote`

Det tynneste verktøyet så langt, og det første med et **ikke-tomt `required`**. To nye filer:

| Fil | Hva |
|-----|-----|
| `src/main/java/no/computas/vacationmcp/tools/PricingTools.java` | `@Component` med `@McpTool(name = "get_quote")` → `PricingService.quote(...)` |
| `src/test/java/no/computas/vacationmcp/tools/PricingToolsTest.java` | `@SpringBootTest` med 13 tester, alle tall regnet for hånd mot `data.sql` |

Metodekroppen er **én linje**: `return pricing.quote(destinationId, from, to, numTravelers);`.
Tjenesten gjør både valideringen (datoer, `numTravelers ≥ 1`, at reisemålet finnes og er
tilgjengelig, at en periode dekker oppholdet) og utregningen. Til forskjell fra T-05, der det
ikke fantes noen tjeneste å delegere til, er det her ingenting igjen til verktøylaget — og det
er hele poenget med oppgaven.

#### `Quote` returneres uendret — den har allerede hele regnestykket

Oppgaveteksten sier «verktøyet ditt mapper bare `Quote` til et svar». Svaret her er at det
ikke skal mappes i det hele tatt. Sjekklista fra oppgaven mot feltene i
[`Quote`](src/main/java/no/computas/vacationmcp/service/Quote.java):

| Modellen trenger | Feltet |
|------------------|--------|
| antall netter | `nights` |
| prisen som ble brukt | `pricePerNight` |
| var det sesongpris? | `pricePerNight` ≠ `destination.pricePerNight` ⇒ ja |
| antall reisende | `numTravelers` |
| totalpris | `totalPrice` |
| hvilket opphold dette gjelder | `destination` (hele recorden), `from`, `to` |

Sesongpris-spørsmålet er det eneste som ikke står som et eget felt, og det trengs ikke:
`Quote` bærer med seg **hele** `Destination`-recorden, så den ordinære prisen per natt ligger
ved siden av den som faktisk ble brukt. Er de ulike, gjaldt sesongpris. En egen konvolutt
(à la `AvailabilityResult` i T-05) ville altså bare duplisert informasjon som allerede er der —
og T-05-konvolutten fantes av en helt annen grunn: å gjøre en *tom liste* lesbar. Her finnes
ingen tom-tilstand; enten prises oppholdet, eller så kastes en exception. Dermed gjelder
T-03-konvensjonen rått: domene-record ut, ingen mapping-kode å vedlikeholde.

#### `int`, ikke `Integer` — og den faktiske `inputSchema`-en

`numTravelers` er obligatorisk, så det finnes ingen «ikke oppgitt» som må overleve som `null`.
Da er primitiv `int` riktig — motsatt `Double maxPricePerNight` i T-04, der boksing var
nødvendig nettopp fordi parameteren var valgfri. Verifisert gjennom protokollen mot den
nybygde jar-en:

```jsonc
"inputSchema": {
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "destinationId":  {"type": "integer", "format": "int64",  "description": "Id-en til reisemålet, slik den kommer fra `list_destinations` … En ukjent id gir en feil, ikke et tomt svar."},
    "from":           {"type": "string",  "format": "date",   "description": "Innsjekksdato på ISO-8601-formatet yyyy-MM-dd …"},
    "to":             {"type": "string",  "format": "date",   "description": "Utsjekksdato på ISO-8601-formatet yyyy-MM-dd … et opphold fra 1. til 10. er ni netter."},
    "numTravelers":   {"type": "integer", "format": "int32",  "description": "Antall reisende, minst 1 … 0 eller et negativt tall avvises som feil."}
  },
  "required": ["destinationId", "from", "to", "numTravelers"]
}
```

**`required` er endelig fylt ut** — kontrasten til T-04, der lista var tom med vilje. Merk
formatene: `long → int64`, `int → int32`, `LocalDate → "string"/"date"` (T-05-beslutningen).

Og til forskjell fra `format`, som bare er en annotasjon, **håndheves `required` faktisk** av
serverens skjemavalidering, før metoden kalles. Et kall uten `numTravelers` ga:

```jsonc
{"content":[{"type":"text","text":"Tool (get_quote) input validation failed: Validation failed: JSON schema validation errors: [: påkrevd egenskap 'numTravelers' ikke funnet]"}],"isError":true}
```

Derfor er null-sjekken på datoene som T-05 måtte ha, unødvendig her — men den ligger uansett i
`PricingService`, så meldingen er dekket dobbelt.

#### Verifisering

**1. `./gradlew build` er grønt** — 48 tester (35 fra før + 13 nye). De nye dekker sesongpris,
normalpris-fallback, at leddene henger sammen (`pricePerNight × nights × numTravelers ==
totalPrice`), nedre grense `numTravelers = 1`, `0` og negativt antall, datoer utenfor enhver
periode, et opphold som spenner over to *tilstøtende* perioder, delvis dekket opphold,
`from` etter `to`, manglende datoer, ukjent reisemål, og to andre seedede reisemål.

**2. Gjennom protokollen** — håndtrykk som i T-00, deretter `tools/list` og seks `tools/call`.
Stderr bekreftet registreringen:
`Tilgjengelige MCP-tools (5): [about_application, check_availability, list_destinations, search_destinations, get_quote]`.

| Argumenter | Resultat |
|------------|----------|
| `{"destinationId":1,"from":"2026-07-01","to":"2026-07-10","numTravelers":2}` | `isError:false`, `totalPrice: 39600.0` |
| `{"destinationId":1,"from":"2026-09-05","to":"2026-09-10","numTravelers":2}` | `isError:false`, `totalPrice: 18500.0` (normalpris) |
| `…,"numTravelers":0` | `isError:true`: `Error invoking method: getQuote` / `antall reisende må være minst 1` |
| `{"destinationId":1,"from":"2026-12-01","to":"2026-12-10","numTravelers":2}` | `isError:true`: `Ingen tilgjengelig periode dekker 2026-12-01 til 2026-12-10` |
| `{"destinationId":999,…}` | `isError:true`: `Fant ingen destinasjon med id 999` (`NotFoundException`) |
| `numTravelers` utelatt | `isError:true`: skjemavalideringen, se JSON-en over |

**Regnestykket, fra det faktiske `tools/call`-svaret** (id 3 over):

```json
{"destination":{"id":1,"name":"Lofoten Rorbuer","country":"Norge","description":"Tradisjonelle rorbuer med utsikt over fjorden.","pricePerNight":1850.0,"available":true},
 "from":"2026-07-01","to":"2026-07-10","nights":9,"numTravelers":2,"pricePerNight":2200.0,"totalPrice":39600.0}
```

Kontrollregning mot `data.sql`: Lofoten koster ordinært **1850**/natt, men perioden
2026-07-01→2026-08-31 (`availability` id 1) har `season_price = 2200.0`, så sesongprisen
gjelder. 2026-07-01→2026-07-10 er **9 netter** (utsjekksdagen faktureres ikke), og med
**2 reisende**: `2200 × 9 × 2 = 39 600`. Formelen fra akseptkriteriet stemmer, og modellen kan
lese den av direkte: `pricePerNight: 2200.0` ved siden av `destination.pricePerNight: 1850.0`
forteller at det *var* sesongpris, uten et eneste ekstra verktøykall.

Det andre kallet er kontrollen på fallback-grenen: høstperioden (id 2) har
`season_price = NULL`, så `1850 × 5 × 2 = 18 500` — og der er `pricePerNight` lik
`destination.pricePerNight`.

#### Fellen verktøybeskrivelsen måtte advare mot

`findCovering` krever at **én** periode dekker hele oppholdet (`start_date <= from AND
end_date >= to`), mens `check_availability` bruker `findOverlapping`. Et opphold
2026-08-15→2026-09-15 gir derfor **to treff** i `check_availability`, men avvises av
`get_quote` — periodene 1 og 2 er tilstøtende, ikke slått sammen. Det er lett for en modell å
lese «to ledige perioder» som «kan bookes», så `description` sier det eksplisitt, og en egen
test (`rejectsAStayThatSpansTwoAdjacentPeriods`) låser oppførselen.
