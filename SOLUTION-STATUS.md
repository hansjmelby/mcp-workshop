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
| T-02 | Koble serveren til Claude | ⬜ | — |
| T-03 | `list_destinations` | ⬜ | — |
| T-04 | `search_destinations` | ⬜ | — |
| T-05 | `check_availability` | ⬜ | — |
| T-06 | `get_quote` | ⬜ | — |
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
- **Feilhåndtering:** `ValidationException`/`NotFoundException` fra tjenestelaget formidles
  som MCP tool-feil (`isError`), ikke som stacktrace.
- Hver oppgave = én commit med meldingsprefiks `T-xx: …`.

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
