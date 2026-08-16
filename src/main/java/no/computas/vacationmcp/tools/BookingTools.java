package no.computas.vacationmcp.tools;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import no.computas.vacationmcp.domain.Booking;
import no.computas.vacationmcp.domain.BookingStatus;
import no.computas.vacationmcp.service.BookingService;
import no.computas.vacationmcp.service.PricingService;
import no.computas.vacationmcp.service.Quote;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.stereotype.Component;

/**
 * MCP-verktøy for booking-arbeidsflyten (Epic 3–4 i {@code BACKLOG.md}).
 *
 * <p>Klassen er hjemmet til hele booking-domenet: {@code create_booking} (T-07),
 * {@code get_booking} (T-08), {@code update_booking_status} (T-09), {@code list_bookings}
 * (T-10), {@code cancel_booking} (T-12) og {@code create_booking_interactive} (T-20). De fem
 * første går mot den samme {@link BookingService}-en, så konstruktøren klarte seg med én
 * avhengighet helt fram til T-20 — det som kom til underveis var metoder, ikke felt. T-20 er
 * det første verktøyet her som trenger noe mer: det henter et pristilbud
 * ({@link PricingService}) <em>før</em> det spør brukeren, og skriver bare hvis svaret er ja.
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
    private final PricingService pricing;

    public BookingTools(BookingService bookings, PricingService pricing) {
        this.bookings = bookings;
        this.pricing = pricing;
    }

    /**
     * Oppretter en booking ved å delegere til
     * {@link BookingService#createBooking(String, long, LocalDate, LocalDate, int)}, som gjør
     * validering, prisberegning og kapasitetssjekk og lagrer med status
     * {@link BookingStatus#PENDING}.
     *
     * <p><b>Kapasitetsregelen (T-11) ligger i tjenesten, ikke her.</b> {@code BookingService}
     * finner den ene {@code availability}-raden som dekker hele oppholdet, trekker fra summen av
     * reisende i alle ikke-kansellerte bookinger på reisemålet som overlapper datoene
     * ({@code BookingRepository.sumActiveTravelers}), og kaster {@code ValidationException} hvis
     * det ikke er nok igjen. Verktøyet legger ingen sjekk oppå — jobben er å formidle meldingen
     * videre, og det er derfor {@code description} bruker plass på hva modellen skal gjøre med
     * «N ledige plasser, M forespurt». Se T-11-seksjonen i {@code SOLUTION-STATUS.md} for
     * regnestykket i detalj.
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
                    ukjent reisemål, `from` etter `to`, færre enn 1 reisende, tomt kundenavn, \
                    og datoer som ingen **enkelt** tilgjengelighetsperiode dekker («Ingen \
                    tilgjengelig periode dekker …» — et opphold som krysser skjøten mellom to \
                    perioder avvises selv om begge periodene er åpne). `get_quote` gir prisen \
                    uten å booke noe — bruk den når brukeren bare lurer på hva det koster.

                    Er det for få plasser igjen, avvises kallet med «Ikke nok kapasitet i \
                    perioden: N ledige plasser, M forespurt», og **ingenting lagres**. N \
                    gjelder nøyaktig de datoene du spurte om, og er periodens kapasitet minus \
                    alle bookinger på reisemålet som overlapper datoene — uansett status, \
                    bortsett fra `CANCELLED`. Slik bruker du tallet:

                    - **N ≥ 1:** foreslå N reisende eller færre på samme datoer, så går det \
                    gjennom. Resten av følget må eventuelt bookes på andre datoer.
                    - **N = 0:** færre reisende hjelper ikke — det er fullt. Foreslå andre \
                    datoer eller et annet reisemål.
                    - **Datoene teller halvåpent:** et opphold som *starter* på utsjekksdagen \
                    til et annet kolliderer ikke. Å flytte innsjekk én dag, eller korte ned \
                    oppholdet, kan derfor være nok til å frigjøre plass.
                    - `check_availability` viser periodens **totale** kapasitet, ikke ledige \
                    plasser. N i denne feilmeldingen er fasiten for akkurat de datoene; bruk \
                    `check_availability` til å finne andre åpne perioder å foreslå.

                    Ikke gjenta det samme kallet uten å endre noe — svaret blir det samme.""",
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

                    Skal du **kansellere**, bruk `cancel_booking` i stedet. Det er nøyaktig samme \
                    operasjon som `CANCELLED` her, men med ett argument i stedet for to, så det \
                    finnes ingen statusverdi å bomme på. Bruk dette verktøyet til å flytte en \
                    booking *framover* i livssyklusen.

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
                    `create_booking` for å opprette en booking, `update_booking_status` for å \
                    flytte en videre i livssyklusen, og `cancel_booking` for å avlyse en.""",
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

    /**
     * Kansellerer en booking ved å delegere til {@link BookingService#cancel(long)}, som frigjør
     * plassene bookingen holdt på i perioden.
     *
     * <p><b>Forholdet til {@code update_booking_status}: operasjonen er identisk.</b> Det er verdt
     * å si rett ut, siden det er det åpenbare spørsmålet oppgaven reiser.
     * {@link BookingService#cancel(long)} er én linje — {@code updateStatus(id, CANCELLED)} — så
     * samme tilstandsmaskin, samme {@code UPDATE}, samme feilmeldinger og samme returverdi. Et
     * {@code cancel_booking(3)} og et {@code update_booking_status(3, CANCELLED)} er ikke til å
     * skille fra hverandre i databasen etterpå. Verktøyet finnes altså <em>ikke</em> fordi det gjør
     * noe annet, men fordi det er en annen <em>oppføring i katalogen</em>:
     *
     * <ul>
     *   <li><b>Navnet er det sterkeste signalet modellen får.</b> «Kanseller booking 3» treffer
     *       {@code cancel_booking} direkte. Veien om {@code update_booking_status} krever at
     *       modellen i tillegg velger riktig verdi blant fem i enum-et — og en bom der er ikke en
     *       feilmelding, men en <em>annen</em> lovlig endring: {@code COMPLETED} på en
     *       {@code PAID}-booking er en gyldig overgang som stille markerer oppholdet som
     *       gjennomført i stedet for avlyst. Ett obligatorisk argument i stedet for to fjerner hele
     *       den feilklassen.
     *   <li><b>Granulariteten i {@code annotations} følger verktøyet, ikke argumentverdien.</b>
     *       {@code update_booking_status} må bære verste fall for alle fem verdiene: det er
     *       {@code CANCELLED} som gjør at hintet er {@code destructiveHint = true}, mens
     *       {@code PENDING → CONFIRMED} er ren framdrift. En host kan ikke sette regler per
     *       argumentverdi — den ser bare verktøynavnet og hint-blokken. Med kanselleringen som
     *       egen oppføring kan hosten gate, logge eller kreve bekreftelse på <em>akkurat</em> den
     *       handlingen ved navn. Det er dette en generisk statusendring ikke kan uttrykke, og
     *       merk at poenget ikke er at hint-<em>verdiene</em> blir andre (de er ordrett de samme
     *       som i T-09), men hvilket kall de henger på.
     * </ul>
     *
     * <p><b>Hintene — samme blokk som {@code update_booking_status}, og det er riktig:</b>
     *
     * <ul>
     *   <li>{@code readOnlyHint = false} — et {@code UPDATE} mot {@code bookings}. Hosten skal
     *       behandle kallet som en handling, ikke som et oppslag.
     *   <li>{@code destructiveHint = true} — dette er det mest destruktive verktøyet i settet, og
     *       det eneste stedet hvor alle tre pekene i spesifikasjonen peker samme vei samtidig:
     *       {@code status} <em>overskrives</em> (den forrige verdien er borte), {@code CANCELLED}
     *       er en <b>endestasjon</b> uten vei tilbake, og handlingen frigjør plassene til andre —
     *       så selv om raden ligger igjen, kan effekten være umulig å reversere i praksis: er
     *       plassene tatt av noen andre i mellomtiden, hjelper det ikke å opprette en ny booking.
     *       {@code create_booking} (T-07) er kontrasten: et rent {@code INSERT} der ingenting går
     *       tapt, og der {@code false} er riktig.
     *   <li>{@code idempotentHint = true} — spørsmålet oppgaven ber om å tenke gjennom: hva skjer
     *       ved to kanselleringer på rad? Det første kallet flytter bookingen til
     *       {@code CANCELLED}. Det andre slår i tilstandsmaskinen —
     *       {@link BookingStatus#canTransitionTo(BookingStatus)} har et <em>tomt</em> sett for
     *       {@code CANCELLED}, så en overgang til seg selv er ikke en kant — og gir
     *       {@code ValidationException("Ulovlig statusovergang: CANCELLED -> CANCELLED")}. Kallet
     *       <em>feiler</em>, men databasen er identisk etter kall to som etter kall ett, og hintet
     *       handler nettopp om <b>effekten</b> av gjentatte kall, ikke om svaret. Ingen ekstra
     *       plasser frigjøres, ingen rad røres. Altså idempotent, og et retry etter timeout er
     *       trygt. Konsekvensen for modellen står i {@code description}: en «Ulovlig statusovergang:
     *       CANCELLED -&gt; CANCELLED» betyr som regel at det <em>første</em> kallet gikk gjennom.
     *   <li>{@code openWorldHint = false} — fortsatt bare vår egen SQLite-base. Ingen ekstern
     *       booking-partner å avbestille hos, ingen refusjon å be om.
     * </ul>
     */
    @McpTool(
            name = "cancel_booking",
            title = "Kanseller booking",
            description =
                    """
                    Kansellerer en booking: setter status til `CANCELLED` og **frigjør plassene** \
                    den holdt på, slik at andre kan booke dem. Bruk dette verktøyet når brukeren \
                    vil avlyse, avbestille eller «slette» en booking — det er ett argument og \
                    ingen statusverdi å bomme på. `update_booking_status` gjør det samme med \
                    `CANCELLED`, men er til for å flytte en booking *framover* i livssyklusen \
                    (`CONFIRMED`, `PAID`, `COMPLETED`).

                    Kanselleringen er **endelig**. `CANCELLED` er en endestasjon, så bookingen kan \
                    ikke gjenopplives etterpå — vil brukeren likevel reise, må du opprette en ny \
                    med `create_booking`, og da er det ingen garanti for at plassene fortsatt er \
                    ledige. Bekreft derfor med brukeren før du kaller verktøyet, og slå gjerne opp \
                    bookingen med `get_booking` først, så du kansellerer riktig id.

                    Svaret er hele den kansellerte bookingen, med `status` satt til `CANCELLED` og \
                    resten av feltene uendret. Raden **blir liggende**: den dukker fortsatt opp i \
                    `list_bookings`, men teller ikke lenger mot kapasiteten i perioden, så et \
                    `create_booking` som nettopp ble avvist med «Ikke nok kapasitet …» kan gå \
                    gjennom rett etterpå.

                    To ting avvises, og ingen av dem endrer noe: en ukjent id gir «Fant ingen \
                    booking med id N», og en booking som allerede er avsluttet gir «Ulovlig \
                    statusovergang: COMPLETED -> CANCELLED» eller «Ulovlig statusovergang: \
                    CANCELLED -> CANCELLED». Den siste betyr som regel at kanselleringen din \
                    allerede gikk gjennom — bekreft med `get_booking` i stedet for å prøve på \
                    nytt.""",
            annotations =
                    @McpTool.McpAnnotations(
                            title = "Kanseller booking",
                            readOnlyHint = false,
                            destructiveHint = true,
                            idempotentHint = true,
                            openWorldHint = false))
    public Booking cancelBooking(
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Id-en til bookingen som skal kanselleres, slik den kom fra \
                                    `create_booking`, `get_booking` eller `list_bookings`. En \
                                    ukjent id gir en feilmelding, og ingenting endres — ikke \
                                    gjett, og ikke prøv deg fram med flere id-er.""")
                    long id) {
        // Ingen forhåndssjekk av statusen her: BookingService.cancel er updateStatus(id,
        // CANCELLED), som spør tilstandsmaskinen og kaster ValidationException (ulovlig overgang)
        // eller NotFoundException (ukjent id). Begge får boble ut (T-04).
        return bookings.cancel(id);
    }

    // =================================================================================
    // T-20 · Elicitation — serveren spør brukeren midt i verktøykallet
    // =================================================================================

    /**
     * Skjemaet vi ber brukeren fylle ut. Recorden er <b>kontrakten mot mennesket</b>, ikke mot
     * modellen: Spring AI genererer et JSON Schema av den og legger det i
     * {@code requestedSchema} på {@code elicitation/create}, og hosten rendrer det som en
     * dialog eller et skjema.
     *
     * <p><b>MCP tillater bare et flatt skjema med primitive felt</b> —
     * {@code string}/{@code number}/{@code integer}/{@code boolean}, eventuelt en
     * {@code enum} av strenger. Ingen nøstede objekter, ingen lister. Det er en langt
     * strammere kontrakt enn {@code inputSchema} for et verktøy (som gjerne har nøstede
     * records), og grunnen er praktisk: hosten skal kunne bygge et skjema av dette uten å vite
     * noe om domenet vårt. SDK-en håndhever det — {@code McpAsyncServerExchange
     * .createElicitation} validerer {@code requestedSchema} før den sender noe, så et nøstet
     * felt her blir en feil på serveren, ikke en rar dialog hos brukeren.
     *
     * <p>{@code @JsonPropertyDescription} er teksten brukeren ser ved feltet. Alle felt er
     * obligatoriske i skjemaet ({@code PROPERTY_REQUIRED_BY_DEFAULT = true} i Spring AI sin
     * skjemagenerator, samme regel som for {@code @McpToolParam} i T-04), men merk at
     * <em>ingen</em> validerer svaret for oss: kommer det tilbake uten {@code customerName},
     * er feltet {@code null} i recorden. Derfor er {@code confirmed} en bokset
     * {@link Boolean} og ikke en primitiv {@code boolean} — «ikke besvart» skal kunne skilles
     * fra «svarte nei», selv om begge behandles likt.
     */
    public record BookingConfirmation(
            @JsonPropertyDescription(
                            "Fullt navn bookingen skal stå på, f.eks. «Ola Nordmann».")
                    String customerName,
            @JsonPropertyDescription(
                            "Sett til «ja»/true for å bekrefte datoene og totalprisen over. "
                                    + "Svarer du nei, opprettes ingen booking.")
                    Boolean confirmed) {
    }

    /** Utfallet av et {@code create_booking_interactive}-kall. */
    public enum InteractiveOutcome {
        /** Brukeren bekreftet, og bookingen er opprettet. Se {@code booking}. */
        BOOKED,
        /** Brukeren avslo dialogen ({@code action: "decline"}). Ingenting er lagret. */
        DECLINED,
        /** Brukeren lukket dialogen uten å velge ({@code action: "cancel"}). */
        CANCELLED,
        /** Brukeren sendte inn skjemaet, men krysset ikke av for bekreftelse. */
        NOT_CONFIRMED,
        /** Klienten støtter ikke elicitation. Ingen dialog ble vist; se {@code message}. */
        ELICITATION_NOT_SUPPORTED
    }

    /**
     * Konvolutten {@code create_booking_interactive} returnerer. I motsetning til de andre
     * booking-verktøyene kan dette verktøyet ende <em>godt</em> uten at det finnes en booking:
     * at brukeren sier nei er et normalt utfall, ikke en feil. Derfor er svaret en konvolutt
     * med et eksplisitt {@code outcome} — og ikke en {@code isError: true} — når ingenting ble
     * lagret. Feil er fortsatt feil: ukjent reisemål, ugyldige datoer og manglende kapasitet
     * bobler ut som før (T-04).
     *
     * @param outcome hva som skjedde — se {@link InteractiveOutcome}
     * @param message én setning skrevet til modellen: hva den skal si eller gjøre videre
     * @param quote   pristilbudet brukeren fikk se. Alltid med, uansett utfall, så modellen kan
     *                oppsummere hva som ble avvist eller bekreftet
     * @param booking den lagrede bookingen — {@code null} for alt annet enn {@code BOOKED}
     */
    public record InteractiveBookingResult(
            InteractiveOutcome outcome, String message, Quote quote, Booking booking) {
    }

    /**
     * Oppretter en booking <b>bare hvis brukeren bekrefter</b>, ved å be hosten om input midt i
     * verktøykallet — MCP-primitiven <em>elicitation</em>.
     *
     * <h4>Hva elicitation er, og hvorfor det snur retningen</h4>
     *
     * <p>Alt annet i denne serveren er svar på spørsmål: hosten sender {@code tools/call}, vi
     * svarer. Elicitation snur pilen. Midt inne i behandlingen av {@code tools/call} sender
     * <em>serveren</em> en JSON-RPC-<b>request</b> den andre veien —
     * {@code elicitation/create}, med sin egen {@code id} — og blir stående og vente på svaret
     * fra hosten før den gjør ferdig verktøykallet. To forespørsler er altså i luften samtidig
     * over den samme forbindelsen, i hver sin retning. Det er nettopp derfor JSON-RPC-{@code id}
     * finnes (se T-00), og det er den mekanismen som gjør at en MCP-server kan involvere
     * mennesket uten å ha noe brukergrensesnitt selv.
     *
     * <p>Merk hvem som svarer: {@code elicitation/create} går til <em>brukeren</em>, ikke til
     * modellen. Hosten viser et skjema, mennesket fyller det ut, og svaret kommer tilbake som
     * strukturerte data. Sammenlign med T-21 (sampling), der serveren spør <em>modellen</em>.
     *
     * <h4>Flyten</h4>
     *
     * <ol>
     *   <li>Hent et pristilbud ({@link PricingService#quote}). Dette validerer reisemål, datoer
     *       og antall reisende — og skriver ingenting. Er noe galt, feiler kallet <em>før</em>
     *       brukeren plages med en dialog.
     *   <li>Sjekk at klienten faktisk kan svare ({@code ctx.elicitEnabled()}).
     *   <li>Spør brukeren: reisemål, datoer, netter og totalpris i klartekst, pluss et skjema
     *       med navn og bekreftelse.
     *   <li>Først <em>etter</em> et ja kalles {@link BookingService#createBooking}.
     * </ol>
     *
     * <h4>Tre svar, ikke ett</h4>
     *
     * <p>{@code ElicitResult.action} har tre verdier, og alle tre må håndteres:
     * {@code ACCEPT} (skjemaet ble sendt inn — innholdet ligger i {@code content}),
     * {@code DECLINE} (brukeren sa nei) og {@code CANCEL} (brukeren lukket dialogen uten å
     * velge). Bare {@code ACCEPT} har innhold; {@link StructuredElicitResult#structuredContent}
     * er {@code null} for de to andre. Og {@code ACCEPT} betyr «skjemaet kom tilbake», ikke
     * «ja» — brukeren kan ha sendt inn med bekreftelsen usatt, og da er utfallet
     * {@link InteractiveOutcome#NOT_CONFIRMED}.
     *
     * <h4>Klienter uten elicitation: definert fallback, ikke feil</h4>
     *
     * <p>De fleste klienter støtter ikke elicitation (og en rå stdio-røyktest gjør det
     * definitivt ikke). Kaller vi likevel, kaster Spring AI en {@code IllegalStateException} —
     * «Elicitation not supported by the client» — som ville blitt en {@code isError: true} med
     * et Java-klassenavn i. Verktøyet sjekker derfor {@code ctx.elicitEnabled()} <em>først</em>
     * og faller tilbake på å levere pristilbudet med utfallet
     * {@link InteractiveOutcome#ELICITATION_NOT_SUPPORTED}.
     *
     * <p>Valget mellom «tydelig feilmelding» og «definert fallback» falt på det siste, av tre
     * grunner: (1) det <em>er</em> ingen feil — hverken argumentene eller serveren er gale, og
     * {@code isError} bør bety at noe gikk galt; (2) arbeidet vi allerede har gjort er verdt
     * noe, så modellen får pristilbudet i hånden og kan bekrefte med brukeren i chatten og
     * deretter kalle {@code create_booking}; (3) degraderingen er ærlig og synlig — utfallet
     * står i klartekst i svaret, og {@code booking} er {@code null}, så ingen kan forveksle det
     * med en vellykket booking. Det som <em>ikke</em> ble vurdert som et alternativ, er å booke
     * uten å spørre: da hadde verktøyet vært farligst nettopp der garantien manglet.
     *
     * <h4>{@code annotations}: nøyaktig de samme som {@code create_booking}</h4>
     *
     * <p>Det er poenget. Hintene beskriver <em>effekten på verden</em>, ikke
     * samhandlingsmønsteret: verktøyet skriver fortsatt en rad ({@code readOnlyHint = false}),
     * det er fortsatt et rent {@code INSERT} ({@code destructiveHint = false}), to kall gir
     * fortsatt to bookinger ({@code idempotentHint = false}), og alt skjer fortsatt mot vår
     * egen base ({@code openWorldHint = false}). At serveren <em>lover</em> å spørre først
     * endrer ingenting hosten kan verifisere — og på fallback-veien spør den ikke i det hele
     * tatt. En host skal derfor gate dette verktøyet like hardt som {@code create_booking};
     * elicitation er et ekstra sikkerhetsnett, ikke en erstatning for hostens eget.
     *
     * <h4>To fallgruver</h4>
     *
     * <ul>
     *   <li><b>20 sekunders tidsavbrudd.</b> Server→klient-forespørsler bruker
     *       {@code spring.ai.mcp.server.request-timeout}, som er {@code 20s} som default. Et
     *       menneske rekker sjelden å lese og fylle ut et skjema på 20 sekunder, så skru den
     *       opp for interaktive verktøy. Tråden står og venter (kallet er synkront), men
     *       henger ikke i det uendelige.
     *   <li><b>Pristilbudet er ingen reservasjon.</b> Kapasiteten sjekkes av
     *       {@link BookingService#createBooking} <em>etter</em> at brukeren har svart. Blir
     *       plassene tatt i mellomtiden, feiler kallet med «Ikke nok kapasitet …» selv om
     *       brukeren sa ja — som forventet, men verdt å vite når dialogen står oppe lenge.
     * </ul>
     */
    @McpTool(
            name = "create_booking_interactive",
            title = "Opprett booking (med bekreftelse)",
            description =
                    """
                    Oppretter en booking, men **spør brukeren først**: verktøyet regner ut \
                    prisen, viser reisemål, datoer, netter og totalsum i en dialog, og lagrer \
                    bookingen bare hvis brukeren bekrefter. Kundenavnet spør det om i samme \
                    dialog — derfor er det ikke et argument her, og du skal **ikke** finne på \
                    et navn.

                    Bruk dette verktøyet når brukeren er klar til å booke og du vil ha en \
                    eksplisitt bekreftelse på tall og datoer. Bruk `create_booking` når du \
                    allerede har navnet og har fått bekreftelsen i samtalen — det er ett kall \
                    uten dialog. Bruk `get_quote` når brukeren bare lurer på hva det koster.

                    Svaret er alltid en konvolutt med `outcome`, `message`, `quote` \
                    (pristilbudet brukeren fikk se) og `booking`. **Les `outcome` før du sier \
                    noe til brukeren:**

                    - `BOOKED` — bookingen er opprettet, og ligger i `booking` med `id`, \
                    `status: PENDING` og `totalPrice`. Oppgi id-en til brukeren.
                    - `DECLINED` — brukeren sa nei. **Ingenting er lagret.** Ikke prøv igjen \
                    med de samme datoene; spør hva hen vil endre.
                    - `CANCELLED` — brukeren lukket dialogen uten å svare. Ingenting er \
                    lagret. Spør om hen fortsatt vil booke.
                    - `NOT_CONFIRMED` — skjemaet kom tilbake uten bekreftelse. Ingenting er \
                    lagret. Behandle det som et nei.
                    - `ELICITATION_NOT_SUPPORTED` — klienten kan ikke vise dialogen, så \
                    **ingen ble spurt og ingenting er lagret**. `quote` er likevel gyldig: \
                    gjenta datoene og totalprisen for brukeren, spør om navn og bekreftelse i \
                    chatten, og kall `create_booking` med svaret.

                    `booking` er `null` i alle tilfeller unntatt `BOOKED` — det er den sikre \
                    sjekken på om noe faktisk ble lagret. Kall verktøyet **én gang** per \
                    opphold: to kall som begge bekreftes gir to bookinger. Ugyldige datoer, \
                    ukjent reisemål og for få ledige plasser gir en vanlig feilmelding, ikke \
                    en av verdiene over.""",
            annotations =
                    @McpTool.McpAnnotations(
                            title = "Opprett booking (med bekreftelse)",
                            readOnlyHint = false,
                            destructiveHint = false,
                            idempotentHint = false,
                            openWorldHint = false))
    public InteractiveBookingResult createBookingInteractive(
            // Infrastrukturparameter: Spring AI fyller den inn selv og holder den UTE av
            // inputSchema (McpJsonSchemaGenerator hopper over McpSyncRequestContext,
            // McpSyncServerExchange, McpTransportContext m.fl.). Modellen ser den altså ikke.
            // Merk at den bare finnes i en *stateful* server — STREAMABLE eller stdio. Med
            // spring.ai.mcp.server.protocol=STATELESS kaster Spring AI ved oppstart:
            // «Stateless tool methods do not support McpSyncRequestContext parameter.»
            McpSyncRequestContext ctx,
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Id-en til reisemålet, slik den kommer fra \
                                    `list_destinations` eller `search_destinations`. En ukjent \
                                    id gir en feil, og ingen dialog vises.""")
                    long destinationId,
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Innsjekksdato på ISO-8601-formatet yyyy-MM-dd, f.eks. \
                                    «2026-07-01». Må være før til-datoen. Datoen vises til \
                                    brukeren i bekreftelsesdialogen, så den skal være den du \
                                    faktisk mener — ikke en gjetning.""")
                    LocalDate from,
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Utsjekksdato på ISO-8601-formatet yyyy-MM-dd, f.eks. \
                                    «2026-07-10». Må være etter fra-datoen. Regnes som \
                                    utsjekksdag og faktureres ikke, så 1. til 10. er ni \
                                    netter.""")
                    LocalDate to,
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Antall reisende, minst 1. Påvirker både prisen som vises i \
                                    dialogen og hvor mange plasser som beslaglegges — spør \
                                    brukeren hvis det ikke er oppgitt, ikke gjett.""")
                    int numTravelers) {

        // 1) Pristilbudet FØRST. PricingService validerer reisemål, datoer og antall reisende
        //    og skriver ingenting; feil bobler ut som vanlig (T-04). Poenget er rekkefølgen:
        //    er kallet ugyldig, skal brukeren aldri se en dialog.
        Quote quote = pricing.quote(destinationId, from, to, numTravelers);

        // 2) Har klienten i det hele tatt en mottaker? elicitEnabled() ser på
        //    ClientCapabilities fra initialize-håndtrykket. Uten sjekken ville Spring AI
        //    kastet IllegalStateException.
        if (!ctx.elicitEnabled()) {
            return new InteractiveBookingResult(
                    InteractiveOutcome.ELICITATION_NOT_SUPPORTED,
                    """
                    Klienten støtter ikke elicitation, så ingen bekreftelsesdialog kunne \
                    vises, og ingen booking er opprettet. Pristilbudet i `quote` er gyldig: \
                    gjenta datoene og totalprisen for brukeren, spør om navnet bookingen skal \
                    stå på, og kall `create_booking` når du har fått bekreftelsen i chatten.""",
                    quote,
                    null);
        }

        // 3) Spør brukeren. Kallet BLOKKERER til hosten svarer (eller til
        //    spring.ai.mcp.server.request-timeout løper ut, default 20s).
        StructuredElicitResult<BookingConfirmation> answer =
                ctx.elicit(spec -> spec.message(confirmationMessage(quote)), BookingConfirmation.class);

        // 4) Alle tre utfallene. switch-en er uttømmende over enum-et, så en ny verdi i
        //    SDK-en ville blitt en kompileringsfeil framfor en stille «ingenting skjedde».
        return switch (answer.action()) {
            case ACCEPT -> bookIfConfirmed(
                    answer.structuredContent(), quote, destinationId, from, to, numTravelers);
            case DECLINE -> new InteractiveBookingResult(
                    InteractiveOutcome.DECLINED,
                    "Brukeren avslo bookingen i dialogen. Ingenting er lagret. Ikke prøv igjen "
                            + "med de samme opplysningene — spør hva som skal endres.",
                    quote,
                    null);
            case CANCEL -> new InteractiveBookingResult(
                    InteractiveOutcome.CANCELLED,
                    "Brukeren lukket dialogen uten å svare. Ingenting er lagret. Spør om hen "
                            + "fortsatt vil booke oppholdet.",
                    quote,
                    null);
        };
    }

    /**
     * {@code ACCEPT} betyr «skjemaet kom tilbake», ikke «ja». Brukeren kan ha sendt det inn med
     * bekreftelsen usatt — og siden ingen validerer svaret for oss, kan feltene i teorien være
     * {@code null}. Begge deler behandles som et nei.
     *
     * <p>Er svaret et ja, går vi videre til {@link BookingService#createBooking} med navnet
     * brukeren selv skrev. Kapasitetssjekken skjer der, altså <em>etter</em> bekreftelsen; blir
     * plassene tatt mens dialogen står oppe, feiler kallet med «Ikke nok kapasitet …». Det er
     * riktig oppførsel — et pristilbud er ingen reservasjon — men verdt å kjenne til. Et blankt
     * navn stoppes av tjenesten med «kundenavn må oppgis»; vi gjentar ikke den regelen her.
     */
    private InteractiveBookingResult bookIfConfirmed(
            BookingConfirmation confirmation,
            Quote quote,
            long destinationId,
            LocalDate from,
            LocalDate to,
            int numTravelers) {

        if (confirmation == null || !Boolean.TRUE.equals(confirmation.confirmed())) {
            return new InteractiveBookingResult(
                    InteractiveOutcome.NOT_CONFIRMED,
                    "Brukeren sendte inn skjemaet uten å bekrefte. Ingenting er lagret. "
                            + "Behandle det som et nei, og spør hva som eventuelt skal endres.",
                    quote,
                    null);
        }

        Booking booking =
                bookings.createBooking(
                        confirmation.customerName(), destinationId, from, to, numTravelers);

        return new InteractiveBookingResult(
                InteractiveOutcome.BOOKED,
                "Brukeren bekreftet, og bookingen er opprettet med status PENDING. "
                        + "Oppgi id-en til brukeren.",
                quote,
                booking);
    }

    /**
     * Teksten mennesket leser i dialogen. Dette er det ene stedet i {@code tools/}-laget der
     * formatering hører hjemme: mottakeren er en person, ikke en modell, og det finnes ingen
     * JSON-serialisering å lene seg på (jf. samme resonnement for {@code resources/} i T-13).
     * Alle tallene brukeren skal si ja til står her — reisemål, datoer, netter, reisende, pris
     * per natt og totalsum — for det er nettopp de tallene bekreftelsen gjelder.
     */
    private static String confirmationMessage(Quote quote) {
        return """
                Bekreft bookingen:

                Reisemål: %s (%s)
                Innsjekk: %s
                Utsjekk:  %s
                Netter:   %d
                Reisende: %d
                Pris:     %s kr per natt

                Totalt:   %s kr

                Fyll inn navnet bookingen skal stå på, og bekreft. Sier du nei, opprettes ingen \
                booking."""
                .formatted(
                        quote.destination().name(),
                        quote.destination().country(),
                        quote.from(),
                        quote.to(),
                        quote.nights(),
                        quote.numTravelers(),
                        kroner(quote.pricePerNight()),
                        kroner(quote.totalPrice()));
    }

    /** «14800» framfor «14800.0» — hele kroner når beløpet er helt, ellers to desimaler. */
    private static String kroner(double amount) {
        return amount == Math.rint(amount)
                ? String.format(Locale.ROOT, "%.0f", amount)
                : String.format(Locale.ROOT, "%.2f", amount);
    }
}
