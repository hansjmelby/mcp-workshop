package no.computas.vacationmcp.tools;

import java.time.LocalDate;
import no.computas.vacationmcp.domain.Booking;
import no.computas.vacationmcp.domain.BookingStatus;
import no.computas.vacationmcp.service.BookingService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP-verktøy for booking-arbeidsflyten (Epic 3–4 i {@code BACKLOG.md}).
 *
 * <p>Klassen er hjemmet til hele booking-domenet og vokser gjennom T-07–T-12:
 * {@code create_booking} (T-07), {@code get_booking} (T-08),
 * {@code update_booking_status} (T-09), {@code list_bookings} (T-10) og
 * {@code cancel_booking} (T-12). Alle går mot den samme {@link BookingService}-en, så
 * konstruktøren skal ikke trenge flere avhengigheter etter hvert som verktøyene kommer til.
 *
 * <p>Som de andre {@code *Tools}-klassene er dette en ren fasade: tjenesten validerer,
 * beregner pris, håndhever kapasitet og tilstandsmaskin. Verktøyene her legger ingen regler
 * oppå det, og fanger ingen exceptions — {@code ValidationException} og
 * {@code NotFoundException} får boble ut og blir et {@code CallToolResult} med
 * {@code isError: true} (mønsteret fra T-04).
 *
 * <p><b>Første skrivende verktøy.</b> {@code create_booking} endrer databasen, og
 * {@code annotations} er satt deretter — se javadoc-en på metoden og T-07-seksjonen i
 * {@code SOLUTION-STATUS.md}. De lesende verktøyene sine hint
 * ({@code readOnlyHint = true, idempotentHint = true}) skal <em>ikke</em> arves hit.
 *
 * <p><b>Hint hører til metoden, ikke klassen.</b> Fra og med T-08 er klassen blandet:
 * {@code get_booking} er et rent oppslag ({@code readOnlyHint = true},
 * {@code idempotentHint = true}), mens {@code create_booking} skriver. {@code annotations}
 * settes per {@code @McpTool}, og i protokollen er hvert verktøy sin egen oppføring i
 * {@code tools/list} — «skrivende klasse» finnes ikke som begrep for hosten. Å samle
 * booking-verktøyene i én klasse er derfor bare kodeorganisering (se «Struktur for
 * verktøyklasser»); hintene må vurderes på nytt for hver eneste metode, og et lesende verktøy
 * skal ikke arve nabometodens {@code readOnlyHint = false}.
 */
@Component
public class BookingTools {

    private final BookingService bookings;

    public BookingTools(BookingService bookings) {
        this.bookings = bookings;
    }

    /**
     * Oppretter en booking ved å delegere til
     * {@link BookingService#createBooking(String, long, LocalDate, LocalDate, int)}, som gjør
     * validering, prisberegning og kapasitetssjekk og lagrer med status
     * {@link BookingStatus#PENDING}.
     *
     * <p><b>Hintene, og hva de betyr for en host som spør brukeren om bekreftelse:</b>
     *
     * <ul>
     *   <li>{@code readOnlyHint = false} — verktøyet skriver en rad til {@code bookings} og
     *       beslaglegger kapasitet. Dette er hintet som faktisk avgjør om en host behandler
     *       kallet som en handling (be om bekreftelse, logge det) i stedet for et oppslag den
     *       kan gjøre fritt. De tre andre er bare meningsbærende når dette er {@code false}.
     *   <li>{@code destructiveHint = false} — spesifikasjonen skiller mellom
     *       <em>additive</em> og <em>destruktive</em> oppdateringer, ikke mellom «ufarlig» og
     *       «viktig». Et {@code INSERT} legger til en ny rad; ingenting overskrives eller
     *       slettes, ingen eksisterende booking endres, og angreknappen finnes
     *       ({@code cancel_booking}, T-12, frigjør kapasiteten igjen). Derfor {@code false} —
     *       men merk at det <em>ikke</em> betyr «kjør i vei uten å spørre»: det er
     *       {@code readOnlyHint = false} som ber hosten om å involvere brukeren.
     *   <li>{@code idempotentHint = false} — det viktigste hintet her. To identiske kall gir
     *       <em>to</em> bookinger med hver sin id, og dobbelt så mange plasser beslaglagt.
     *       Verktøyet har ingen nøkkel som lar serveren kjenne igjen et gjentatt kall. Hintet
     *       forteller hosten at et retry etter timeout ikke er trygt, og at modellen ikke skal
     *       «prøve igjen for sikkerhets skyld» når svaret ble borte.
     *   <li>{@code openWorldHint = false} — alt skjer mot vår egen SQLite-database med et
     *       lukket sett reisemål. Ingen betalingsleverandør, ingen ekstern booking-partner,
     *       ingen nettverkskall.
     * </ul>
     */
    @McpTool(
            name = "create_booking",
            title = "Opprett booking",
            description =
                    """
                    Oppretter en booking på et reisemål for et datointervall og et antall \
                    reisende. Dette er det første verktøyet som **endrer** noe: det lagrer en \
                    ny booking og beslaglegger plasser i perioden. Bekreft derfor reisemål, \
                    datoer, antall reisende og pris med brukeren før du kaller det, og kall \
                    det bare én gang per opphold — to like kall gir to bookinger.

                    Svaret er den lagrede bookingen: `id` (referansen brukeren skal ta vare \
                    på), `status` som alltid er `PENDING` ved oppretting, `totalPrice` i \
                    norske kroner regnet ut på samme måte som `get_quote`, pluss \
                    `customerName`, `destinationId`, `startDate`, `endDate` og `numTravelers` \
                    slik de ble lagret. Oppgi alltid id-en i svaret ditt til brukeren.

                    Verktøyet validerer alt selv og avviser med en forklarende feilmelding: \
                    ukjent reisemål, `from` etter `to`, færre enn 1 reisende, datoer som ingen \
                    **enkelt** tilgjengelighetsperiode dekker, tomt kundenavn — og for få \
                    ledige plasser («Ikke nok kapasitet i perioden: N ledige plasser, M \
                    forespurt»). Får du kapasitetsfeilen, er reisemålet delvis fullt: foreslå \
                    færre reisende eller andre datoer, og bruk `check_availability` for å se \
                    periodenes kapasitet. `get_quote` gir prisen uten å booke noe — bruk den \
                    når brukeren bare lurer på hva det koster.""",
            annotations =
                    @McpTool.McpAnnotations(
                            title = "Opprett booking",
                            readOnlyHint = false,
                            destructiveHint = false,
                            idempotentHint = false,
                            openWorldHint = false))
    public Booking createBooking(
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Navnet på kunden bookingen skal stå på, f.eks. «Ola \
                                    Nordmann». Spør brukeren om navnet — ikke finn på et. \
                                    Tomt navn eller bare mellomrom avvises.""")
                    String customerName,
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Id-en til reisemålet, slik den kommer fra \
                                    `list_destinations` eller `search_destinations`. En ukjent \
                                    id gir en feil, ikke et tomt svar.""")
                    long destinationId,
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Innsjekksdato på ISO-8601-formatet yyyy-MM-dd, f.eks. \
                                    «2026-07-01». Må være før til-datoen. Andre skrivemåter, \
                                    som «01.07.2026» eller «i morgen», avvises — regn om til \
                                    en konkret dato først.""")
                    LocalDate from,
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Utsjekksdato på ISO-8601-formatet yyyy-MM-dd, f.eks. \
                                    «2026-07-10». Må være etter fra-datoen. Datoen regnes som \
                                    utsjekksdag og faktureres ikke, så et opphold fra 1. til \
                                    10. er ni netter.""")
                    LocalDate to,
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Antall reisende, minst 1. Tallet påvirker både prisen og \
                                    hvor mange plasser som beslaglegges i perioden — spør \
                                    brukeren hvis det ikke er oppgitt, ikke gjett. 0 eller et \
                                    negativt tall avvises som feil.""")
                    int numTravelers) {
        // Ingen validering, kapasitetssjekk eller prisberegning her: BookingService gjør alt
        // tre, og kaster ValidationException/NotFoundException som får boble videre (T-04).
        return bookings.createBooking(customerName, destinationId, from, to, numTravelers);
    }

    /**
     * Slår opp én booking ved å delegere til {@link BookingService#get(long)}, som kaster
     * {@code NotFoundException} hvis id-en ikke finnes.
     *
     * <p><b>Hintene er de motsatte av nabometoden sine</b> — se klasse-javadoc-en. Et
     * {@code SELECT} endrer ingenting, så {@code readOnlyHint = true} og
     * {@code idempotentHint = true}; samme id gir samme svar helt til noen endrer bookingen med
     * et annet verktøy. {@code destructiveHint = false} og {@code openWorldHint = false} som
     * ellers (ingen data røres, alt ligger i vår egen SQLite-base). Dette er nøyaktig samme
     * blokk som de lesende verktøyene i T-03–T-06 har, og det er meningen: hintene beskriver
     * <em>metoden</em>, ikke klassen den tilfeldigvis bor i.
     *
     * <p>Svaret er hele {@link Booking}-recorden, uendret etter T-03-konvensjonen. Den bærer
     * allerede alt en modell trenger for å oppsummere bookingen — {@code status} og
     * {@code totalPrice} inkludert — så det finnes ingenting å mappe eller formatere her.
     */
    @McpTool(
            name = "get_booking",
            title = "Hent booking",
            description =
                    """
                    Henter én booking med id-en dens, slik den ser ut i databasen akkurat nå. \
                    Bruk verktøyet når brukeren spør om en booking hen allerede har — «hva er \
                    status på booking 3?», «hva kostet den?», «hvilke datoer var det?» — og \
                    alltid før du oppsummerer eller endrer en booking, så du refererer til \
                    lagrede tall og ikke til det som ble sagt tidligere i samtalen.

                    Svaret er hele bookingen: `id`, `customerName`, `destinationId`, \
                    `startDate`, `endDate`, `numTravelers`, `totalPrice` i norske kroner og \
                    `status` (`PENDING`, `CONFIRMED`, `PAID`, `COMPLETED` eller `CANCELLED`). \
                    Merk at `destinationId` er en id, ikke et navn — slå den opp med \
                    `list_destinations` hvis brukeren skal se hva reisemålet heter.

                    Verktøyet **endrer ingenting** og kan trygt kalles på nytt; det oppretter \
                    heller ingen booking. Bruk `create_booking` for å opprette en, og ta vare \
                    på `id`-en du får tilbake derfra — den er det eneste denne oppslaget \
                    godtar. Finnes ikke id-en, får du feilmeldingen «Fant ingen booking med id \
                    N»; da har du sannsynligvis gjettet på et nummer. Spør brukeren om \
                    referansen i stedet for å prøve deg fram med flere id-er.""",
            annotations =
                    @McpTool.McpAnnotations(
                            title = "Hent booking",
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    public Booking getBooking(
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Id-en til bookingen, slik den kom i `id`-feltet fra \
                                    `create_booking`. Et positivt heltall. En ukjent id gir en \
                                    feilmelding, ikke et tomt svar — ikke gjett.""")
                    long id) {
        // Ingen try/catch: NotFoundException fra tjenesten får boble ut og blir et
        // CallToolResult med isError: true og meldingen som tekst (mønsteret fra T-04).
        return bookings.get(id);
    }
}
