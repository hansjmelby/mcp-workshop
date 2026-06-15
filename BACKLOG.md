# Backlog — Ferie-booking MCP-server

Dette er arbeidslista for workshopen. Skallet du har klonet **kjører allerede** og
eksponerer ett eksempel-verktøy (`about_application`). Jobben din er å plukke oppgaver
herfra, én etter én, og gradvis bygge ut MCP-serveren til en fungerende ferie-booking-tjeneste.

Hver oppgave har **Mål**, **Akseptkriterier**, **Hint** og **Slik tester du**. Ta dem
gjerne i rekkefølge — senere epics bygger på tidligere. Fasiten ligger på `solution`-branchen
(prøv selv først!).

> **Domenemodell** (allerede i `schema.sql` + `data.sql`):
> `destinations` (meny av reisemål) · `availability` (perioder + kapasitet + sesongpris) ·
> `bookings` (kunde, reisemål, datoer, antall reisende, totalpris, status).
> Status-flyt: `PENDING → CONFIRMED → PAID → COMPLETED` (+ `CANCELLED`).

---

## Epic 0 — Kom i gang

### T-01 · Bygg, kjør og inspiser skallet
- **Mål:** Få serveren opp og se eksempel-verktøyet i MCP Inspector.
- **Akseptkriterier:** `./gradlew build` er grønt; du ser `about_application` i Inspector og får svar når du kaller det.
- **Hint:** Start Inspector med `npx @modelcontextprotocol/inspector` og pek den på den kjørbare jar-en (`java -jar build/libs/vacation-booking-mcp-0.0.1-SNAPSHOT.jar`) som stdio-kommando.
- **Slik tester du:** Kall `about_application` fra Inspector og les svaret.

### T-02 · Koble serveren til Claude
- **Mål:** Bruke serveren fra en ekte LLM-host (Claude Desktop eller Claude Code).
- **Akseptkriterier:** Claude lister `about_application` og kan kalle det.
- **Hint:** Legg serveren inn som en stdio-MCP-server i host-konfigurasjonen (kommando = `java -jar …`). Se README for eksempel-config.
- **Slik tester du:** Spør Claude «hva er denne applikasjonen?» og se at den bruker verktøyet.

---

## Epic 1 — Utforsk destinasjoner (tools + DB)

### T-03 · `list_destinations`
- **Mål:** Et verktøy som returnerer alle tilgjengelige reisemål fra databasen.
- **Akseptkriterier:** Returnerer navn, land, pris pr. natt for alle `available = 1`.
- **Hint:** Injiser `JdbcTemplate`, skriv en `@McpTool`-metode. Returner gjerne en `record`-liste.
- **Slik tester du:** `tools/call list_destinations` gir 5 reisemål.

### T-04 · `search_destinations(query, country?, maxPricePerNight?)`
- **Mål:** Søk/filtrering med valgfrie parametre.
- **Akseptkriterier:** Filtrerer på fritekst i navn/beskrivelse, evt. land og makspris. Ugyldig input (negativ pris) avvises pent.
- **Hint:** Bruk `@McpToolParam(required = false, …)` for valgfrie felter. Valider før spørring.

---

## Epic 2 — Tilgjengelighet & pris

### T-05 · `check_availability(destinationId, from, to)`
- **Mål:** Vis ledige perioder og kapasitet for et reisemål.
- **Akseptkriterier:** Datoer valideres (ISO-format, `from < to`); returnerer overlappende `availability`-rader.
- **Hint:** Parse til `LocalDate`. Gi en tydelig feilmelding ved ugyldige datoer.

### T-06 · `get_quote(destinationId, from, to, numTravelers)`
- **Mål:** Beregn totalpris for et opphold.
- **Akseptkriterier:** Pris = (sesongpris ?: pris pr. natt) × netter × reisende. `numTravelers ≥ 1`. Avviser datoer utenfor tilgjengelig periode.

---

## Epic 3 — Booking-workflow

### T-07 · `create_booking(...)`
- **Mål:** Opprett en booking med full validering.
- **Akseptkriterier:** Validerer reisemål finnes/er tilgjengelig, datoer, antall reisende; beregner totalpris; lagrer med status `PENDING`; returnerer booking-id.
- **Hint:** Gjenbruk valideringen fra T-05/T-06.

### T-08 · `get_booking(id)` · **T-09** `update_booking_status(id, status)` · **T-10** `list_bookings(status?)`
- **Mål:** Lese, oppdatere og liste bookinger.
- **Akseptkriterier (T-09):** Statusendring følger tilstandsmaskinen — ulovlige overganger (f.eks. `COMPLETED → PENDING`) avvises.
- **Hint:** Modeller lovlige overganger eksplisitt (f.eks. et `Map<Status, Set<Status>>`).

---

## Epic 4 — Kapasitet

### T-11 · Avvis overbooking
- **Mål:** En booking kan ikke overstige `availability.capacity` for perioden.
- **Akseptkriterier:** Summen av aktive bookinger + ny booking ≤ kapasitet, ellers avvises.

### T-12 · `cancel_booking(id)`
- **Mål:** Kanseller en booking og frigjør kapasitet.
- **Akseptkriterier:** Setter status `CANCELLED`; frigjort kapasitet blir tilgjengelig igjen.

---

## Epic 5 — Resources & Prompts

### T-13 · Destinasjoner som **Resource**
- **Mål:** Eksponer katalogen og enkeltreisemål som MCP-ressurser (`destination://{id}`).
- **Hint:** Se Spring AI sine `@McpResource`-annotasjoner.

### T-14 · Bookinger som **Resource** (`booking://{id}`)

### T-15 · **Prompts**
- **Mål:** Gjenbrukbare prompt-maler, f.eks. «planlegg en ferie innen budsjett» og «reisesammendrag».
- **Hint:** `@McpPrompt`-annoterte metoder.

---

## Epic 6 — Rapport

### T-16 · `bookings_report`
- **Mål:** Oppsummer omsetning og belegg pr. reisemål/periode.

---

## Epic 7 — Remote / HTTP-transport

### T-17 · Bytt fra stdio til Streamable HTTP
- **Mål:** Kjør serveren som en stå-alene HTTP-tjeneste.
- **Hint:** Bytt starteren til `spring-ai-starter-mcp-server-webmvc` og sett `spring.ai.mcp.server.protocol=STREAMABLE`. Skru på web igjen (fjern stdio-spesifikk config).

### T-18 · Legg til bearer-token-auth
- **Mål:** Beskytt endepunktet med et enkelt token.

### T-19 · Koble Claude til remote-serveren
- **Mål:** Bruk HTTP-transporten fra hosten i stedet for stdio.

---

## Bonus

- **T-20 · Elicitation** — be brukeren bekrefte datoer/tillegg midt i en booking.
- **T-21 · Sampling** — la serveren be hosten foreslå «dagens anbefaling» basert på katalogen.
