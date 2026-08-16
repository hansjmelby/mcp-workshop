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
| T-07 | `create_booking` | ✅ | `tools/BookingTools.java` → verktøyet `create_booking` (første **skrivende** verktøy — egne `annotations`; fem obligatoriske parametere; `Booking` returneres uendret); test `tools/BookingToolsTest.java` med 10 tester ([se under](#t-07--create_booking)) |
| T-08 | `get_booking` | ✅ | `get_booking` lagt til i `tools/BookingTools.java` (første **lesende** verktøy i en blandet klasse — egne hint per metode; én obligatorisk `long id`; `Booking` returneres uendret); 4 nye tester i `tools/BookingToolsTest.java` ([se under](#t-08--get_booking)) |
| T-09 | `update_booking_status` | ✅ | `update_booking_status` lagt til i `tools/BookingTools.java` (første **enum** over MCP-grensen — `BookingStatus` gir `"enum":[…]` i skjemaet, og ugyldige verdier stoppes av skjemavalideringen; første verktøy med `destructiveHint = true` + `idempotentHint = true`); 7 nye tester i `tools/BookingToolsTest.java` ([se under](#t-09--update_booking_status)) |
| T-10 | `list_bookings` | ✅ | `list_bookings` lagt til i `tools/BookingTools.java` (første **valgfrie enum** — `"enum":[…]` i skjemaet *og* tomt `required`; bar `List<Booking>` som svar, ingen konvolutt; krysshenvisning begge veier mot `get_booking`); 7 nye tester i `tools/BookingToolsTest.java` ([se under](#t-10--list_bookings)) |
| T-11 | Avvis overbooking | ✅ | Verifikasjonsoppgave — ingen ny forretningslogikk. Kapasitetsregnestykket kartlagt og verifisert gjennom protokollen (fyll opp → én over → kansellering frigjør); `description` på `create_booking` utvidet med hva modellen skal gjøre med «N ledige plasser», og `check_availability` presisert (`capacity` er total, ikke ledig); 7 nye tester i `tools/BookingToolsTest.java` ([se under](#t-11--avvis-overbooking)) |
| T-12 | `cancel_booking` | ✅ | `cancel_booking` lagt til i `tools/BookingTools.java` — **funksjonelt identisk** med `update_booking_status(id, CANCELLED)` (`BookingService.cancel` *er* `updateStatus(id, CANCELLED)`); eget verktøy likevel, av hensyn til katalogen: ett argument i stedet for to, og en `destructiveHint = true`-oppføring hosten kan gate på ved navn. Kapasitetsfrigjøringen verifisert gjennom protokollen (fyll opp → avvist → `cancel_booking` → **samme** booking går gjennom); 7 nye tester i `tools/BookingToolsTest.java` ([se under](#t-12--cancel_booking)) |
| T-13 | Destinasjoner som Resource | ✅ | Ny pakke `resources/` → `resources/DestinationResources.java` med **to** `@McpResource`: den statiske `destination://catalog` (havner i `resources/list`) og malen `destination://{id}` (havner i `resources/templates/list`). Første primitiv som ikke er et verktøy: innholdet er **`text/markdown`**, ikke JSON, fordi det legges rått i konteksten; felles ressurs-beslutninger etablert for T-14; test `resources/DestinationResourcesTest.java` med 7 tester ([se under](#t-13--destinasjoner-som-resource)) |
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
  `BookingTools` (T-07 ✅, T-08 ✅, T-09 ✅, T-10 ✅, T-11 ✅ — ingen nytt verktøy, bare
  `description`, T-12 ✅). Fordelingen holdt hele veien: `BookingTools` fikk fem verktøy og
  beholdt sin ene konstruktør-avhengighet. `AboutTool` (entall) står igjen som
  eksempel-klassen fra skallet.
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
  Skrivende verktøy (T-07 ✅, T-09 ✅, T-12 ✅) skal *ikke* arve dette — sett `readOnlyHint = false` og
  vurder `destructiveHint`/`idempotentHint` selv; se
  [T-07-seksjonen](#t-07--create_booking) for den første faktiske hint-blokken og
  begrunnelsen hint for hint. Merk at `destructiveHint` og `idempotentHint` bare er
  meningsbærende når `readOnlyHint == false`; vi setter dem likevel eksplisitt for å unngå at
  Spring AI sin default (`destructiveHint = true`) står igjen i katalogen.
  **Hintene hører til metoden, ikke klassen** (vist i T-08): `BookingTools` inneholder både det
  skrivende `create_booking` og det lesende `get_booking`, og hver `@McpTool` har sin egen
  `annotations`-blokk. I `tools/list` er hvert verktøy uansett sin egen oppføring — «skrivende
  klasse» er ikke et begrep hosten kjenner. Vurder derfor alle fire hintene på nytt for hver
  metode, og la aldri et lesende verktøy arve nabometodens `readOnlyHint = false`.
- **`description` er prompt-engineering**: si *hva* verktøyet gir, *når* modellen skal velge
  det framfor naboverktøyet, hvilke felt som kommer tilbake, og hvilke forbehold som gjelder.
  Legg til krysshenvisning begge veier når nabo-verktøyet finnes (gjort i T-04:
  `list_destinations` og `search_destinations` peker på hverandre).
- **Valgfrie parametere må merkes eksplisitt** med `@McpToolParam(required = false, …)`, og
  Java-typen må være bokset (`Double`/`Integer`/`Long`) der `null` skal bety «ikke oppgitt».
  Se T-04 for den faktiske `inputSchema`-en dette gir. Referansetyper (`String`, `LocalDate`,
  enum-er) er allerede «boksede» og trenger ingenting utover `required = false` — se
  [T-10](#t-10--list_bookings) for kombinasjonen valgfri + enum.

### Datoer over MCP-grensen (avgjort i T-05 — følg denne)

**Bruk `java.time.LocalDate` som parametertype, ikke `String` med egen parsing.** Gjelder alle
verktøy som tar imot datoer: T-05 ✅, T-06 ✅ og T-07 ✅ (`create_booking`). T-12
(`cancel_booking`) landet uten datoer i det hele tatt — den identifiserer bookingen med `id`
alene — så beslutningen berører ikke det verktøyet.

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

### Enum-er over MCP-grensen (avgjort i T-09 — følg denne)

**Bruk enum-typen (`BookingStatus`) som parametertype, ikke `String`.** Gjelder T-09 ✅ og
T-10 ✅ (`list_bookings(status?)`), som tar samme enum — der *valgfritt*, se
[T-10-seksjonen](#t-10--list_bookings) for hvordan `enum` og et tomt `required` ser ut sammen.

Verifisert empirisk mot den ekte JSON-en (se [T-09-seksjonen](#t-09--update_booking_status)):
Spring AI legger **konstantnavnene inn i skjemaet** som en `enum`-liste, og — til forskjell fra
`format: "date"` — er dette en begrensning JSON Schema faktisk håndhever:

```jsonc
"status": {"type":"string","enum":["PENDING","CONFIRMED","PAID","COMPLETED","CANCELLED"],"description":"…"}
```

- **Modellen får de gyldige verdiene maskinlesbart.** Med `String` hadde skjemaet vært et bart
  `{"type":"string"}`, og verdilista måtte stått i prosa i `description` — samme tap som
  `String`-datoer ville gitt i T-05, bare tydeligere.
- **Ugyldige verdier stoppes av skjemavalideringen, før metoden.** `"BANANA"` — og `"confirmed"`
  med små bokstaver, for lista er case-sensitiv — gir
  `input validation failed: … [/status: har ikke en verdi i oppregningen ["PENDING", …]]`.
  Dette er *tidligere* enn datoene stoppes: `format: "date"` er bare en annotasjon, så
  «01.07.2026» slipper forbi validatoren og stoppes av Jackson. En ugyldig enum-verdi når aldri
  deserialiseringen.
- **Feilmeldingen ramser opp de lovlige verdiene**, så en modell som bommer kan rette seg selv
  uten et nytt `tools/list`.
- **Skjemaet kan ikke uttrykke tilstandsmaskinen.** `enum` sier hvilke verdier som finnes, ikke
  hvilke overganger som er lov fra der bookingen står nå. Den regelen ligger i
  `BookingStatus.canTransitionTo(...)` og håndheves av `BookingService.updateStatus(...)` —
  kompenser i `description`, ikke med ny logikk i verktøyet.

### Ressurser over MCP-grensen (avgjort i T-13 — følg denne)

Gjelder T-13 ✅ og **T-14** (`booking://{id}`), som skal se lik ut. Alt er verifisert mot den
ekte JSON-en; tracen ligger i [T-13-seksjonen](#t-13--destinasjoner-som-resource).

- **Egen pakke `no.computas.vacationmcp.resources`**, klassenavn med suffikset `Resources`
  (`DestinationResources` T-13 ✅, `BookingResources` T-14). Ressurser og verktøy blandes ikke i
  samme klasse — de har ulik bruker (modellen vs. applikasjonen/mennesket), ulikt svarformat og
  ulik feilkanal.
- **Innholdet er lesbar tekst, `mimeType = "text/markdown"` — ikke JSON.** Dette er det motsatte
  valget av T-03 (verktøy returnerer domene-records som JSON), og det er bevisst: et
  verktøysvar er et mellomresultat modellen leser og plukker felt fra midt i en verktøykjede,
  mens ressursinnholdet legges **rått inn i konteksten** av hosten, ofte før modellen har sagt
  noe. Begrunnelsen i sin helhet står i T-13-seksjonen.
- **`id` skal alltid stå i teksten** (`**Lofoten Rorbuer** (id 1)`), for det er broa fra ressurs
  til verktøy: uten id-en kan ikke modellen gå videre til `get_quote`/`create_booking`.
- **Returtypen må være `String`** (eller `List<String>`/`ResourceContents`/`ReadResourceResult`).
  Spring AI serialiserer *ikke* en record for deg her, slik den gjør for `@McpTool` —
  `SyncMcpResourceMethodCallback.validateReturnType` kaster ved oppstart hvis du prøver.
  Formateringen er altså din, og hører hjemme i ressursklassen.
- **URI-variabler kommer alltid som `String`**, og det finnes ikke noe `inputSchema` som kan
  validere dem. Konverter og valider selv i metoden.
- **Statisk ressurs vs. mal avgjøres utelukkende av URI-en.** Inneholder `uri` en `{variabel}`,
  havner oppføringen i `resources/templates/list`; ellers i `resources/list`. Én
  `@McpResource`-annotasjon, to helt ulike protokollister.
- **Feil får boble, som i `tools/`-laget — men utfallet er et annet.** `resources/read` har ingen
  `isError`-kanal: klienten får en ekte JSON-RPC-`error` (`-32602`) med meldingen vår i `data`.
  Skriv derfor meldinger som hjelper *applikasjonen og mennesket*, ikke bare modellen. Å kaste
  `McpError.RESOURCE_NOT_FOUND` selv for å få spesifikasjonens `-32002` **virker ikke** i Spring
  AI 2.0.0 — se T-13 for hvorfor.
- **`title` og `annotations` på `@McpResource` er dødt i Spring AI 2.0.0** — `SyncMcpResourceProvider`
  leser bare `uri`, `name`, `description`, `mimeType` og `meta`. Verifisert: ingen av dem dukker
  opp i `resources/list`. Legg det klienten skal vise i `name` og `description`.

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

### T-07 · `create_booking`

Første **skrivende** verktøy, og starten på Epic 3. To nye filer:

| Fil | Hva |
|-----|-----|
| `src/main/java/no/computas/vacationmcp/tools/BookingTools.java` | `@Component` med `@McpTool(name = "create_booking")` → `BookingService.createBooking(...)` |
| `src/test/java/no/computas/vacationmcp/tools/BookingToolsTest.java` | `@SpringBootTest` med 10 tester, tall regnet for hånd mot `data.sql` |

Metodekroppen er igjen **én linje**: `return bookings.createBooking(customerName, destinationId,
from, to, numTravelers);`. Tjenesten gjør kundenavn-sjekken, kaller `PricingService.quote(...)`
(som validerer datoer, antall reisende og reisemål), finner den dekkende perioden, trekker fra
allerede bookede plasser, lagrer som `PENDING` og leser raden tilbake. Verktøyet legger ingenting
oppå — heller ikke kapasitetsregelen fra T-11, som allerede ligger der.

Klassen heter `BookingTools` fordi **T-08, T-09, T-10 og T-12 skal inn i samme klasse** (se
«Struktur for verktøyklasser»). Alle fem verktøyene går mot den samme `BookingService`-en, så
konstruktøren med én avhengighet holder hele veien; det som kommer til er metoder, ikke felt.
`Booking` returneres uendret etter T-03-konvensjonen — den bærer `id`, `status` og `totalPrice`,
altså alt akseptkriteriet ber om, pluss ekkoet av inputen.

#### `annotations`: det første verktøyet som ikke er `readOnly`

Den faktiske blokken fra `tools/list` mot den nybygde jar-en, ved siden av det de lesende
verktøyene (T-03–T-06) har:

```jsonc
// create_booking (T-07)                        // get_quote / list_destinations / …
"annotations": {                                // "annotations": {
  "title": "Opprett booking",                   //   "title": "Beregn pris",
  "readOnlyHint": false,                        //   "readOnlyHint": true,
  "destructiveHint": false,                     //   "destructiveHint": false,
  "idempotentHint": false,                      //   "idempotentHint": true,
  "openWorldHint": false                        //   "openWorldHint": false
}                                               // }
```

To av fire hint er altså snudd. Begrunnelsen, hint for hint:

- **`readOnlyHint = false`** — verktøyet setter inn en rad i `bookings` og beslaglegger plasser i
  perioden. Dette er hintet som *faktisk* betyr noe for en host: det er skillet mellom «et oppslag
  den kan gjøre fritt» og «en handling som bør bekreftes/logges». Klienter som Claude Desktop og
  Claude Code ber om godkjenning for verktøykall uansett, men `readOnlyHint` er det maskinlesbare
  signalet en host bruker til å skille «auto-approve»-kandidatene fra resten — og
  `list_destinations` skal ikke ligge i samme bøtte som `create_booking`.
- **`destructiveHint = false`** — dette er det hintet det er lettest å sette feil. Spesifikasjonen
  spør om oppdateringen er *additiv* eller *destruktiv*, ikke om den er «viktig». Et `INSERT`
  legger til en ny rad: ingenting overskrives, ingen eksisterende booking endres, ingen data går
  tapt, og handlingen kan angres (`cancel_booking`, T-12, frigjør kapasiteten igjen).
  Sammenlign med T-09/T-12, som *endrer* status på en rad som allerede finnes — der er svaret et
  annet. Merk at `false` her ikke betyr «kjør uten å spørre»; det er `readOnlyHint = false` som
  ber hosten involvere brukeren.
- **`idempotentHint = false`** — det viktigste hintet for akkurat dette verktøyet. To identiske
  kall gir **to** bookinger med hver sin id og dobbelt så mange plasser beslaglagt; det finnes
  ingen idempotensnøkkel som lar serveren kjenne igjen et gjentatt kall. Hintet forteller hosten
  at et automatisk retry etter timeout ikke er trygt, og modellen at den ikke skal «prøve igjen
  for sikkerhets skyld» hvis svaret ble borte. Verifisert i praksis: kall nummer to i røyktesten
  ville fått `id: 2`, ikke `id: 1` på nytt.
- **`openWorldHint = false`** — alt skjer mot vår egen SQLite-base med et lukket sett reisemål.
  Ingen betalingsleverandør, ingen ekstern booking-partner, ingen nettverkskall. Uendret fra de
  lesende verktøyene.

Verktøybeskrivelsen sier det samme i prosa, siden hintene er *advisory* og en modell ikke
nødvendigvis ser dem: «Dette er det første verktøyet som **endrer** noe … kall det bare én gang
per opphold — to like kall gir to bookinger.»

#### `inputSchema`: fem obligatoriske parametere

```jsonc
"required": ["customerName", "destinationId", "from", "to", "numTravelers"]
```

Ingen nye mønstre: `String → "string"`, `long → "integer"/"int64"`, `LocalDate →
"string"/"date"` (T-05-beslutningen), `int → "integer"/"int32"`. Alle er obligatoriske, så
primitivene er riktige — ingen «ikke oppgitt» som må overleve som `null`. `customerName` er
førstemann i `properties`, i samme rekkefølge som Java-parameterne.

#### Kapasitetsavvisningen (akseptkriteriet i T-11 — verifisert her, men T-11 er ikke krysset av)

Meldingen fra `BookingService` kommer uendret ut gjennom verktøylaget, og den er handlingsbar:
den sier **både** hvor mange plasser som er igjen og hvor mange som ble bedt om, så modellen kan
foreslå et lavere antall eller andre datoer uten et nytt oppslag:

```jsonc
{"content":[{"type":"text","text":"Error invoking method: createBooking\nIkke nok kapasitet i perioden: 1 ledige plasser, 2 forespurt"}],"isError":true}
```

Innpakningslinja «Error invoking method: createBooking» er den samme kosmetiske støyen T-04
allerede aksepterte. Beskrivelsen av verktøyet nevner meldingen ordrett og forteller hva modellen
skal gjøre med den. Selve T-11-raden i tabellen står med vilje urørt — den oppgaven er ikke
implementert her, bare demonstrert.

#### Verifisering

**1. `./gradlew build` er grønt** — 58 tester (48 fra før + 10 nye). De nye dekker vellykket
booking (id > 0, `status: PENDING`, `totalPrice` mot `data.sql`, og at ekkoet av inputen stemmer),
at den returnerte bookingen er lik den lagrede (`bookings.get(id)`), ukjent reisemål
(`NotFoundException`), datoer utenfor enhver periode, `numTravelers = 0`, tomt/blankt/`null`
kundenavn, `from` etter `to`, kapasitetsgrensen med **ordrett** melding (1 ledig av 3, 2
forespurt), en booking som fyller kapasiteten nøyaktig (og at neste da får «0 ledige plasser»),
og at kapasiteten gjelder per *overlappende* periode — samme reisemål med ikke-overlappende datoer
går fint.

Testreisemålet er **Kyoto Machiya (id 3)**: 1600/natt, én periode 2026-10-01→2026-11-30 uten
sesongpris og med kapasitet **3** — den laveste i seed-dataene, og derfor den som gjør
kapasitetsgrensen enkel å treffe med to kall.

> **Første testklasse som skriver til databasen.** Den følger opplegget fra `BookingServiceTest`:
> `DELETE FROM bookings` i `@BeforeEach`, slik at kapasiteten er kjent uansett hva som lå igjen
> fra en tidligere kjøring — `build/test-vacation.db` slettes ikke mellom `./gradlew test`-kall.
> Her er den samme metoden i tillegg annotert `@AfterEach`, så bookinger fra denne klassen ikke
> lekker inn i andre testklasser i samme kjøring (rekkefølgen er ikke garantert).

**2. Gjennom protokollen** — håndtrykk som i T-00, deretter `tools/list` og fire `tools/call` mot
den nybygde jar-en. Stderr bekreftet registreringen:
`Tilgjengelige MCP-tools (6): [about_application, check_availability, create_booking, list_destinations, search_destinations, get_quote]`.

| Argumenter (alle mot `destinationId: 3`) | Resultat |
|------------------------------------------|----------|
| `{"customerName":"Ola Nordmann","from":"2026-10-05","to":"2026-10-08","numTravelers":2}` | `isError:false` — se JSON-en under |
| `…,"numTravelers":0` | `isError:true`: `antall reisende må være minst 1` |
| `{"customerName":"Per Person","from":"2026-10-06","to":"2026-10-09","numTravelers":2}` | `isError:true`: `Ikke nok kapasitet i perioden: 1 ledige plasser, 2 forespurt` |
| `{"customerName":"  ", …}` | `isError:true`: `kundenavn må oppgis` |

Den vellykkede bookingen, ordrett fra tekstblokken:

```json
{"id":1,"customerName":"Ola Nordmann","destinationId":3,"startDate":"2026-10-05","endDate":"2026-10-08","numTravelers":2,"totalPrice":9600.0,"status":"PENDING"}
```

Kontrollregning mot `data.sql`: Kyoto koster 1600/natt og perioden (`availability` id 4) har
`season_price = NULL`, så normalprisen gjelder. 2026-10-05→2026-10-08 er **3 netter** × **2
reisende**: `1600 × 3 × 2 = 9600`. `id: 1` og `status: PENDING` er akseptkriteriet.

Det tredje kallet er kapasitetssjekken i praksis: 2 av 3 plasser var tatt av det første kallet,
og den nye forespørselen overlapper (6.–9. oktober mot 5.–8.) — derfor 1 ledig plass mot 2
forespurt.

> **Røyktesten skriver til den ekte `vacation.db` i prosjektroten.** Den er git-ignorert, men
> ikke tom — bookinger derfra ville ligget igjen og påvirket kapasiteten neste gang noen kjører
> serveren. Fila ble derfor kopiert før kjøringen og lagt tilbake etterpå
> (`select count(*) from bookings` er 0 igjen). Alternativet er å slette `vacation.db` og la
> `schema.sql`/`data.sql` seede den på nytt ved oppstart.

### T-08 · `get_booking`

Andre verktøy i `BookingTools`, og det første **lesende** verktøyet i en klasse som allerede
inneholder et skrivende. Ingen nye filer:

| Fil | Endring |
|-----|---------|
| `src/main/java/no/computas/vacationmcp/tools/BookingTools.java` | nytt `@McpTool(name = "get_booking")` → `BookingService.get(id)`; klasse-javadoc utvidet med «hint hører til metoden, ikke klassen» |
| `src/test/java/no/computas/vacationmcp/tools/BookingToolsTest.java` | 4 nye tester (alle felt etter oppslag, gjentatt oppslag, ukjent id, id som er borte) |

Metodekroppen er igjen **én linje**: `return bookings.get(id);`. Tjenesten gjør oppslaget mot
`BookingRepository.findById(...)` og kaster `NotFoundException` hvis `Optional`-en er tom, så
verktøyet har verken null-sjekk eller `try/catch`. `Booking` returneres uendret etter
T-03-konvensjonen — recorden bærer allerede `status` og `totalPrice`, altså alt en modell
trenger for å oppsummere bookingen, og det finnes ingenting å mappe.

#### Hint settes per metode, ikke per klasse

Dette er poenget oppgaven egentlig handler om. `BookingTools` er nå blandet, og de to
verktøyene i den har **motsatte** hint:

```jsonc
// create_booking (T-07)                        // get_booking (T-08)
"annotations": {                                // "annotations": {
  "title": "Opprett booking",                   //   "title": "Hent booking",
  "readOnlyHint": false,                        //   "readOnlyHint": true,
  "destructiveHint": false,                     //   "destructiveHint": false,
  "idempotentHint": false,                      //   "idempotentHint": true,
  "openWorldHint": false                        //   "openWorldHint": false
}                                               // }
```

Begge blokkene er hentet ordrett fra det samme `tools/list`-svaret. `annotations` er et
attributt på `@McpTool`, altså på **metoden**, og i protokollen er hvert verktøy sin egen
oppføring i `tools`-arrayet — hosten ser aldri hvilken Java-klasse de kom fra. At vi samler
booking-verktøyene i én klasse er ren kodeorganisering (se «Struktur for verktøyklasser»); det
gir ingen felles «skrivende» sikkerhetsprofil. Motsatt vei er fella tydeligere: hadde
`get_booking` arvet naboen sin `readOnlyHint = false`, ville en host som skiller auto-approve
fra bekreftelseskrevende kall bedt brukeren om å godkjenne et rent `SELECT`, og
readOnly-hintets verdi som signal ville forsvunnet. Konkret for de to som snus tilbake:

- **`readOnlyHint = true`** — et oppslag leser én rad og endrer ingenting.
- **`idempotentHint = true`** — samme id gir samme svar. Formelt sier hintet at *gjentatte kall
  ikke gir ytterligere effekt*, og et `SELECT` har ingen effekt i det hele tatt. At svaret kan
  endre seg hvis `update_booking_status` (T-09) kjører imellom, er irrelevant for hintet — det
  handler om verktøyets egne bivirkninger, ikke om at verden står stille.

`destructiveHint = false` og `openWorldHint = false` er uendret fra `create_booking`, men av
enda mer opplagte grunner: ingenting slettes eller overskrives, og alt ligger i vår egen
SQLite-base.

#### `inputSchema`: én obligatorisk parameter

```jsonc
"inputSchema": {
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "id": {"type": "integer", "format": "int64", "description": "Id-en til bookingen, slik den kom i `id`-feltet fra `create_booking`. Et positivt heltall. En ukjent id gir en feilmelding, ikke et tomt svar — ikke gjett."}
  },
  "required": ["id"]
}
```

Primitiv `long` er riktig her av samme grunn som i T-06/T-07: verdien er obligatorisk, så det
finnes ingen «ikke oppgitt» som må overleve som `null`. Skjemavalideringen håndhever `required`
før metoden kalles — et kall med `{}` ga
`Tool (get_booking) input validation failed: … [: påkrevd egenskap 'id' ikke funnet]`.

#### `NotFoundException` for ukjent id — den faktiske meldingen

Mønsteret fra T-04 gjelder uendret: ingen `try/catch`, exception-en får boble. Verifisert
gjennom protokollen med `{"id":999}`:

```jsonc
{"jsonrpc":"2.0","id":5,"result":{"content":[{"type":"text","text":"Error invoking method: getBooking\nFant ingen booking med id 999"}],"isError":true}}
```

Altså `result` med `isError: true` (ikke JSON-RPC `error`), ingen stacktrace, og to linjer:
Spring AI sin innpakning `Error invoking method: getBooking` + `BookingService` sin egen
melding `Fant ingen booking med id 999`. Meldingen navngir id-en, så modellen ser hvilket
oppslag som feilet uten å gjette. Verktøybeskrivelsen siterer den ordrett og sier hva modellen
skal gjøre: spør brukeren om referansen, ikke prøv deg fram med flere id-er.

#### Krysshenvisningen

`description` peker på `create_booking` som kilden til `id`-en (og sier eksplisitt at
`get_booking` *ikke* oppretter noe), og på `list_destinations` for å oversette `destinationId`
til et navn. Den nevner med vilje **ikke** `list_bookings` (T-10): den finnes ikke i `tools/list`
ennå, og en modell som leser «bruk `list_bookings` for å finne id-en» ville forsøkt et kall som
ikke eksisterer. Krysshenvisningen begge veier legges inn når T-10 faktisk lander — samme
rekkefølge som T-03/T-04 gjorde det.

#### Verifisering

**1. `./gradlew build` er grønt** — 62 tester (58 fra før + 4 nye). De nye dekker: oppslag av en
nyopprettet booking der **alle åtte feltene** sjekkes enkeltvis og recorden i tillegg
sammenlignes som helhet med det `create_booking` returnerte; to identiske oppslag som gir
identisk svar (`idempotentHint = true` i praksis); ukjent id (999) med **ordrett** melding; og
en id som fantes, men er borte etter `DELETE FROM bookings` — også der en feil, ikke et tomt
svar. Samme opprydding som resten av klassen (`DELETE FROM bookings` i `@BeforeEach` +
`@AfterEach`).

**2. Gjennom protokollen** — håndtrykk som i T-00, deretter `tools/list` og fire `tools/call`
mot den nybygde jar-en. Stderr bekreftet registreringen:
`Tilgjengelige MCP-tools (7): [about_application, check_availability, create_booking, get_booking, list_destinations, search_destinations, get_quote]`.

| Kall | Resultat |
|------|----------|
| `create_booking` `{"customerName":"Ola Nordmann","destinationId":3,"from":"2026-10-05","to":"2026-10-08","numTravelers":2}` | `isError:false`, `id: 1`, `status: PENDING`, `totalPrice: 9600.0` |
| `get_booking` `{"id":1}` | `isError:false` — **byte for byte samme JSON** som `create_booking` returnerte |
| `get_booking` `{"id":999}` | `isError:true`: `Error invoking method: getBooking` / `Fant ingen booking med id 999` |
| `get_booking` `{}` | `isError:true`: skjemavalideringen, `påkrevd egenskap 'id' ikke funnet` |

Den hentede bookingen, ordrett fra tekstblokken:

```json
{"id":1,"customerName":"Ola Nordmann","destinationId":3,"startDate":"2026-10-05","endDate":"2026-10-08","numTravelers":2,"totalPrice":9600.0,"status":"PENDING"}
```

At de to svarene er identiske er selve akseptkriteriet: bookingen som ble lagret, er den som
kommer tilbake — inkludert `startDate`/`endDate` som `"2026-10-05"`-strenger gjennom
`LocalDate`-serialiseringen fra T-05.

> **Røyktesten skriver til `vacation.db` i prosjektroten**, akkurat som i T-07. Fila ble kopiert
> før kjøringen og lagt tilbake etterpå (`select count(*) from bookings` er 0 igjen).
> Hjelpeskriptet lå i en scratchpad-katalog utenfor repoet.

### T-09 · `update_booking_status`

Tredje verktøy i `BookingTools`, og det første der et **enum** krysser MCP-grensen. Ingen nye
filer:

| Fil | Endring |
|-----|---------|
| `src/main/java/no/computas/vacationmcp/tools/BookingTools.java` | nytt `@McpTool(name = "update_booking_status")` → `BookingService.updateStatus(id, target)`; `get_booking` fikk krysshenvisning til det nye verktøyet |
| `src/test/java/no/computas/vacationmcp/tools/BookingToolsTest.java` | 7 nye tester (lovlig overgang, hele kjeden, hoppet steg, terminal status, gjentatt kall, kansellering fra alle tre ikke-terminale statuser, ukjent id) |

Metodekroppen er igjen **én linje**: `return bookings.updateStatus(id, status);`. Tjenesten slår
opp bookingen, spør `BookingStatus.canTransitionTo(...)` og kaster `ValidationException` ved en
ulovlig overgang — verktøyet kjenner ingen regler selv og gjør ingen forhåndssjekk.

#### Enum-et: `BookingStatus`, ikke `String` — og skjemaet avgjorde det

Dette er kjernen i oppgaven, og svaret er utvetydig. Den faktiske `inputSchema`-en fra
`tools/list` mot den nybygde jar-en:

```jsonc
"inputSchema": {
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "id": {
      "type": "integer",
      "format": "int64",
      "description": "Id-en til bookingen som skal endres, slik den kom fra `create_booking` eller `get_booking`. En ukjent id gir en feilmelding, og ingenting endres — ikke gjett."
    },
    "status": {
      "type": "string",
      "enum": ["PENDING", "CONFIRMED", "PAID", "COMPLETED", "CANCELLED"],
      "description": "Statusen bookingen skal flyttes til. Må være én av de fem verdiene i skjemaet, skrevet med store bokstaver. Overgangen må være lovlig fra statusen bookingen står i nå: …"
    }
  },
  "required": ["id", "status"]
}
```

**Enum-verdiene kommer med.** Spring AI reflekterer over Java-typen og legger konstantnavnene inn
som en `enum`-liste, i deklarasjonsrekkefølgen fra `BookingStatus` (som tilfeldigvis også er
rekkefølgen i livssyklusen). Modellen får dermed hele verdimengden maskinlesbart, uten at vi har
skrevet den noe sted. Det er nøyaktig det [T-00 gjettet](#t-00--se-mcp-protokollen-før-du-bruker-spring-annotasjoner)
(«enum → `"enum":[…]`»), og det avgjorde valget: med `String` hadde skjemaet vært et bart
`{"type":"string"}`, og de fem verdiene måtte stått som prosa i `description` — der de ville
råtnet stille hvis noen la til en status i enum-et. Nå kan de ikke komme ut av synk.

#### Hvor «BANANA» stoppes — sammenlignet med `format: "date"` fra T-05

Fire sonderinger gjennom protokollen:

| Argument for `status` | Svar | Hvem stoppet det |
|-----------------------|------|------------------|
| `"CONFIRMED"` (lovlig overgang) | `isError:false`, oppdatert booking | — (gyldig) |
| `"BANANA"` | `isError:true`: `Tool (update_booking_status) input validation failed: … [/status: har ikke en verdi i oppregningen ["PENDING", "CONFIRMED", "PAID", "COMPLETED", "CANCELLED"]]` | **skjemavalideringen**, før metoden |
| `"confirmed"` (små bokstaver) | samme melding som over | **skjemavalideringen** — `enum` er case-sensitiv |
| utelatt | `isError:true`: `… [: påkrevd egenskap 'status' ikke funnet]` | **skjemavalideringen** (`required`) |

**Kontrasten til T-05 er poenget.** `format: "date"` er i JSON Schema bare en *annotasjon*, så
«01.07.2026» slipper forbi validatoren og stoppes først av Jackson, med en melding som lekker
`java.time.LocalDate`. `enum` er derimot en ekte *begrensning*: den håndheves av serverens
skjemavalidering, akkurat som `type` og `required`, og en ugyldig verdi når aldri
deserialiseringen — langt mindre metoden. Meldingen er dessuten bedre enn den vi ville skrevet
selv: den ramser opp de lovlige verdiene, så modellen kan rette seg uten et nytt `tools/list`.
Enum-et er altså det sterkeste av de tre nivåene vi har sett så langt: `String` (ingenting),
`format` (hint til modellen), `enum` (håndhevet kontrakt).

Det skjemaet **ikke** kan uttrykke, er tilstandsmaskinen. `enum` sier hvilke verdier som finnes,
ikke hvilke overganger som er lov fra der bookingen står *nå* — det avhenger av databasens
tilstand og hører hjemme i `BookingStatus.canTransitionTo(...)`.

#### Meldingen ved ulovlig overgang — og hva `description` måtte kompensere for

```jsonc
{"content":[{"type":"text","text":"Error invoking method: updateBookingStatus\nUlovlig statusovergang: COMPLETED -> PENDING"}],"isError":true}
```

Mønsteret fra T-04 uendret: `result` med `isError: true`, ingen stacktrace, Spring AI sin
innpakningslinje + tjenestens egen melding. Meldingen er lesbar og navngir **begge** statusene, så
modellen ser hva den forsøkte og hvor bookingen faktisk står. Men den sier ikke hva som *er*
lovlig — «`COMPLETED -> PENDING` er ulovlig» hjelper ikke en modell til å gjette at `PAID` er det
eneste steget videre fra `CONFIRMED`. Å legge de lovlige overgangene inn i feilmeldingen ville
krevd ny logikk i verktøylaget (eller i `BookingService`, som er gitt kode), så kompensasjonen
ligger der den hører hjemme: i `description`, som ramser opp hele tilstandsmaskinen som en liste
og sier eksplisitt at `COMPLETED`/`CANCELLED` er endestasjoner, at kjeden bare går framover ett
steg om gangen, og at et gjentatt kall med samme verdi avvises.

#### `annotations`: `destructiveHint = true`, `idempotentHint = true` — begge motsatt av T-07

```jsonc
// create_booking (T-07)                        // update_booking_status (T-09)
"annotations": {                                // "annotations": {
  "title": "Opprett booking",                   //   "title": "Endre bookingstatus",
  "readOnlyHint": false,                        //   "readOnlyHint": false,
  "destructiveHint": false,                     //   "destructiveHint": true,
  "idempotentHint": false,                      //   "idempotentHint": true,
  "openWorldHint": false                        //   "openWorldHint": false
}                                               // }
```

To skrivende verktøy i samme klasse, og likevel motsatt svar på to av fire hint — samme lærdom
som i T-08, bare skarpere: hintene beskriver *hva metoden gjør*, ikke hvilken bøtte den ligger i.

- **`readOnlyHint = false`** — et `UPDATE` mot `bookings`. Samme som T-07: hosten skal behandle
  kallet som en handling, ikke som et oppslag.
- **`destructiveHint = true`** — her skiller det seg fra `create_booking`. Spesifikasjonen spør om
  oppdateringen er *additiv* eller *destruktiv*. T-07 gjør et `INSERT`: en ny rad, ingenting går
  tapt. Dette verktøyet **overskriver** `status` på en rad som allerede finnes — den forrige
  verdien er borte etterpå — og tilstandsmaskinen er enveiskjørt, så `COMPLETED` og `CANCELLED`
  ikke kan angres. `CANCELLED` avlyser i tillegg en reell booking og frigjør plassene til andre.
  Alle tre pekene sier destruktiv.
- **`idempotentHint = true`** — også motsatt av T-07, og det som gjør oppgaven verdt å tenke
  gjennom. Hintet handler om **effekten** av gjentatte kall, ikke om svaret: kaller du
  `{"id":1,"status":"CONFIRMED"}` to ganger, flytter det første bookingen, mens det andre avvises
  med `Ulovlig statusovergang: CONFIRMED -> CONFIRMED` (en overgang til seg selv er ikke en kant i
  maskinen). Databasen er identisk etter kall to som etter kall ett — ingen ytterligere effekt,
  altså idempotent. Kontrasten til `create_booking`, der kall to gir en *ny* booking med ny id, er
  hele forskjellen. Praktisk konsekvens for hosten: et retry etter timeout kan ikke dobbelt-flytte
  en booking fra `PENDING` til `PAID`. Konsekvensen for modellen står i `description`: et retry som
  svarer «Ulovlig statusovergang: X -> X» betyr som regel at det *første* kallet gikk gjennom —
  bekreft med `get_booking` framfor å konkludere med at endringen feilet.
- **`openWorldHint = false`** — fortsatt bare vår egen SQLite-base.

#### Verifisering

**1. `./gradlew build` er grønt** — 69 tester (62 fra før + 7 nye). De nye dekker: lovlig overgang
`PENDING → CONFIRMED` der endringen også sjekkes lagret og resten av raden verifiseres uendret;
hele kjeden `PENDING → CONFIRMED → PAID → COMPLETED`; et hoppet steg (`PENDING → PAID`) med
**ordrett** melding og kontroll på at statusen står urørt etterpå; begge ulovlige overgangene ut
av `COMPLETED` (`PENDING` og `CANCELLED`); et gjentatt identisk kall (`idempotentHint = true` i
praksis: avvist, men bookingen er bit for bit den samme); kansellering fra alle tre ikke-terminale
statusene pluss at `CANCELLED` ikke kan gjenopplives; og ukjent id (999). Testene bruker
ikke-overlappende datovinduer innenfor Kyoto-perioden, slik at kapasiteten på 3 ikke blander seg
inn — kapasitet er T-11 sitt tema. Samme opprydding som resten av klassen (`DELETE FROM bookings`
i `@BeforeEach` + `@AfterEach`).

**2. Gjennom protokollen** — håndtrykk som i T-00, deretter `tools/list` og ni `tools/call` mot den
nybygde jar-en. Stderr bekreftet registreringen:
`Tilgjengelige MCP-tools (8): [about_application, check_availability, create_booking, get_booking, update_booking_status, list_destinations, search_destinations, get_quote]`.

| Kall | Resultat |
|------|----------|
| `create_booking` `{"customerName":"Ola Nordmann","destinationId":3,"from":"2026-10-05","to":"2026-10-08","numTravelers":2}` | `isError:false`, `id: 1`, `status: PENDING` |
| `{"id":1,"status":"CONFIRMED"}` | `isError:false`, hele bookingen tilbake med `"status":"CONFIRMED"` |
| `{"id":1,"status":"CONFIRMED"}` (igjen) | `isError:true`: `Ulovlig statusovergang: CONFIRMED -> CONFIRMED` |
| `{"id":1,"status":"PENDING"}` | `isError:true`: `Ulovlig statusovergang: CONFIRMED -> PENDING` (ingen vei bakover) |
| `{"id":1,"status":"BANANA"}` | `isError:true`: **skjemavalideringen**, se tabellen over |
| `{"id":1,"status":"PAID"}` → `{"id":1,"status":"COMPLETED"}` | begge `isError:false` — kjeden fullført |
| `{"id":1,"status":"CANCELLED"}` | `isError:true`: `Ulovlig statusovergang: COMPLETED -> CANCELLED` |
| `{"id":999,"status":"CONFIRMED"}` | `isError:true`: `Fant ingen booking med id 999` |

Den oppdaterte bookingen, ordrett fra tekstblokken etter `CONFIRMED`:

```json
{"id":1,"customerName":"Ola Nordmann","destinationId":3,"startDate":"2026-10-05","endDate":"2026-10-08","numTravelers":2,"totalPrice":9600.0,"status":"CONFIRMED"}
```

Bare `status` er endret — resten er byte for byte det `create_booking` returnerte. `Booking`
returneres uendret etter T-03-konvensjonen; recorden bærer allerede den nye statusen, så det
finnes ingen kvittering å konstruere.

> **Røyktesten skriver til `vacation.db` i prosjektroten**, som i T-07/T-08. Fila ble kopiert før
> kjøringen og lagt tilbake etterpå (`select count(*) from bookings` er 0 igjen). Hjelpeskriptene
> lå i en scratchpad-katalog utenfor repoet.

#### Krysshenvisningen

`get_booking` sier nå eksplisitt at den *ikke* flytter status, og peker på
`update_booking_status` — samme begge-veier-mønster som T-03/T-04. Beskrivelsen av det nye
verktøyet peker motsatt vei: slå opp bookingen med `get_booking` først for å se hvilken status den
faktisk står i, og bruk den samme etter et tvilsomt retry. `list_bookings` (T-10) er med vilje
ikke nevnt ennå — den finnes ikke i `tools/list` før den oppgaven lander.

### T-10 · `list_bookings`

Fjerde verktøy i `BookingTools`, og det første med en **valgfri enum-parameter** — altså T-04
(`required = false`) og T-09 (enum-typen over grensen) i samme delskjema. Ingen nye filer:

| Fil | Endring |
|-----|---------|
| `src/main/java/no/computas/vacationmcp/tools/BookingTools.java` | nytt `@McpTool(name = "list_bookings")` → `BookingService.list(status)`; `get_booking` fikk den utsatte krysshenvisningen fra T-08 |
| `src/test/java/no/computas/vacationmcp/tools/BookingToolsTest.java` | 7 nye tester (uten filter, hver relevante status, de to terminale, filter uten treff, tom database, at nyopprettede dukker opp, gjentatt kall) |

Metodekroppen er igjen **én linje**: `return bookings.list(status);`. `BookingService.list(...)`
velger selv mellom `findAll()` og `findByStatus(...)` på `null`, og repository-et sorterer på
`id` — verktøyet filtrerer, sorterer og pakker ingenting.

#### Skjemaet: `enum`-lista **og** et tomt `required`

Den faktiske `inputSchema`-en fra `tools/list` mot den nybygde jar-en:

```jsonc
"inputSchema": {
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "status": {
      "type": "string",
      "enum": ["PENDING", "CONFIRMED", "PAID", "COMPLETED", "CANCELLED"],
      "description": "Valgfritt statusfilter. **Utelat parameteren** for å få alle bookinger — ikke finn på en verdi for å «fylle den ut». Oppgir du den, må det være én av de fem verdiene i skjemaet …"
    }
  },
  "required": []
}
```

Dette er den interessante kombinasjonen, og de to halvdelene er uavhengige:

- **`enum`-lista kommer av typen** (T-09). Den er der uansett om parameteren er obligatorisk
  eller ikke — `required` og `enum` er to forskjellige nøkkelord i JSON Schema, og det ene sier
  *om* feltet må være med, det andre *hvilke verdier* det kan ha hvis det er med.
- **Tomt `required` kommer av `@McpToolParam(required = false)`** (T-04). Uten den ville
  `status` stått i lista, siden Spring AI har `PROPERTY_REQUIRED_BY_DEFAULT = true` — og en
  modell som bare ville se *alle* bookinger måtte oppgitt en av de fem verdiene og dermed
  filtrert bort resten. Nøyaktig samme felle som i T-04, bare mer skadelig her, fordi enum-et
  ikke har noen «nøytral» verdi å velge.

Til sammen: modellen kan utelate argumentet helt, men *oppgir* den det, håndhever validatoren at
verdien er en av de fem. Verifisert med begge halvdelene gjennom protokollen — `{}` går gjennom
(og gir alle), mens `{"status":"BANANA"}` **og** `{"status":"pending"}` (små bokstaver — `enum` er
case-sensitiv) stoppes av skjemavalideringen, før metoden:

```jsonc
{"content":[{"type":"text","text":"Tool (list_bookings) input validation failed: Validation failed: JSON schema validation errors: [/status: har ikke en verdi i oppregningen [\"PENDING\", \"CONFIRMED\", \"PAID\", \"COMPLETED\", \"CANCELLED\"]]"}],"isError":true}
```

**Ingen boksing å tenke på.** T-04 måtte bruke `Double` framfor `double` for at «ikke oppgitt»
skulle overleve som `null`. Et enum er en referansetype, så det problemet finnes ikke: `null`
kommer rett inn i `BookingService.list(...)`, som er skrevet for nettopp det. Regelen fra
«Struktur for verktøyklasser» gjelder altså bare primitiver.

#### Svarformen: en bar `List<Booking>`, ikke en konvolutt

Dette er det bevisste valget oppgaven ber om, og svaret er **bar liste** — T-03-konvensjonen,
som `list_destinations` og `search_destinations` også følger. T-05 sin `AvailabilityResult`
er unntaket som ble laget for et konkret problem, og det problemet finnes ikke her:

| T-05 sin begrunnelse | Gjelder for `list_bookings`? |
|----------------------|------------------------------|
| En **ukjent id** gir stille tomt svar, umulig å skille fra «ingenting ledig» | Nei — den eneste parameteren er et enum validatoren allerede har godkjent. Det finnes ingen «ugyldig men taus» input |
| Spørringen har **ikke-åpenbar semantikk** (overlapp ≠ dekket), så ekkoet hjelper modellen | Nei — «bookinger med status X, sortert på id» er akkurat det det ser ut som |
| Modellen trenger å se **hva den faktisk spurte om** | Nei — `status` er verdien modellen selv sendte i samme kall, ikke noe serveren utledet |

Det som blir igjen av T-05-argumentet, er at et bart `[]` kan leses som en feil. Den risikoen er
reell, men den løses billigere i `description` enn med en konvolutt som dupliserer inputen:
beskrivelsen sier ordrett at «en **tom liste er et gyldig svar**, ikke en feil», hva den betyr
(ingen bookinger med den statusen — eller ingen bookinger i det hele tatt), og hva modellen skal
gjøre (si det til brukeren, eventuelt prøve uten filter). Et `matchingBookings`-felt hadde
dessuten vært ren duplisering: en liste kan telles, mens `matchingPeriods` i T-05 sto sammen med
et ekko som faktisk bar ny informasjon.

Konsekvensen er at svaret er identisk i form med `list_destinations` — et JSON-array av
domene-records, ingen mapping, ingen DTO. Legges det til et felt i `Booking`, er det med.

#### Krysshenvisningen begge veier (den T-08 utsatte)

T-08 lot med vilje være å nevne `list_bookings` i `get_booking`, fordi verktøyet ikke fantes i
`tools/list` ennå og en modell ikke skal ledes mot et kall som ikke eksisterer. Nå er begge
retningene på plass:

- **`get_booking` → `list_bookings`:** «Kjenner du ikke id-en, kall `list_bookings` og finn
  bookingen i lista, eller spør brukeren om referansen — ikke prøv deg fram med flere id-er her.»
  (Erstatter den forrige formuleringen, som bare hadde «spør brukeren».)
- **`list_bookings` → `get_booking`:** «Har du allerede id-en, bruk `get_booking` i stedet; det er
  ett oppslag i stedet for hele lista.»

Beskrivelsen peker også videre til `create_booking` og `update_booking_status`, og forklarer at
`destinationId` er en id som må slås opp med `list_destinations` for å bli et navn — samme
presisering som i `get_booking`. Én ting til som ellers ville blitt gjettet feil: **kansellerte
bookinger blir liggende** i lista med status `CANCELLED`; de forsvinner ikke, men teller ikke
lenger mot kapasiteten.

#### `annotations`: lesende, per metode

```jsonc
"annotations": {"title":"List bookinger","readOnlyHint":true,"destructiveHint":false,"idempotentHint":true,"openWorldHint":false}
```

Identisk med `get_booking` (T-08) og de lesende verktøyene i T-03–T-06, og ulikt de to skrivende
nabometodene i samme klasse. Ingen ny vurdering: et `SELECT` endrer ingenting, gjentatte kall har
ingen effekt, og alt ligger i vår egen SQLite-base. `BookingTools` har nå **to** lesende og **to**
skrivende verktøy — poenget fra T-08 om at hintene hører til metoden, ikke klassen, er dermed
demonstrert i begge retninger.

#### Verifisering

**1. `./gradlew build` er grønt** — 76 tester (69 fra før + 7 nye). De nye dekker: alle bookinger
uten filter, i `id`-rekkefølge; at en nyopprettet booking dukker opp med det samme og er bit for
bit det `create_booking` returnerte; filtrering på hver av `PENDING`/`CONFIRMED`/`PAID` med tre
bookinger i hver sin status samtidig; de to terminale statusene (`COMPLETED`/`CANCELLED`) og at en
kansellert booking blir liggende i lista uten filter; et filter uten treff (tom liste, ikke feil);
en helt tom database (tom liste for `null` og for alle fem statusene); og to identiske kall som gir
identisk svar (`idempotentHint = true`). Statustestene bruker ikke-overlappende datovinduer innenfor
Kyoto-perioden, som i T-09, så kapasiteten på 3 ikke blander seg inn. Samme opprydding som resten av
klassen (`DELETE FROM bookings` i `@BeforeEach` + `@AfterEach`).

**2. Gjennom protokollen** — håndtrykk som i T-00, deretter `tools/list` og ni `tools/call` mot den
nybygde jar-en. Stderr bekreftet registreringen:
`Tilgjengelige MCP-tools (9): [about_application, check_availability, create_booking, get_booking, list_bookings, update_booking_status, list_destinations, search_destinations, get_quote]`.

| Kall | Resultat |
|------|----------|
| `list_bookings` `{}` (tom database) | `isError:false`, `[]` — tom liste, ikke feil |
| `create_booking` × 2 (Kyoto, 5.–8. og 20.–23. oktober) | `isError:false`, `id: 1` og `id: 2`, begge `PENDING` |
| `update_booking_status` `{"id":2,"status":"CONFIRMED"}` | `isError:false` |
| `list_bookings` `{}` | `isError:false`, **begge** bookingene, sortert på id |
| `list_bookings` `{"status":"CONFIRMED"}` | `isError:false`, bare `id: 2` |
| `list_bookings` `{"status":"CANCELLED"}` | `isError:false`, `[]` — gyldig status, ingen treff |
| `list_bookings` `{"status":"BANANA"}` | `isError:true`: skjemavalideringen, se JSON-en over |
| `list_bookings` `{"status":"pending"}` | `isError:true`: samme melding — `enum` er case-sensitiv |

Lista uten filter, ordrett fra tekstblokken:

```json
[{"id":1,"customerName":"Ola Nordmann","destinationId":3,"startDate":"2026-10-05","endDate":"2026-10-08","numTravelers":2,"totalPrice":9600.0,"status":"PENDING"},
 {"id":2,"customerName":"Kari Nordmann","destinationId":3,"startDate":"2026-10-20","endDate":"2026-10-23","numTravelers":1,"totalPrice":4800.0,"status":"CONFIRMED"}]
```

Elementene er byte for byte de samme JSON-objektene `create_booking` og `update_booking_status`
returnerte — ingen mapping på veien, som konvensjonen fra T-03 lover. Kontrollregning mot
`data.sql`: Kyoto 1600/natt uten sesongpris, `1600 × 3 × 2 = 9600` og `1600 × 3 × 1 = 4800`.

De to tomme svarene (`{}` mot tom database, og `CANCELLED` uten treff) er verdt å merke seg ved
siden av hverandre: begge er `isError:false` med `[]`, og de er umulige å skille fra hverandre uten
å se argumentene. Det er nøyaktig det beskrivelsen kompenserer for.

> **Røyktesten skriver til `vacation.db` i prosjektroten**, som i T-07–T-09. Fila ble kopiert før
> kjøringen og lagt tilbake etterpå (`select count(*) from bookings` er 0 igjen). Hjelpeskriptet lå
> i en scratchpad-katalog utenfor repoet.

### T-11 · Avvis overbooking

**En verifikasjonsoppgave, ikke en implementasjonsoppgave.** Regelen ligger allerede i
`BookingService.createBooking(...)`, og akseptkriteriet («summen av aktive bookinger + ny booking
≤ kapasitet») er oppfylt fra før. Jobben her var å kartlegge *nøyaktig* hvordan den regner, bevise
gjennom protokollen at MCP-laget formidler den, og tette hullene i formidlingen. **Ingen ny
forretningslogikk, ingen ny SQL, ingen `try/catch` i verktøylaget.**

| Fil | Endring |
|-----|---------|
| `src/main/java/no/computas/vacationmcp/tools/BookingTools.java` | `description` på `create_booking` utvidet med hva modellen skal gjøre med kapasitetsfeilen; javadoc peker på hvor regelen faktisk bor |
| `src/main/java/no/computas/vacationmcp/tools/AvailabilityTools.java` | `description` på `check_availability` presisert: `capacity` er periodens **totale** antall plasser, ikke ledige |
| `src/test/java/no/computas/vacationmcp/tools/BookingToolsTest.java` | 7 nye tester som pinner ned grensetilfellene |

#### Kapasitetsregnestykket, presist

Tre kodelinjer i `BookingService.createBooking(...)` er hele regelen:

```java
Availability period = pricing.findCoveringPeriod(destinationId, from, to);
int alreadyBooked = bookings.sumActiveTravelers(destinationId, from, to);
int remaining = period.capacity() - alreadyBooked;
if (numTravelers > remaining) { throw new ValidationException(...); }
```

og SQL-en bak `sumActiveTravelers` er den som bestemmer *hvem* som teller:

```sql
SELECT COALESCE(SUM(num_travelers), 0) FROM bookings
WHERE destination_id = ? AND status <> 'CANCELLED' AND start_date < ? /* to */ AND end_date > ? /* from */
```

Punkt for punkt — dette er fasiten deltakerne skal kunne sjekke seg mot:

1. **Kapasiteten kommer fra én enkelt `availability`-rad.** `findCovering` er
   `start_date <= from AND end_date >= to ORDER BY start_date LIMIT 1`, altså raden som dekker
   **hele** oppholdet. Kapasitet **summeres aldri på tvers av rader**, og et opphold som krysser
   skjøten mellom to tilstøtende perioder avvises *før* kapasiteten regnes ut — med «Ingen
   tilgjengelig periode dekker …», ikke en kapasitetsfeil. Lofoten (id 1) har to slike perioder
   (`07-01→08-31` og `09-01→10-31`), og 2026-08-30→2026-09-02 faller mellom to stoler selv om
   begge sidene er åpne og ledige. Skulle to rader overlappe i tid, vinner den med lavest
   `start_date` (`LIMIT 1`) — det forekommer ikke i `data.sql`.
2. **Hvilke bookinger teller:** alle på **samme reisemål** som overlapper datoene, i **alle
   statuser unntatt `CANCELLED`**. `PENDING`, `CONFIRMED`, `PAID` *og* `COMPLETED` holder like
   godt på plassene sine; det er bare `CANCELLED` som slipper dem — og det er hele mekanismen
   T-12 lener seg på. Summeringen bryr seg **ikke** om hvilken `availability`-rad de bookingene
   hørte til; den filtrerer bare på `destination_id` og datooverlapp. (Uproblematisk her, siden
   periodene i `data.sql` ikke overlapper hverandre: to bookinger i ulike perioder kan uansett
   ikke overlappe i dato.)
3. **Overlapp er halvåpent:** `start_date < to AND end_date > from`, med *strenge* ulikheter.
   Utsjekksdagen er fri — et opphold som **starter** på utsjekksdagen til et annet kolliderer
   ikke. Delvis overlapp teller derimot fullt ut: én felles natt er nok til at *hele*
   `num_travelers` fra den andre bookingen trekkes fra.
4. **Summen tas over hele det forespurte vinduet, ikke per dag.** Regelen er derfor
   **konservativ**: den slipper aldri gjennom en dag med flere reisende enn kapasiteten (enhver
   booking som dekker en dag i vinduet er med i summen), men den kan avvise et opphold som
   strengt tatt hadde fått plass. Kyoto (kapasitet 3) med bookinger 20.–22. (2 reisende) og
   26.–28. (2 reisende): et opphold 21.–27. for **1** reisende avvises, selv om ingen enkeltdag
   ville hatt mer enn 3. Verifisert både i test og gjennom protokollen.
5. **Grensen er inklusiv.** `numTravelers > remaining` avviser, så *akkurat* på grensen er
   lovlig. `remaining` kan bli negativ (punkt 4), men meldingen viser
   `Math.max(remaining, 0)` — modellen ser aldri «-1 ledige plasser».
6. **Sjekken er ikke transaksjonell.** `sumActiveTravelers` og `insert` er to separate spørringer
   uten lås, så to samtidige kall kan i teorien begge se plass. Irrelevant for en stdio-server med
   én klient, men verdt å vite hvis noen tar med seg mønsteret videre.

#### Gjennom protokollen — den faktiske feil-JSON-en

Røyktest mot `build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar` (håndtrykk som i T-00, deretter
13 `tools/call`). Reisemålet er **Kyoto Machiya (id 3)** — 1600/natt, én periode
`2026-10-01→2026-11-30` uten sesongpris og med **kapasitet 3**, den laveste i `data.sql` og derfor
den som er raskest å fylle opp.

Feilmeldingen klienten faktisk får, ordrett:

```jsonc
{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"Error invoking method: createBooking\nIkke nok kapasitet i perioden: 1 ledige plasser, 2 forespurt"}],"isError":true}}
```

Hele sekvensen, i rekkefølge:

| # | Kall (alle mot `destinationId: 3` der ikke annet står) | Resultat |
|---|--------------------------------------------------------|----------|
| 1 | `create_booking` Ola, 10-05→10-08, **2** | `isError:false`, `id: 1` — 2 av 3 plasser tatt |
| 2 | `create_booking` Kari, 10-06→10-09, **2** | `isError:true`: **`1 ledige plasser, 2 forespurt`** — delvis overlapp teller fullt |
| 3 | `create_booking` Kari, 10-06→10-09, **1** | `isError:false`, `id: 2` — nøyaktig på grensen (2 + 1 = 3) går gjennom |
| 4 | `create_booking` Per, 10-07→10-10, **1** | `isError:true`: **`0 ledige plasser, 1 forespurt`** — én over grensen |
| 5 | `check_availability` 10-07→10-10 | `isError:false`, `"capacity":3` — **totalkapasiteten**, selv om 0 er ledige |
| 6 | `update_booking_status` `{"id":1,"status":"CANCELLED"}` | `isError:false` — frigjør 2 plasser |
| 7 | `create_booking` Per, 10-07→10-10, **1** (kall 4 om igjen) | `isError:false`, `id: 3` — kansellering frigjorde plassen |
| 8 | `create_booking` Nina, 10-09→10-13, **3** | `isError:true`: `2 ledige plasser, 3 forespurt` — én natt overlapp med id 3 |
| 9 | `create_booking` Nina, **10-10**→10-13, **3** | `isError:false`, `id: 4` — innsjekk på utsjekksdagen kolliderer ikke |
| 10–11 | `create_booking` 10-20→10-22 **2**, og 10-26→10-28 **2** | `isError:false`, `id: 5` og `id: 6` |
| 12 | `create_booking` 10-21→10-27, **1** | `isError:true`: `0 ledige plasser, 1 forespurt` — summen over hele vinduet er 4 (`remaining = -1`, klippet til 0) |
| 13 | `create_booking` **Lofoten (id 1)**, 08-30→09-02, 2 | `isError:true`: `Ingen tilgjengelig periode dekker 2026-08-30 til 2026-09-02` |

Rad 5 og 12 er de to observasjonene som førte til endringer; rad 2/4/7/9/13 er akseptkriteriet.

#### Er meldingen god nok for en LLM?

Meldingen er **handlingsbar som den er**: den oppgir både `N` (ledige) og `M` (forespurt), så
modellen kan foreslå et lavere antall uten et eneste nytt verktøykall. Ingen stacktrace, `isError:
true` i et `result` (mønsteret fra T-04), og innpakningslinja `Error invoking method: createBooking`
er den samme kosmetiske støyen som ellers. Men tre hull i *formidlingen* ble avdekket, og de er
tettet i `description` — ikke i logikken:

1. **`N = 0` gjør «foreslå færre reisende» meningsløst.** Den gamle teksten sa «foreslå færre
   reisende eller andre datoer» uten å skille. Nå står regelen eksplisitt: `N ≥ 1` → book N eller
   færre på samme datoer; `N = 0` → det hjelper ikke, foreslå andre datoer eller et annet reisemål.
2. **`check_availability` svarer på noe annet enn modellen tror.** Rad 5 over: `capacity: 3` mens
   0 plasser er ledige. Den gamle teksten sendte modellen dit for å «se periodenes kapasitet», som
   inviterer til nettopp den feilslutningen. Nå sier `create_booking` at `N` er fasiten for de
   datoene og at `check_availability` er til for å finne *andre* åpne perioder — og
   `check_availability` sier selv at `capacity` er totalen, ikke ledige plasser, med henvisning til
   hvor det ekte tallet kommer fra.
3. **Halvåpen-regelen var usynlig.** At utsjekksdagen er fri (rad 8 vs. 9) er den billigste
   løsningen når det er nesten fullt, men ingenting i katalogen fortalte modellen det. Nå står det
   i beskrivelsen, sammen med at et kortere opphold kan overlappe færre bookinger (punkt 4 over).

I tillegg presiserer beskrivelsen at **ingenting lagres** når kapasiteten sprekker (så et retry
ikke er farlig, bare nytteløst uten endring), og at skjøten mellom to perioder gir en *annen*
feilmelding enn kapasitet. `annotations` er uendret — T-11 legger ikke til et verktøy.

#### Verifisering

**1. `./gradlew build` er grønt** — 83 tester (76 fra før + 7 nye i `BookingToolsTest`, som nå har
35). De nye dekker: grensen nådd i **flere steg** (2 + 1 = 3 går gjennom, den fjerde plassen ikke);
innsjekk på en annen bookings utsjekksdag (ingen kollisjon) mot én dag tidligere (kollisjon);
at `CANCELLED` frigjør plassene mens raden blir liggende; at `CONFIRMED`/`PAID`/`COMPLETED`
fortsatt holder på dem; at summen tas over hele vinduet (to bookinger med luft mellom seg, ingen
enkeltdag over kapasitet, likevel avvist) og at `Math.max(remaining, 0)` skjuler det negative
tallet; at kapasiteten er per reisemål (Lofoten fullt påvirker ikke Kyoto); og at et opphold over
skjøten mellom to `availability`-rader avvises før kapasiteten regnes ut, mens begge sidene hver
for seg går fint. Grensetilfeller som allerede var dekket i T-07 (én over grensen, hele kapasiteten
i ett kall, ikke-overlappende datoer) er **ikke** duplisert.

**2. Gjennom protokollen** — tabellen over. Stderr bekreftet som vanlig registreringen:
`Tilgjengelige MCP-tools (9): [about_application, check_availability, create_booking, get_booking, list_bookings, update_booking_status, list_destinations, search_destinations, get_quote]`.

> **Røyktesten skriver til `vacation.db` i prosjektroten**, som i T-07–T-10. Fila ble kopiert før
> kjøringen og lagt tilbake etterpå (`select count(*) from bookings` er 0 igjen). Hjelpeskriptet lå
> i en scratchpad-katalog utenfor repoet.

### T-12 · `cancel_booking`

Femte og siste verktøy i `BookingTools`, og **siste oppgave i Epic 4**. Ingen nye filer:

| Fil | Endring |
|-----|---------|
| `src/main/java/no/computas/vacationmcp/tools/BookingTools.java` | nytt `@McpTool(name = "cancel_booking")` → `BookingService.cancel(id)`; `update_booking_status` og `list_bookings` fikk krysshenvisning til det; klasse-javadoc oppdatert (klassen er komplett) |
| `src/test/java/no/computas/vacationmcp/tools/BookingToolsTest.java` | 7 nye tester (kansellering fra hver ikke-terminal status, allerede kansellert, `COMPLETED`, ukjent id, frigjort kapasitet, og at de to veiene gir samme resultat) |

Metodekroppen er igjen **én linje**: `return bookings.cancel(id);`.

#### Er dette bare `update_booking_status(id, CANCELLED)`? Ja — les koden

Dette er det åpenbare spørsmålet, og svaret skal ikke pyntes på. `BookingService.cancel(...)`
er bokstavelig talt én setning:

```java
/** Kanseller en booking (frigjør kapasitet). */
public Booking cancel(long id) {
    return updateStatus(id, BookingStatus.CANCELLED);
}
```

Altså **samme metode**: samme tilstandsmaskin (`BookingStatus.canTransitionTo(...)`), samme
`UPDATE`, samme `ValidationException`/`NotFoundException` med ordrett samme tekster, samme
returverdi. Kaller du `cancel_booking(3)` eller `update_booking_status(3, CANCELLED)` er
databasen etterpå ikke til å skille fra hverandre — og heller ikke JSON-en klienten får. Det er
verifisert i test (`cancelBookingIsTheSameOperationAsUpdatingTheStatusToCancelled`), ikke antatt.

Så hvorfor et eget verktøy? **Ikke fordi det gjør noe annet, men fordi det er en annen oppføring
i katalogen.** To grunner, og begge handler om hva som er lettest å treffe riktig:

1. **Navnet er det sterkeste signalet modellen får** — sterkere enn prosa i `description`, som
   den kan lese fort eller hoppe over. «Kanseller booking 3» treffer `cancel_booking` direkte.
   Veien om `update_booking_status` krever ett steg til: å velge riktig verdi blant fem i
   enum-et. Og en bom der er ikke en feilmelding — det er en **annen lovlig endring**.
   `COMPLETED` på en `PAID`-booking er en gyldig overgang som stille markerer oppholdet som
   *gjennomført* i stedet for *avlyst*, og feilen oppdages først når kunden ikke får pengene
   igjen. `cancel_booking` har ett obligatorisk argument og ingen verdi å bomme på, så hele den
   feilklassen forsvinner. Skjemaene ved siden av hverandre gjør forskjellen konkret:

   ```jsonc
   // cancel_booking                 // update_booking_status
   "required": ["id"]                // "required": ["id", "status"]
   // properties: id                 // properties: id, status ("enum":[…5 verdier])
   ```

2. **`annotations` festes til verktøyet, ikke til argumentverdien.** Dette er det
   hint-mekanismen kan uttrykke som en generisk statusendring ikke kan. `update_booking_status`
   må bære **verste fall for alle fem verdiene**: det er `CANCELLED` som gjør at hintet må være
   `destructiveHint = true`, mens `PENDING → CONFIRMED` er ren, additiv framdrift. En host kan
   ikke sette regler per argumentverdi — den ser verktøynavnet og hint-blokken, ingenting annet.
   Med kanselleringen som egen oppføring kan hosten gate, logge eller kreve bekreftelse på
   *akkurat* den handlingen ved navn, i stedet for på «alt som kan skje med `status`-feltet».
   Merk at poenget ikke er at hint-**verdiene** blir andre — de er ordrett de samme som i T-09,
   se blokkene under — men **hvilket kall de henger på**.

At `CANCELLED` fortsatt er en lovlig verdi i `update_booking_status` er et bevisst valg:
enum-et kommer fra Java-typen `BookingStatus` (T-09), og å fjerne én konstant fra skjemaet ville
krevd en egen enum-type bare for MCP-laget. Duplisering av domenet, for en gevinst
`description` dekker billigere — den peker nå eksplisitt videre: «Skal du **kansellere**, bruk
`cancel_booking` i stedet.»

#### `annotations`: det mest destruktive verktøyet i settet

```jsonc
// cancel_booking (T-12)                       // update_booking_status (T-09)
"annotations": {                               // "annotations": {
  "title": "Kanseller booking",                //   "title": "Endre bookingstatus",
  "readOnlyHint": false,                       //   "readOnlyHint": false,
  "destructiveHint": true,                     //   "destructiveHint": true,
  "idempotentHint": true,                      //   "idempotentHint": true,
  "openWorldHint": false                       //   "openWorldHint": false
}                                              // }
```

Begge blokkene er hentet ordrett fra det samme `tools/list`-svaret, og de er **identiske** — som
de skal være, når operasjonen er den samme. Hint for hint:

- **`readOnlyHint = false`** — et `UPDATE` mot `bookings`. Hosten skal behandle kallet som en
  handling, ikke som et oppslag.
- **`destructiveHint = true`** — dette er det eneste verktøyet der alle tre pekene i
  spesifikasjonen peker samme vei samtidig: `status` **overskrives** (den forrige verdien er
  borte), `CANCELLED` er en **endestasjon** uten vei tilbake i tilstandsmaskinen, og handlingen
  **frigjør plassene til andre**. Det siste er verdt å dvele ved: selv om raden ligger igjen, kan
  effekten være umulig å reversere i praksis — er plassene tatt av noen andre i mellomtiden,
  hjelper det ikke å opprette en ny booking. Kontrasten er `create_booking` (T-07), som gjør et
  rent `INSERT` der ingenting går tapt, og der `false` er riktig svar.
- **`idempotentHint = true`** — spørsmålet oppgaven ber om å tenke gjennom: *hva skjer ved to
  kanselleringer på rad?* `BookingStatus.ALLOWED` gir `CANCELLED` et **tomt** sett med lovlige
  overganger, så heller ikke til seg selv. Kall nummer to slår derfor i tilstandsmaskinen og gir
  `Ulovlig statusovergang: CANCELLED -> CANCELLED`. Kallet **feiler**, men databasen er bit for
  bit den samme etter kall to som etter kall ett: ingen ekstra plasser frigjøres, ingen rad røres.
  Hintet handler om **effekten** av gjentatte kall, ikke om svaret — altså idempotent, og et
  retry etter timeout er trygt. Nøyaktig samme resonnement som i T-09, og motsatt av
  `create_booking`, der kall to gir en *ny* booking med ny id.
- **`openWorldHint = false`** — fortsatt bare vår egen SQLite-base. Ingen ekstern booking-partner
  å avbestille hos, ingen refusjon å be om.

`description` kompenserer for at hintene er *advisory* og at modellen ikke nødvendigvis ser dem:
at kanselleringen er endelig, at raden blir liggende i `list_bookings`, at plassene frigjøres —
og at en «Ulovlig statusovergang: CANCELLED -> CANCELLED» som regel betyr at det *første* kallet
gikk gjennom, så modellen skal bekrefte med `get_booking` framfor å prøve igjen.

#### Akseptkriteriet: frigjort kapasitet, verifisert gjennom protokollen

Røyktest mot `build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar` (håndtrykk som i T-00, deretter
`tools/list` og tolv `tools/call`). Reisemålet er **Kyoto Machiya (id 3)** — 1600/natt, én
periode `2026-10-01→2026-11-30`, **kapasitet 3**. T-11 gjorde det tilsvarende med
`update_booking_status`; her er det den dedikerte inngangen som frigjør plassene.

| # | Kall | Resultat |
|---|------|----------|
| 1 | `create_booking` Ola, 10-05→10-08, **3** | `isError:false`, `id: 1` — hele kapasiteten er brukt opp |
| 2 | `create_booking` Kari, 10-06→10-09, **2** | `isError:true`: **`Ikke nok kapasitet i perioden: 0 ledige plasser, 2 forespurt`** |
| 3 | **`cancel_booking` `{"id":1}`** | `isError:false`, hele bookingen tilbake med `"status":"CANCELLED"` |
| 4 | `create_booking` Kari, 10-06→10-09, **2** — **nøyaktig samme kall som #2** | `isError:false`, `id: 2`, `status: PENDING` ✅ |
| 5 | `cancel_booking` `{"id":1}` (igjen) | `isError:true`: `Ulovlig statusovergang: CANCELLED -> CANCELLED` — ingen ekstra effekt |
| 6 | `cancel_booking` `{"id":999}` | `isError:true`: `Fant ingen booking med id 999` |
| 7 | `cancel_booking` `{}` | `isError:true`: **skjemavalideringen**, `påkrevd egenskap 'id' ikke funnet` |
| 8 | `list_bookings` `{}` | `isError:false`, **begge** — den kansellerte ligger igjen med `CANCELLED` |
| 9–11 | `update_booking_status` id 2 → `CONFIRMED` → `PAID` → `COMPLETED` | alle `isError:false` |
| 12 | `cancel_booking` `{"id":2}` | `isError:true`: `Ulovlig statusovergang: COMPLETED -> CANCELLED` |

Rad 2 → 3 → 4 **er** akseptkriteriet, og de tre svarene ordrett fra tekstblokkene:

```jsonc
// 2) avvist mens det er fullt
{"content":[{"type":"text","text":"Error invoking method: createBooking\nIkke nok kapasitet i perioden: 0 ledige plasser, 2 forespurt"}],"isError":true}

// 3) cancel_booking — bare status er endret, resten er byte for byte som ved oppretting
{"id":1,"customerName":"Ola Nordmann","destinationId":3,"startDate":"2026-10-05","endDate":"2026-10-08","numTravelers":3,"totalPrice":14400.0,"status":"CANCELLED"}

// 4) samme forespørsel som i 2, nå godtatt
{"id":2,"customerName":"Kari Nordmann","destinationId":3,"startDate":"2026-10-06","endDate":"2026-10-09","numTravelers":2,"totalPrice":9600.0,"status":"PENDING"}
```

Mekanismen er `status <> 'CANCELLED'` i `BookingRepository.sumActiveTravelers` (kartlagt i
[T-11](#t-11--avvis-overbooking), punkt 2): plassene frigjøres i det statusen settes, uten at
raden slettes. Rad 8 viser begge halvdelene samtidig — den kansellerte bookingen er fortsatt i
lista, men teller ikke lenger med. Kontrollregning mot `data.sql`: Kyoto 1600/natt uten
sesongpris, `1600 × 3 netter × 3 reisende = 14 400` og `1600 × 3 × 2 = 9600`.

Stderr bekreftet registreringen — **ti** verktøy, og Epic 3–4 er dermed komplett:
`Tilgjengelige MCP-tools (10): [about_application, check_availability, create_booking, get_booking, list_bookings, cancel_booking, update_booking_status, list_destinations, search_destinations, get_quote]`.

> **Røyktesten skriver til `vacation.db` i prosjektroten**, som i T-07–T-11. Fila ble kopiert før
> kjøringen og lagt tilbake etterpå (`select count(*) from bookings` er 0 igjen). Hjelpeskriptet
> lå i en scratchpad-katalog utenfor repoet.

#### Verifisering i test

**`./gradlew build` er grønt** — 90 tester (83 fra før + 7 nye i `BookingToolsTest`, som nå har
42). De nye dekker: at kanselleringen lagres og at bare `status` er rørt; kansellering fra alle
tre ikke-terminale statusene gjennom det nye verktøyet; en allerede kansellert booking (avvist,
men bit for bit uendret — `idempotentHint = true` i praksis); en `COMPLETED` booking som ikke kan
avlyses; ukjent id; **akseptkriteriet** (fyll opp → avvist → `cancel_booking` → samme booking går
gjennom, og den kansellerte raden ligger fortsatt i `list_bookings`); og at de to veiene til
`CANCELLED` gir samme resultat. Testene fra T-09/T-11 som allerede kansellerer via
`update_booking_status` er **ikke** duplisert — de nye bruker med vilje den nye inngangen. Samme
opprydding som resten av klassen (`DELETE FROM bookings` i `@BeforeEach` + `@AfterEach`).

### T-13 · Destinasjoner som Resource

Første oppgave som ikke handler om verktøy. To nye filer, ingen endringer i eksisterende kode:

| Fil | Hva |
|-----|-----|
| `src/main/java/no/computas/vacationmcp/resources/DestinationResources.java` | `@Component` med **to** `@McpResource`: `destination://catalog` og `destination://{id}` |
| `src/test/java/no/computas/vacationmcp/resources/DestinationResourcesTest.java` | 7 tester (katalogens innhold, id-ene, prisformat, enkeltoppslag, ukjent id, ikke-numerisk id, statisk vs. mal) |

Ny pakke `resources/` ved siden av `tools/`. Grunnen er ikke ryddighet for ryddighetens skyld —
det er to forskjellige primitiver med to forskjellige brukere, og de har allerede ulikt
svarformat og ulik feilkanal (begge deler under).

#### Tools vs. resources — det du må ha med deg videre

Samme data (reisemålene) er nå eksponert to ganger, med vilje. Forskjellen ligger i *hvem som
bestemmer at innholdet skal inn i samtalen*:

| | Verktøy (`@McpTool`) | Ressurs (`@McpResource`) |
|---|---|---|
| Hvem velger å bruke den? | **Modellen**, midt i sin egen resonnering | **Applikasjonen eller mennesket** — hosten lister dem opp som vedlegg/@-nevninger, brukeren peker |
| Når havner innholdet i konteksten? | Etter et `tools/call`, som et svar modellen ba om | Før modellen har sagt noe — teksten legges rått inn |
| Adressering | Navn + argumenter validert av `inputSchema` | En **URI** (og eventuelle URI-variabler). Ikke noe skjema |
| Bivirkninger | Kan være skrivende (`create_booking`) | Alltid ren lesing — et `resources/read` skal ikke endre noe |
| Protokollmetoder | `tools/list`, `tools/call` | `resources/list`, `resources/templates/list`, `resources/read` |
| Feil | `result` med `isError: true` — *modellen* leser feilen og prøver på nytt | JSON-RPC `error` — *klientkoden* håndterer den |

En nyttig tommelfingerregel: **verktøy er verb, ressurser er substantiv.** «Søk etter reisemål
under 2000 kroner» er en handling modellen tar (verktøy). «Reisemålskatalogen» er et dokument
brukeren kan legge ved (ressurs). Derfor er det helt greit at `list_destinations` og
`destination://catalog` viser de samme radene — de brukes på hver sin måte.

> Merk at MCP-spesifikasjonen ikke *forbyr* en modell å lese ressurser; en host kan gi modellen
> tilgang til `resources/read`. Men designet skal ta utgangspunkt i at ressursen er
> applikasjonsstyrt kontekst, ikke et verktøykall.

#### Én annotasjon, to protokollister

`@McpResource` brukes helt likt i begge metodene. Det eneste som avgjør hvor oppføringen havner,
er om `uri` inneholder en `{variabel}` — se `SyncMcpResourceProvider`, som kjører den samme lista
med metoder to ganger og filtrerer på `McpPredicates.isUriTemplate(uri)`:

```jsonc
// resources/list — statiske ressurser, en ferdig liste klienten kan vise fram
{"jsonrpc":"2.0","id":2,"result":{"resources":[
  {"uri":"destination://catalog","name":"destination_catalog","description":"Hele katalogen over feriereisemål …","mimeType":"text/markdown"}]}}

// resources/templates/list — maler klienten selv må fylle inn
{"jsonrpc":"2.0","id":3,"result":{"resourceTemplates":[
  {"uriTemplate":"destination://{id}","name":"destination","description":"Ett enkelt reisemål slått opp på id …","mimeType":"text/markdown"}]}}
```

Legg merke til at feltet til og med *heter* noe annet: `uri` i den ene lista, `uriTemplate` i den
andre. En klient kan bla i `resources`-lista og la brukeren klikke; en mal må den derimot fylle
inn selv (eller be modellen om å gjøre det), og derfor er `description` på malen skrevet som en
bruksanvisning med et konkret eksempel (`destination://3`).

**Hva med `destination://catalog` — kunne den ikke også matchet malen `destination://{id}`?** Jo,
men statiske ressurser vinner: `McpAsyncServer.resourcesReadRequestHandler` prøver
`findResourceSpecification(uri)` *først*, og går bare videre til
`findResourceTemplateSpecification(uri)` hvis ingen statisk ressurs matcher. Verifisert —
`resources/read` på `destination://catalog` gir katalogen, ikke et forsøk på å slå opp et
reisemål med id-en «catalog».

**To attributter som ikke gjør noe i Spring AI 2.0.0:** `title()` og `annotations()` (`audience`,
`priority`, `lastModified`) på `@McpResource` leses aldri av `SyncMcpResourceProvider` — den
bygger `McpSchema.Resource` av `uri`, `name`, `description`, `mimeType` og `meta`. Satt og
verifisert: ingen av dem dukket opp i `resources/list`. Det klienten skal vise fram, må derfor
stå i `name`. (For `@McpTool` er `annotations` derimot ekte — se T-03/T-07.)

#### Innholdet: lesbar markdown, ikke JSON

`mimeType = "text/markdown"`, og metodene bygger teksten selv. Dette er **motsatt** av
[T-03](#t-03--list_destinations), der verktøyene returnerer domene-recorden og lar Spring AI
serialisere den til JSON. Avveiningen er en annen, og det er verdt å forstå hvorfor:

1. **Innholdet havner rått i konteksten.** Et verktøysvar er et mellomresultat modellen selv ba
   om og plukker felt fra; en ressurs blir liggende i prompten som lesestoff, ofte plassert der
   av et menneske som også ser den i klienten. Da er `- **Lofoten Rorbuer** (id 1) — Norge, 1850
   kr per natt. Tradisjonelle rorbuer …` bedre enn `{"id":1,"name":"Lofoten Rorbuer","country":…}`
   — for både mennesket og modellen, og med færre tokens brukt på gjentatte feltnavn.
2. **Det finnes ingen kontrakt å bryte.** Et verktøy har `inputSchema` (og eventuelt
   `outputSchema`); en ressurs har bare `mimeType`, som er et *renderingshint*. Ingen klient
   parser innholdet etter et skjema, så JSON kjøper deg ingen maskinlesbarhet noen faktisk bruker
   her — bare mer syntaks.
3. **Spring AI serialiserer ikke for deg likevel.** `SyncMcpResourceMethodCallback.validateReturnType`
   godtar bare `String`, `List<String>`, `ResourceContents`, `List<ResourceContents>` og
   `ReadResourceResult` — returnerer du en record, feiler serveren ved *oppstart*. Formateringen
   er altså din uansett; spørsmålet er bare om du skriver JSON eller prosa for hånd.

Det som *ikke* endres av valget: **`id` må stå i teksten.** Broa fra ressurs til verktøy er
id-en, og uten den kan ikke modellen gå videre til `check_availability`/`get_quote`/`create_booking`.
Derfor står den eksplisitt både i katalogen (`(id 1)`) og i enkeltoppslaget (`- **id:** 3 — bruk
denne i …`). Det er samme argument som i T-03, bare med et annet uttrykk.

Når ville `application/json` vært riktig? Hvis ressursen skal *konsumeres av kode* — en klient som
tegner et kart av reisemålene, eller en pipeline som differ katalogen mellom kjøringer. Da er
maskinlesbarhet poenget, og markdown blir tungvint. T-14 (bookinger) har samme profil som T-13 og
skal derfor bruke markdown; det står i de felles beslutningene over.

Den faktiske responsen på `resources/read`:

```jsonc
{"jsonrpc":"2.0","id":4,"result":{"contents":[{"uri":"destination://catalog","mimeType":"text/markdown","text":"# Reisemålskatalog\n\nKatalogen inneholder 5 reisemål (åpne for booking). Prisen er utgangspris per natt i norske kroner — for en konkret periode kan sesongpris gjelde, så bruk verktøyet `get_quote` før du oppgir en totalsum.\n\n- **Lofoten Rorbuer** (id 1) — Norge, 1850 kr per natt. Tradisjonelle rorbuer med utsikt over fjorden.\n- **Santorini Caldera** (id 2) — Hellas, 2400 kr per natt. Hvitkalkede suiter på kanten av vulkankrateret.\n- **Kyoto Machiya** (id 3) — Japan, 1600 kr per natt. Historisk bytownhouse nær tempeldistriktet.\n- **Toscana Agriturismo** (id 4) — Italia, 1400 kr per natt. Vingård og olivenlund i de toscanske åsene.\n- **Tromsø Nordlys-lodge** (id 5) — Norge, 2100 kr per natt. Lodge med glasstak for nordlysobservasjon.\n"}]}}

{"jsonrpc":"2.0","id":5,"result":{"contents":[{"uri":"destination://3","mimeType":"text/markdown","text":"# Kyoto Machiya\n\n- **id:** 3 — bruk denne i `check_availability`, `get_quote` og `create_booking`.\n- **Land:** Japan\n- **Pris per natt:** 1600 kr (utgangspris; sesongpris kan gjelde for en konkret periode)\n- **Åpent for booking:** ja\n\nHistorisk bytownhouse nær tempeldistriktet.\n"}]}}
```

Svaret er et `contents`-**array** (en ressurs kan bestå av flere deler), og hver del ekkoer `uri`
og `mimeType`. Merk at `uri` i svaret er den *forespurte* URI-en (`destination://3`), ikke malen.

#### Ukjent id: her ligner det *ikke* på T-04

Dette er den viktigste forskjellen å ha sett med egne øyne. Verktøyfeil er et **resultat**
(`isError: true`) fordi modellen skal lese dem; ressursfeil er en **JSON-RPC-`error`** fordi det
er applikasjonen som må håndtere dem. Faktiske svar:

```jsonc
// destination://999 — malen matcher, metoden vår kjører, NotFoundException bobler
{"jsonrpc":"2.0","id":6,"error":{"code":-32602,"message":"Error invoking resource method: destination in no.computas.vacationmcp.resources.DestinationResources. /nCause: Fant ikke reisemål med id 999. Gyldige id-er står i destination://catalog.","data":"Fant ikke reisemål med id 999. Gyldige id-er står i destination://catalog."}}

// destination://abc — «abc» når helt fram til metoden; vår egen parsing avviser den
{"jsonrpc":"2.0","id":7,"error":{"code":-32602,"message":"Error invoking resource method: destination in no.computas.vacationmcp.resources.DestinationResources. /nCause: «abc» er ikke en gyldig reisemål-id. URI-malen er destination://{id} der {id} er et heltall, f.eks. destination://3.","data":"…"}}

// booking://1 — ingen ressurs OG ingen mal matcher; her svarer SDK-en selv
{"jsonrpc":"2.0","id":8,"error":{"code":-32002,"message":"Resource not found","data":{"uri":"booking://1"}}}
```

Tre observasjoner:

1. **Ingen `isError`, ingen `content`.** `resources/read` har ikke den kanalen i det hele tatt.
   En klient som behandler alt med `error` som «kallet feilet» får altså riktig oppførsel gratis,
   men modellen ser ikke nødvendigvis feilen — det er hostens valg om den viderefører den.
2. **`-32602` (`INVALID_PARAMS`) er Spring AI sin innpakning**, på samme måte som
   «Error invoking method: …»-linja i [T-04](#t-04--search_destinations):
   `SyncMcpResourceMethodCallback.apply` fanger alt som kommer ut av metoden og bygger
   `McpError.builder(ErrorCodes.INVALID_PARAMS)` med rot-årsakens melding — både i `message`
   (etter en `/nCause:` som er en skrivefeil i biblioteket, ikke hos oss) og i `data`.
   Java-klassenavnet lekker, akkurat som metodenavnet gjorde i T-04. **Meldingen er derfor det
   eneste vi styrer** — og siden den leses av et menneske eller en klient, peker den videre til
   `destination://catalog` i stedet for bare å si «ikke funnet».
3. **Spesifikasjonens egen kode for dette er `-32002`** (`RESOURCE_NOT_FOUND`), og SDK-en bruker
   den selv når *ingen* ressurs eller mal matcher URI-en (`booking://1` over). Den samme koden
   for en ukjent id ville vært riktigere.

**Forsøket som ikke virker — verdt å kjenne til.** `SyncMcpResourceMethodCallback.apply` har en
gren som ser lovende ut:

```java
catch (Exception e) {
    if (e instanceof McpError mcpError && mcpError.getJsonRpcError() != null) {
        throw mcpError;      // slipp klientens egen feil rett gjennom
    }
    throw McpError.builder(ErrorCodes.INVALID_PARAMS) …
}
```

Fasiten prøvde derfor først å kaste `McpError.RESOURCE_NOT_FOUND.apply("destination://999")` fra
metoden. **Det ble ikke `-32002`.** Grunnen er at metoden kalles med refleksjon
(`this.method.invoke(...)`), så det som fanges er en `InvocationTargetException` — ikke vår
`McpError`. `instanceof`-sjekken bommer, og vi endte i `INVALID_PARAMS`-grenen likevel, nå med en
*dårligere* melding («Resource not found», uten id-en):

```jsonc
{"jsonrpc":"2.0","id":6,"error":{"code":-32602,"message":"Error invoking resource method: destination in … /nCause: Resource not found","data":"Resource not found"}}
```

Konklusjonen ble derfor å følge den etablerte konvensjonen — la exception-en boble, ingen
`try/catch` for feilkodens skyld — og heller investere i meldingen. Vil du virkelig ha `-32002`,
må du forbi annotasjonsmodellen og registrere en `SyncResourceSpecification`-bean med en lambda
som handler; da kalles den uten refleksjon, og et kastet `McpError` når fram. Det er utenfor
T-13.

#### Verifisering i test

**`./gradlew build` er grønt** — 97 tester (90 fra før + 7 nye i `DestinationResourcesTest`). MCP-
serveren er avskrudd i test, så ressursene testes som de Spring-beanene de er: at katalogen har
alle fem reisemålene med id, land, pris og beskrivelse; at prisen skrives «1850 kr» og ikke
«1850.0» (`double` → tekst er vår jobb nå); at enkeltoppslaget på `"3"` gir alle feltene; at
ukjent id gir `NotFoundException` med en melding som peker på katalogen; at `"abc"` gir
`ValidationException`; og — via refleksjon på annotasjonene — at katalog-URI-en er uten
`{variabel}` mens den andre har `{id}`, altså regelen som avgjør statisk vs. mal.

Testen dekker ikke selve protokoll-laget; det gjør røyktesten over. Ingen skriving mot databasen,
så ingen opprydding er nødvendig (i motsetning til booking-testene).

#### Fallgruver

| Symptom | Årsak | Fiks |
|---------|-------|------|
| Serveren feiler ved **oppstart** med «Method must return either ReadResourceResult, List\<ResourceContents\>, …» | Ressursmetoden returnerer en record/`List<Destination>` | Returner `String` (eller `ResourceContents`) — Spring AI serialiserer ikke for deg her |
| «URI variable parameters must be of type String» | Parameteren er `long id` | URI-variabler er alltid `String`; konverter i metoden |
| Ressursen dukker opp i feil liste | `uri` har (eller mangler) en `{variabel}` | `{}` ⇒ `resources/templates/list`, ellers `resources/list` |
| `title` vises ikke i klienten | `@McpResource.title()` leses ikke i Spring AI 2.0.0 | Legg visningsnavnet i `name` |
| `resources/list` er tom etter at du la til en `@McpResource` | Jar-en er et øyeblikksbilde | `./gradlew bootJar` og koble til på nytt (samme fallgruve som for verktøy) |
| Klienten viser ingenting selv om `resources/read` svarer | Ressurser er ikke automatisk med i konteksten | Bruker/host må velge dem — det er hele poenget med primitiven |

Oppstartsloggen (stderr) bekrefter registreringen ved siden av `RegisteredToolsLogger`, som bare
teller verktøy:

```
McpServerAutoConfiguration : Registered tools: 10
McpServerAutoConfiguration : Registered resources: 1
McpServerAutoConfiguration : Registered resource templates: 1
```

#### Røyktesten

Samme håndtrykk som i T-00, men med ressursmetodene i stedet for `tools/list` (hjelpeskriptet lå
i en scratchpad-katalog utenfor repoet):

```bash
{ … initialize … ; notifications/initialized ;
  {"jsonrpc":"2.0","id":2,"method":"resources/list","params":{}} ;
  {"jsonrpc":"2.0","id":3,"method":"resources/templates/list","params":{}} ;
  {"jsonrpc":"2.0","id":4,"method":"resources/read","params":{"uri":"destination://catalog"}} ;
  {"jsonrpc":"2.0","id":5,"method":"resources/read","params":{"uri":"destination://3"}} ;
  {"jsonrpc":"2.0","id":6,"method":"resources/read","params":{"uri":"destination://999"}} ;
  {"jsonrpc":"2.0","id":7,"method":"resources/read","params":{"uri":"destination://abc"}} ;
  {"jsonrpc":"2.0","id":8,"method":"resources/read","params":{"uri":"booking://1"}} ;
} | java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar > stdout.jsonl 2> stderr.log
```

Capability-blokken fra `initialize` er **uendret** fra T-00 (`"resources":{"subscribe":false,
"listChanged":true}`) — den lovet allerede at metodene fantes; det som endret seg er at listene
ikke lenger er tomme. Nøyaktig det poenget T-00 gjorde av capability-tabellen sin.
