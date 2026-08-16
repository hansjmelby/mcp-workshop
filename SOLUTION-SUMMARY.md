# Løsningssammendrag

Kortversjonen av fasiten: hva hver oppgave i [BACKLOG.md](BACKLOG.md) krevde, og hvilke
fallgruver som ligger begravd i den. Detaljene — full protokoll-JSON, designvalg og
begrunnelser — ligger i [SOLUTION-STATUS.md](SOLUTION-STATUS.md).

**Resultat:** 13 verktøy, 3 ressurser, 2 prompts, 155 tester. stdio som default,
Streamable HTTP med bearer-token bak `--spring.profiles.active=http`.

---

## Epic 0 — MCP under panseret

### T-00 · Se protokollen før annotasjonene
Kjør røyktesten og les tracen: `initialize` → `notifications/initialized` → `tools/list`.

- `notifications/initialized` har ingen `id` — i JSON-RPC 2.0 er `id` nettopp det som skiller
  en request fra en notifikasjon, så det finnes ingenting å korrelere et svar mot. Derfor
  trenger røyktesten `sleep`: det finnes ikke noe svar å vente på.
- `about_application` har tomme `properties`/`required` fordi metoden er parameterløs.
  Feltene utelates ikke — `{"properties":{},"required":[]}` *er* skjemaet for «objekt uten felt».
- Serveren annonserer fem capabilities, men bare `tools` har innhold. Spring AI slår dem på
  uavhengig av om noe er annotert — blokken sier hvilke metodefamilier serveren svarer på.

> **Fallgruve:** all logging må til stderr. Ett `System.out.println` i verktøykode korrumperer
> protokollen.

### T-01 · Bygg og inspiser
`./gradlew build`, så MCP Inspector mot jar-en.

> **Fallgruver:** åpne *akkurat* URL-en med auth-tokenet, ellers «connection error» · kjør fra
> prosjektroten, så den relative jar-stien stemmer · bruk fat-jar-en, ikke `-plain.jar` ·
> Inspector v2 krever Node ≥ 22.19.0 · nye verktøy vises først etter rebuild.

### T-02 · Koble til Claude
Registrer som stdio-server: `claude mcp add-json` eller `claude_desktop_config.json`.

> **Fallgruver:** Claude Desktop starter jar-en fra sin egen arbeidskatalog — relativ sti gir
> «server disconnected» uten forklaring, så bruk absolutt sti · hosten holder serverprosessen
> fra oppstart, så `./gradlew clean` river bort fila og krever reconnect (`/mcp`, eller
> restart av Desktop).

---

## Epic 1 — Utforsk destinasjoner

### T-03 · `list_destinations`
`@Component` med `@McpTool` som delegerer til `DestinationService.listAvailable()`.
Etablerte strukturen resten arvet: én verktøyklasse per domeneområde, domene-records som
returtype (Spring AI JSON-serialiserer alt som ikke er `String`).

> **Fallgruve:** sett `annotations` eksplisitt. Spring AI-defaulten annonserer et lesende
> verktøy som `destructiveHint: true`.

### T-04 · `search_destinations(query?, country?, maxPricePerNight?)`
Tre valgfrie parametere, delegert til `DestinationService.search(...)`.
Etablerte feilhåndteringen: la exception-en boble — Spring AI fanger den og gir
`isError: true` uten stacktrace.

> **Fallgruver:** parametere er **obligatoriske som default** i Spring AI — valgfrie krever
> eksplisitt `@McpToolParam(required = false)` · bruk `Double`, ikke `double`, ellers
> kollapser «ikke oppgitt» til 0.

---

## Epic 2 — Tilgjengelighet & pris

### T-05 · `check_availability(destinationId, from, to)`
`LocalDate` som parametertype gir `{"type":"string","format":"date"}` — det beste skjemaet man
kunne skrevet for hånd. Svaret pakkes i en konvolutt med `matchingPeriods`, siden en tom liste
alene er tvetydig for en modell.

> **Fallgruve:** `format: "date"` **håndheves ikke** — JSON Schema behandler `format` som en
> annotasjon, så `"01.07.2026"` slipper forbi validatoren og stoppes først av Jackson.

### T-06 · `get_quote(destinationId, from, to, numTravelers)`
Ren delegering til `PricingService.quote(...)`. `Quote` returneres uendret — den har allerede
både brukt pris og ordinær pris, så modellen ser selv om sesongpris gjaldt.
Første ikke-tomme `required`, og den *håndheves* av validatoren.

> **Fallgruve:** `get_quote` krever at **én** periode dekker hele oppholdet, mens
> `check_availability` bare krever overlapp. To tilstøtende perioder slås ikke sammen.

---

## Epic 3 — Booking-workflow

### T-07 · `create_booking(...)`
Første skrivende verktøy. Ren delegering; validering, kapasitet og pris ligger i tjenesten.

> **Fallgruve — hintene betyr noe presist:** `destructiveHint: false` selv om verktøyet
> skriver, fordi en INSERT er *additiv* (spesifikasjonen spør additiv vs. destruktiv, ikke
> «ufarlig vs. viktig»). `idempotentHint: false`, fordi to like kall gir to bookinger.

### T-08 · `get_booking(id)`
Lesende verktøy i en ellers skrivende klasse.

> **Fallgruve:** `annotations` settes **per metode, ikke per klasse**. Hosten ser aldri hvilken
> Java-klasse verktøyet kom fra — «skrivende klasse» finnes ikke som begrep.

### T-09 · `update_booking_status(id, status)`
`BookingStatus` som parametertype gir `"enum": ["PENDING", …]` i skjemaet, og verdiene
håndheves av validatoren. Nivåene er: `String` = ingenting, `format` = hint,
`enum` = håndhevet kontrakt.

> **Fallgruver:** `enum` er case-sensitiv (`"confirmed"` avvises) · feilmeldingen navngir
> begge statusene, men ikke hva som *er* lovlig — legg tilstandsmaskinen i `description` ·
> `destructiveHint: true` her, i motsetning til T-07, fordi en UPDATE overskriver.

### T-10 · `list_bookings(status?)`
Kombinerer T-04 og T-09: `enum`-lista kommer av Java-typen, tom `required` av
`@McpToolParam(required = false)`. Bar `List<Booking>` — T-05-konvolutten trengs ikke, siden
det ikke finnes noen tvetydig tom-tilstand.

> **Merk:** et enum er en referansetype, så `null` overlever til tjenesten uten
> `Double`-trikset fra T-04.

---

## Epic 4 — Kapasitet

### T-11 · Avvis overbooking
Verifikasjonsoppgave — logikken lå ferdig. Kapasitetsregelen, presist:

1. Kapasiteten kommer fra **én** `availability`-rad — summeres aldri på tvers.
2. Alle statuser unntatt `CANCELLED` teller; `COMPLETED` holder fortsatt på plassene sine.
3. Overlapp er **halvåpent** — utsjekksdagen er fri, men én felles natt trekker fra *hele*
   den andre bookingens `numTravelers`.
4. Summen tas over **hele vinduet, ikke per dag** → konservativ: den slipper aldri gjennom en
   overbooket dag, men kan avvise et opphold som strengt tatt hadde fått plass.

> **Fallgruve:** `check_availability` viser periodens **totale** `capacity`, også når 0 er
> ledige. Å sende modellen dit for å «se kapasiteten» er misvisende.

### T-12 · `cancel_booking(id)`
Funksjonelt identisk med `update_booking_status(id, CANCELLED)` — `cancel(id)` er bokstavelig
talt et kall videre. Begrunnelsen for et eget verktøy ligger i katalogen, ikke i oppførselen:
en bom på enum-verdien er ikke en feilmelding, men *en annen lovlig endring*, og `annotations`
festes til verktøyet, ikke til argumentverdien.

> **Merk:** `idempotentHint: true` selv om kall nummer to feiler — hintet gjelder *effekten*,
> og databasen er identisk etterpå, så retry er trygt.

---

## Epic 5 — Resources & Prompts

### T-13 · Destinasjoner som Resource
`destination://catalog` (statisk) + `destination://{id}` (mal). Samme annotasjon begge steder —
det eneste som avgjør hvilken liste de havner i er om `uri` inneholder en `{variabel}`.
Innhold: `text/markdown`, motsatt av T-03s JSON-valg, fordi ressursinnhold legges **rått i
konteksten** og leses av både menneske og modell.

> **Fallgruver:** `resources/read` har ingen `isError`-kanal — feil blir en ekte JSON-RPC-`error`
> (`-32602`) · `@McpResource` godtar bare `String`/`ResourceContents`/`ReadResourceResult`; en
> record feiler ved oppstart · `title()` og `annotations()` leses ikke i Spring AI 2.0.0 ·
> `McpError` overlever ikke refleksjonskallet (pakkes i `InvocationTargetException`).

### T-14 · Bookinger som Resource
`booking://{id}` med reisemålets navn slått opp, så ressursen står på egne bein.
**Ingen** liste-ressurs: ressursinnhold er et øyeblikksbilde uten holdbarhetsdato, og en liste
som sier `PENDING` etter en kansellering er verre enn ingen liste. `list_bookings` dekker det.

### T-15 · Prompts
To prompts: `plan_vacation_within_budget` (en arbeidsflyt uttrykt som tekst — rekkefølgen på
verktøykallene finnes ikke noe sted i `tools/list`) og `travel_summary` (bookingen vedlagt som
`EmbeddedResource`, gjenbrukt fra T-14).

> **Fallgruver:** annotasjonen heter **`@McpArg`**, ikke `@McpToolParam` · `required()` har
> default **`false`** — stikk motsatt av tools · `required` håndheves av **ingen**, så valider
> selv · `String`-retur pakkes med rollen `assistant`, som om modellen allerede hadde sagt
> teksten — bruk `GetPromptResult` med `Role.USER` · prompt-argumenter konverteres bare til
> `String`/`Integer`/`Long`/`Double`/`Boolean`, så `LocalDate` fra T-05 virker **ikke** her.

**De tre primitivene:** tools → modellen bestemmer når de kalles. Resources → applikasjonen
eller brukeren velger hva som legges i konteksten. Prompts → brukeren velger eksplisitt, typisk
fra en meny.

---

## Epic 6 — Rapport

### T-16 · `bookings_report`
Den ene oppgaven som krevde ny kode i tjenestelaget (`ReportingService`), aggregert i Java over
eksisterende repository-metoder.

- **Omsetning** = alt unntatt `CANCELLED`. `PENDING` teller med, så omsetning og belegg teller
  nøyaktig samme sett bookinger som holder på kapasiteten.
- **Belegg** = plassdøgn, `bookedNights / capacityNights`.

> **Fallgruve:** den nærliggende definisjonen «reisende / capacity» gir over 400 %, fordi
> `capacity` er *samtidige* plasser — ikke en kvote for hele perioden.

---

## Epic 7 — Remote / HTTP

### T-17 · Streamable HTTP
`spring-ai-starter-mcp-server-webmvc` ved siden av stdio-starteren; transporten velges av
profilen `http`. Endepunkt: `POST/GET/DELETE http://localhost:8080/mcp`.

> **Fallgruver:** `spring.ai.mcp.server.protocol=STREAMABLE` er **obligatorisk** — uten linja
> matcher SSE-betingelsen (`matchIfMissing=true`) og du havner stille på den deprecated
> `/sse`-transporten · `spring.main.web-application-type=none` må inn *også* i
> `src/test/resources/application.properties`, som skygger for hovedfila · `Accept` må ha
> **begge** typene (`application/json, text/event-stream`), ellers `400` · sesjons-id-en er en
> **header** (`Mcp-Session-Id`), ikke et JSON-felt · alt etter håndtrykket er `text/event-stream`.

### T-18 · Bearer-token
Eget `OncePerRequestFilter` framfor Spring Security — de tre første tingene en deltaker ellers
måtte lære var å skru *av* defaults (CSRF blokkerer `POST /mcp` med 403, formLogin, generert
passord i loggen). Uten konfigurert token genererer serveren et UUID og logger det som WARN,
så det finnes ingen ubeskyttet tilstand.

> **Merk:** et statisk delt token er ikke OAuth — ingen bruker, scopes eller utløp.
> MCP-spesifikasjonen har en egen autorisasjonsmodell for remote-servere.

### T-19 · Koble Claude til remote
`claude mcp add --transport http <navn> <url> --header "Authorization: Bearer …"`.

> **Fallgruve:** glemmer du `--header`, tolker hosten `401` som en OAuth-utfordring og sonderer
> `.well-known`-endepunkter før den gir opp med «Dynamic Client Registration rejected».
>
> **Nyttig:** `${MILJØVARIABEL}` i header-verdien ekspanderes, så oppsettet kan deles via
> `.mcp.json` uten å committe tokenet. Claude Desktop tar ikke remote-servere i JSON-fila —
> det går via Settings → Connectors, som forventer OAuth; omveien er `mcp-remote` som bro.

---

## Bonus

### T-20 · Elicitation
En `McpSyncRequestContext`-parameter (som Spring AI fyller inn selv og holder utenfor
`inputSchema`) gjør at serveren kan spørre **brukeren** midt i et verktøykall.
`create_booking_interactive` henter tilbud, ber om bekreftelse, og booker først etterpå.

> **Fallgruver:** sjekk `ctx.elicitEnabled()` først — uten sjekken kaster Spring AI en
> `IllegalStateException` med et Java-klassenavn ut til modellen · alle tre utfall må håndteres
> (`accept`/`decline`/`cancel`) · tidsavbruddet er 20 s (`spring.ai.mcp.server.request-timeout`),
> for kort for et menneske · en ikke-interaktiv `claude -p`-økt kan ikke svare og avviser med
> `cancel` · virker bare i stateful server (stdio / `STREAMABLE`).

### T-21 · Sampling
Samme mekanisme, men mottakeren er **modellen**: `ctx.sample(...)` låner hostens LLM.
Den arkitektoniske innsikten: serveren har ingen API-nøkkel, ingen leverandørbinding, og
tokenene betales av den som eier samtalen — som også er grunnen til at hosten skal kunne si nei.

> **Fallgruver:** `maxTokens` er obligatorisk — utelatt fylles den med 500 · `modelHints` er
> *familienavn* («haiku»), ikke modell-id-er · sampling har **ingen `decline`** — en host som
> avviser har bare JSON-RPC-`error` · **Claude Code annonserer ikke `sampling`-capability**,
> så fallback-veien er ikke teoretisk.

---

## Mønstre som gjelder overalt

| Tema | Regel |
|------|-------|
| Struktur | Én verktøyklasse per domeneområde; klassen er ren fasade, tjenesten eier logikken |
| Retur (tools) | Domene-record — Spring AI serialiserer alt som ikke er `String` til JSON |
| Retur (resources) | `text/markdown`; innholdet leses rått av både menneske og modell |
| Feil (tools) | La exception-en boble → `isError: true`, ingen `try/catch` i verktøylaget |
| Feil (resources) | Ingen `isError`-kanal — blir JSON-RPC-`error`, så invester i meldingen |
| Datoer | `LocalDate` i tools (gir `format: "date"`), `String` i prompts (konverteres ikke) |
| Valgfritt | `@McpToolParam(required = false)` i tools; `@McpArg` er allerede `false` som default |
| `annotations` | Per metode, aldri per klasse; beskriver effekten på verden, ikke samhandlingen |
| `description` | Prompt-engineering. Krysshenvis til naboverktøy — men aldri til noe som ikke finnes ennå |
