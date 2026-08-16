# Løsningsstatus — `solution`-branchen

Fasit for [BACKLOG.md](BACKLOG.md). Denne fila er **den delte statustavla**: hver oppgave
løses av en egen agent som leser denne fila først, implementerer, og oppdaterer raden sin
etterpå. Én commit per oppgave.

**Statuskoder:** ⬜ ikke startet · 🟡 pågår · ✅ ferdig · 📝 dokumentert (manuell/interaktiv oppgave) · ⏭️ hoppet over

## Oversikt

| Oppgave | Hva | Status | Leveranse |
|---------|-----|--------|-----------|
| T-00 | MCP-protokollen under panseret | ⬜ | — |
| T-01 | Bygg, kjør og inspiser skallet | ⬜ | — |
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
