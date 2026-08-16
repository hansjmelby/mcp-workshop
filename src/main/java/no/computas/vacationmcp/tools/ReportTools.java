package no.computas.vacationmcp.tools;

import java.time.LocalDate;
import no.computas.vacationmcp.service.BookingsReport;
import no.computas.vacationmcp.service.ReportingService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP-verktøy for rapportering (Epic 6 i {@code BACKLOG.md}) — foreløpig ett: {@code
 * bookings_report}.
 *
 * <p><b>Hvorfor en egen klasse?</b> Konvensjonen fra T-03 er én verktøyklasse per domeneområde, og
 * en rapport har ikke ett. Den leser reisemål, tilgjengelighet <em>og</em> bookinger og setter dem
 * sammen til et nytt tall som ikke finnes i noen av tabellene. Å legge den i {@code BookingTools}
 * ville gitt den klassen en avhengighet til to tjenester til, og navnet ville løyet: verktøyet
 * svarer ikke på spørsmål om <em>en</em> booking. Det er samme resonnement som gjorde
 * {@code VacationPrompts} til én klasse på tvers av domener i T-15.
 *
 * <p><b>Fasaden holder, selv om T-16 er den ene oppgaven med ny tjenestekode.</b> Definisjonene av
 * omsetning og belegg — hvilke statuser som teller, hva nevneren i belegget er, hvordan datofilteret
 * klipper — er forretningsregler og ligger i {@link ReportingService}. Denne klassen kaller
 * tjenesten og returnerer svaret. Ingen utregning, ingen mapping, ingen {@code try/catch}:
 * {@code ValidationException} (fra-dato ikke før til-dato) og {@code NotFoundException} (ukjent
 * reisemål) får boble ut og blir et {@code CallToolResult} med {@code isError: true}, etter
 * mønsteret fra T-04.
 *
 * <p><b>Definisjonene står også i {@code description}.</b> Det er ikke duplisering for
 * dupliseringens skyld: modellen ser aldri javadoc-en, og en rapport der «omsetning» og «belegg»
 * er udefinerte tall er verdiløs for den som skal forklare dem videre for et menneske. Feltnavnene
 * i {@link BookingsReport} gjør resten — hver rate leveres med teller og nevner ved siden av seg.
 */
@Component
public class ReportTools {

    private final ReportingService reporting;

    public ReportTools(ReportingService reporting) {
        this.reporting = reporting;
    }

    /**
     * Rapporterer omsetning og belegg ved å delegere til
     * {@link ReportingService#report(Long, LocalDate, LocalDate)}.
     *
     * <p><b>Tre valgfrie parametere</b>, etter mønsteret fra T-04: {@code required = false} på alle
     * tre (Spring AI har {@code PROPERTY_REQUIRED_BY_DEFAULT = true}, så det må sies eksplisitt),
     * og {@code destinationId} er en bokset {@link Long} slik at «ikke oppgitt» overlever som
     * {@code null} helt fram til tjenesten. Datoene er {@link LocalDate} etter T-05 — de blir
     * {@code {"type":"string","format":"date"}} i skjemaet, og {@code required} blir stående tomt.
     * Et kall uten argumenter er det vanligste og gir hele rapporten.
     *
     * <p>Hintene er de samme som for de andre lesende verktøyene: rapporten er rene
     * {@code SELECT}-er ({@code readOnlyHint = true}, {@code destructiveHint = false}), gjentatte
     * kall endrer ingenting ({@code idempotentHint = true}) og alt ligger i vår egen SQLite-base
     * ({@code openWorldHint = false}). Merk at {@code idempotentHint} handler om <em>effekten</em>
     * av kallet, ikke om svaret: rapporten endrer seg selvsagt når noen booker noe.
     */
    @McpTool(
            name = "bookings_report",
            title = "Rapport: omsetning og belegg",
            description =
                    """
                    Oppsummerer **omsetning** og **belegg** per reisemål og periode. Bruk \
                    verktøyet på spørsmål som «hvordan går det?», «hvilket reisemål tjener vi \
                    mest på?», «hvor fullt er det i juli?» eller «hvor mye mistet vi på \
                    avbestillinger?». Alle tre parameterne er valgfrie — kall det uten \
                    argumenter for hele bildet. Trenger du enkeltbookinger og ikke summer, bruk \
                    `list_bookings`; skal du vite om det er plass til en konkret booking, er det \
                    `check_availability` og `create_booking` som gjelder.

                    **Omsetning** (`revenue`) er summen av `totalPrice` i kroner for alle \
                    bookinger unntatt de kansellerte. `PENDING` er altså **med** — plassene er \
                    beslaglagt selv om bookingen ikke er bekreftet — men den delen skilles ut i \
                    `pendingRevenue` (en delmengde av `revenue`, ikke et tillegg), så du kan si \
                    hvor mye av omsetningen som ennå er usikker. `CANCELLED` teller **ikke** med \
                    i `revenue`, men rapporteres for seg i `cancelledBookings` og \
                    `cancelledRevenue`. Legg aldri `cancelledRevenue` til `revenue`.

                    **Belegg** (`occupancyRate`) måles i **plassdøgn**: \
                    `bookedNights / capacityNights`, der `capacityNights` er periodens kapasitet \
                    (antall samtidige plasser) ganget med antall netter i perioden, og \
                    `bookedNights` er summen av reisende × netter for de samme bookingene som \
                    teller i omsetningen. Tallet er en brøk mellom 0 og 1 — 0.4167 betyr 41,67 % \
                    — og begge leddene ligger ved siden av, så regnestykket kan gjengis. \
                    `occupancyRate: null` betyr at det ikke finnes noen åpen periode å måle mot, \
                    ikke at det er tomt. Merk at belegg **ikke** er «antall bookinger av \
                    kapasiteten»: en periode på to måneder med kapasitet 3 tar imot langt flere \
                    enn 3 reisende i løpet av perioden.

                    Svaret gjentar filtrene (`from`, `to`, `destinationId`; `null` = ikke \
                    filtrert), har `totals` for hele utvalget og én linje per reisemål i \
                    `perDestination` — **sortert på omsetning, høyest først** — med navn, land, \
                    de samme tallene og én linje per tilgjengelighetsperiode i `periods`. \
                    Reisemål uten bookinger står med nuller; det er et svar, ikke et hull.

                    Filtrerer du på dato, virker `from`/`to` ulikt på de to tallene: en booking \
                    teller med hvis den overlapper vinduet, og da med **hele** beløpet (omsetning \
                    fordeles ikke på netter), mens belegget klippes i begge ender — bare netter \
                    inne i vinduet telles, mot kapasiteten i den delen av perioden som ligger \
                    inne i vinduet. Et smalt vindu rundt et opphold gir derfor full omsetning, \
                    men bare de nettene som er inni.""",
            annotations =
                    @McpTool.McpAnnotations(
                            title = "Rapport: omsetning og belegg",
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    public BookingsReport bookingsReport(
            @McpToolParam(
                            required = false,
                            description =
                                    """
                                    Valgfritt filter: rapporter bare dette ene reisemålet, med \
                                    id fra `list_destinations` eller `search_destinations`. \
                                    **Utelat parameteren** for å få alle reisemål — det er den \
                                    vanlige bruken, og lista er sortert slik at det største står \
                                    øverst. En ukjent id gir feilmeldingen «Fant ingen \
                                    destinasjon med id N», ikke en tom rapport.""")
                    Long destinationId,
            @McpToolParam(
                            required = false,
                            description =
                                    """
                                    Valgfri startdato for perioden det rapporteres på, på \
                                    ISO-8601-formatet yyyy-MM-dd, f.eks. «2026-07-01». Datoen er \
                                    inklusiv. Utelat den for «fra tidenes morgen». Oppgir du både \
                                    `from` og `to`, må `from` være først. Andre skrivemåter, som \
                                    «01.07.2026» eller «i sommer», avvises — regn om til en \
                                    konkret dato først.""")
                    LocalDate from,
            @McpToolParam(
                            required = false,
                            description =
                                    """
                                    Valgfri sluttdato for perioden det rapporteres på, på \
                                    ISO-8601-formatet yyyy-MM-dd, f.eks. «2026-08-01». Datoen er \
                                    **eksklusiv**, som en utsjekksdag: et vindu fra 1. juli til \
                                    1. august dekker hele juli og ingen netter i august. Utelat \
                                    den for «uten øvre grense».""")
                    LocalDate to) {
        // Ingen utregning her: ReportingService eier definisjonene av omsetning og belegg, og
        // kaster ValidationException/NotFoundException som får boble videre (T-04).
        return reporting.report(destinationId, from, to);
    }
}
