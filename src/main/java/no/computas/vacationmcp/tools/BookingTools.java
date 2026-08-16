package no.computas.vacationmcp.tools;

import java.time.LocalDate;
import java.util.List;
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
                    heller ingen booking, og det flytter ingen status — bruk \
                    `update_booking_status` til det. Bruk `create_booking` for å opprette en, og \
                    ta vare på `id`-en du får tilbake derfra — den er det eneste denne oppslaget \
                    godtar. Finnes ikke id-en, får du feilmeldingen «Fant ingen booking med id \
                    N»; da har du sannsynligvis gjettet på et nummer. Kjenner du ikke id-en, \
                    kall `list_bookings` og finn bookingen i lista, eller spør brukeren om \
                    referansen — ikke prøv deg fram med flere id-er her.""",
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

    /**
     * Flytter en booking til en ny status ved å delegere til
     * {@link BookingService#updateStatus(long, BookingStatus)}. Tilstandsmaskinen ligger i
     * {@link BookingStatus#canTransitionTo(BookingStatus)} — verktøyet kjenner ingen regler selv
     * og gjør ingen sjekk før kallet.
     *
     * <p><b>Enum-et krysser MCP-grensen som en enum.</b> Parameteren er {@link BookingStatus},
     * ikke {@code String}, og det er hele poenget med T-09: Spring AI utleder
     * {@code {"type":"string","enum":["PENDING","CONFIRMED","PAID","COMPLETED","CANCELLED"]}} i
     * {@code inputSchema}, så modellen får de gyldige verdiene maskinlesbart — og en ukjent verdi
     * («BANANA») stoppes av <em>skjemavalideringen</em>, før metoden kalles. Det er en sterkere
     * kontrakt enn {@code format: "date"} fra T-05, som bare er en annotasjon validatoren ser bort
     * fra. Med {@code String} hadde vi fått et bart {@code {"type":"string"}} og måtte skrevet
     * verdiene i prosa. Se T-09-seksjonen i {@code SOLUTION-STATUS.md} for den faktiske JSON-en.
     *
     * <p><b>Hintene — sammenlign med {@code create_booking}, som svarer motsatt på to av dem:</b>
     *
     * <ul>
     *   <li>{@code readOnlyHint = false} — verktøyet gjør et {@code UPDATE} mot {@code bookings}.
     *       Samme sak som for {@code create_booking}: hosten skal behandle kallet som en handling,
     *       ikke som et oppslag.
     *   <li>{@code destructiveHint = true} — <em>her</em> skiller det seg fra
     *       {@code create_booking}. Det verktøyet gjør et {@code INSERT}: rent additivt, ingenting
     *       går tapt. Dette verktøyet <em>overskriver</em> {@code status}-feltet på en rad som
     *       allerede finnes; den forrige statusen er borte etterpå, og tilstandsmaskinen er
     *       enveiskjørt — {@code COMPLETED} og {@code CANCELLED} er terminale, så det finnes ingen
     *       vei tilbake. {@code CANCELLED} avlyser i tillegg en reell booking og frigjør plassene
     *       til andre. Spesifikasjonen spør nettopp om oppdateringen er additiv eller destruktiv,
     *       og svaret her er destruktiv.
     *   <li>{@code idempotentHint = true} — også motsatt av {@code create_booking}. Hintet handler
     *       om <em>effekten</em> av gjentatte kall, ikke om svaret: to like kall etter hverandre
     *       gir ikke to statusendringer. Det første flytter bookingen, det andre avvises av
     *       tilstandsmaskinen (en overgang til seg selv er ikke lov), og databasen er den samme
     *       etter kall to som etter kall ett. Et retry etter timeout kan altså ikke «dobbelt-flytte»
     *       en booking fra {@code PENDING} til {@code PAID}. Merk konsekvensen for modellen: et
     *       retry som svarer «Ulovlig statusovergang: CONFIRMED -&gt; CONFIRMED» betyr som regel at
     *       det <em>første</em> kallet gikk gjennom — bekreft med {@code get_booking} i stedet for
     *       å konkludere med at endringen feilet.
     *   <li>{@code openWorldHint = false} — fortsatt bare vår egen SQLite-base.
     * </ul>
     */
    @McpTool(
            name = "update_booking_status",
            title = "Endre bookingstatus",
            description =
                    """
                    Flytter en eksisterende booking til en ny status i livssyklusen. Verktøyet \
                    **endrer** databasen og overskriver den statusen bookingen har nå — bekreft \
                    med brukeren før du kaller det, og slå gjerne opp bookingen med \
                    `get_booking` først for å se hvilken status den faktisk står i.

                    Lovlige overganger (alt annet avvises):

                    - `PENDING` → `CONFIRMED` eller `CANCELLED`
                    - `CONFIRMED` → `PAID` eller `CANCELLED`
                    - `PAID` → `COMPLETED` eller `CANCELLED`
                    - `COMPLETED` og `CANCELLED` er **endestasjoner** — derfra går det ingen vei \
                    videre, heller ikke tilbake

                    Kjeden går altså bare framover, ett steg om gangen: skal en `PENDING`-booking \
                    bli `PAID`, må du først sette den til `CONFIRMED`. Det finnes ingen overgang \
                    fra en status til seg selv, så et gjentatt kall med samme verdi avvises — det \
                    betyr som regel at det forrige kallet gikk gjennom, ikke at noe er galt. \
                    Sjekk med `get_booking` framfor å prøve på nytt.

                    Svaret er hele den oppdaterte bookingen, med `status` satt til den nye \
                    verdien og resten av feltene uendret. Ulovlige overganger avvises med \
                    «Ulovlig statusovergang: FRA -> TIL», og en ukjent id med «Fant ingen booking \
                    med id N». Ingen av delene endrer noe i databasen.""",
            annotations =
                    @McpTool.McpAnnotations(
                            title = "Endre bookingstatus",
                            readOnlyHint = false,
                            destructiveHint = true,
                            idempotentHint = true,
                            openWorldHint = false))
    public Booking updateBookingStatus(
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Id-en til bookingen som skal endres, slik den kom fra \
                                    `create_booking` eller `get_booking`. En ukjent id gir en \
                                    feilmelding, og ingenting endres — ikke gjett.""")
                    long id,
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Statusen bookingen skal flyttes til. Må være én av de fem \
                                    verdiene i skjemaet, skrevet med store bokstaver. Overgangen \
                                    må være lovlig fra statusen bookingen står i nå: `CONFIRMED` \
                                    bekrefter en `PENDING`-booking, `PAID` markerer en \
                                    `CONFIRMED`-booking som betalt, `COMPLETED` avslutter et \
                                    gjennomført opphold som er betalt, og `CANCELLED` avlyser en \
                                    booking som ennå ikke er `COMPLETED` (og frigjør plassene). \
                                    Hopp aldri over et steg — sett heller status to ganger.""")
                    BookingStatus status) {
        // Ingen sjekk av tilstandsmaskinen her: BookingService.updateStatus spør
        // BookingStatus.canTransitionTo(...) og kaster ValidationException ved en ulovlig
        // overgang (NotFoundException ved ukjent id). Begge får boble ut (T-04).
        return bookings.updateStatus(id, status);
    }

    /**
     * Lister bookinger ved å delegere til {@link BookingService#list(BookingStatus)}, som
     * returnerer alle når {@code status} er {@code null} og ellers filtrerer på den ene statusen.
     *
     * <p><b>Et valgfritt enum — de to lærdommene kombinert.</b> Parameteren er
     * {@link BookingStatus} (T-09: enum-typen, ikke {@code String}, så skjemaet får en
     * {@code enum}-liste som validatoren faktisk håndhever) <em>og</em> merket
     * {@code required = false} (T-04: Spring AI har {@code PROPERTY_REQUIRED_BY_DEFAULT = true},
     * så et valgfritt argument må sies eksplisitt). Resultatet er et delskjema med begge deler:
     * {@code {"type":"string","enum":[…]}} i {@code properties}, og et <b>tomt</b>
     * {@code required}. Kombinasjonen betyr at modellen kan utelate argumentet helt, men at
     * <em>oppgir</em> den det, må verdien være en av de fem.
     *
     * <p>Enum-et er en referansetype, så {@code null} overlever helt fram til tjenesten uten
     * boksings-trikset {@code Double}/{@code Integer} krevde i T-04 — «ikke oppgitt» er
     * allerede representerbart.
     *
     * <p><b>Svaret er en bar {@code List<Booking>}</b>, ikke en konvolutt à la
     * {@code AvailabilityResult} fra T-05. Konvolutten der fantes for å gjøre et tomt svar
     * lesbart, og den begrunnelsen slår ikke til her: den eneste parameteren er en verdi
     * modellen selv sendte og som skjemaet allerede har validert, og det finnes ingen
     * «ukjent id gir stille tomt svar»-felle å oppklare. Da står T-03-konvensjonen —
     * domene-record ut, ingen innpakning — slik den også gjør for {@code list_destinations} og
     * {@code search_destinations}, som er de nærmeste naboene. At en tom liste er et gyldig svar
     * og ikke en feil, sies i {@code description} i stedet.
     *
     * <p>Hintene er de samme som for {@code get_booking}: et rent {@code SELECT} endrer
     * ingenting ({@code readOnlyHint = true}, {@code destructiveHint = false}), gjentatte kall
     * har ingen effekt ({@code idempotentHint = true}), og alt ligger i vår egen SQLite-base
     * ({@code openWorldHint = false}). De settes per metode, ikke arves fra de skrivende
     * nabometodene — se klasse-javadoc-en.
     */
    @McpTool(
            name = "list_bookings",
            title = "List bookinger",
            description =
                    """
                    Lister bookinger, sortert på `id` (eldste først), eventuelt filtrert på \
                    status. Utelat `status` for å få **alle** bookinger. Bruk verktøyet når \
                    brukeren spør «hvilke bookinger har jeg?», «hva venter på bekreftelse?» \
                    eller «vis de betalte» — og når du trenger å finne igjen en booking hvis \
                    id du ikke har. Har du allerede id-en, bruk `get_booking` i stedet; det \
                    er ett oppslag i stedet for hele lista.

                    Hvert element er en hel booking: `id`, `customerName`, `destinationId`, \
                    `startDate`, `endDate`, `numTravelers`, `totalPrice` i norske kroner og \
                    `status`. `destinationId` er en id, ikke et navn — slå den opp med \
                    `list_destinations` hvis brukeren skal se hva reisemålet heter.

                    En **tom liste er et gyldig svar**, ikke en feil: den betyr at det ikke \
                    finnes noen booking med den statusen (eller ingen bookinger i det hele \
                    tatt, hvis du ikke filtrerte). Si det til brukeren i stedet for å melde at \
                    noe gikk galt, og prøv gjerne uten filter for å se om det finnes bookinger \
                    med en annen status. Merk at kansellerte bookinger blir liggende med status \
                    `CANCELLED` — de forsvinner ikke fra lista, men teller ikke lenger mot \
                    kapasiteten.

                    Verktøyet **endrer ingenting** og kan trygt kalles på nytt. Bruk \
                    `create_booking` for å opprette en booking og `update_booking_status` for å \
                    flytte en videre i livssyklusen.""",
            annotations =
                    @McpTool.McpAnnotations(
                            title = "List bookinger",
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    public List<Booking> listBookings(
            @McpToolParam(
                            required = false,
                            description =
                                    """
                                    Valgfritt statusfilter. **Utelat parameteren** for å få alle \
                                    bookinger — ikke finn på en verdi for å «fylle den ut». \
                                    Oppgir du den, må det være én av de fem verdiene i skjemaet, \
                                    skrevet med store bokstaver: `PENDING` (opprettet, ikke \
                                    bekreftet), `CONFIRMED` (bekreftet, ikke betalt), `PAID` \
                                    (betalt), `COMPLETED` (gjennomført) eller `CANCELLED` \
                                    (avlyst). Bare den ene statusen kommer med — det går ikke an \
                                    å be om flere på én gang, så kall verktøyet en gang per \
                                    status, eller uten filter og sorter selv.""")
                    BookingStatus status) {
        // null = alle: BookingService.list velger mellom findAll() og findByStatus(...).
        // Ingen filtrering, sortering eller innpakning her — repository-et sorterer på id.
        return bookings.list(status);
    }
}
