package no.computas.vacationmcp.resources;

import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.stream.Collectors;
import no.computas.vacationmcp.domain.Booking;
import no.computas.vacationmcp.domain.BookingStatus;
import no.computas.vacationmcp.repository.DestinationRepository;
import no.computas.vacationmcp.service.BookingService;
import no.computas.vacationmcp.service.ValidationException;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * MCP-<b>ressurser</b> for bookinger (T-14 i {@code BACKLOG.md}).
 *
 * <p>Søsterklassen til {@link DestinationResources}, og den følger de samme beslutningene:
 * innholdet er lesbar markdown ({@code text/markdown}) og ikke JSON, id-en står alltid i teksten
 * slik at modellen kan gå videre til verktøyene, URI-variabelen kommer som {@link String} og må
 * konverteres her, og feil får boble ut av metoden slik at {@code resources/read} svarer med en
 * JSON-RPC-{@code error}. Se «Ressurser over MCP-grensen» i {@code SOLUTION-STATUS.md}.
 *
 * <p><b>Hvorfor finnes det ingen {@code booking://list} her, når T-13 laget
 * {@code destination://catalog}?</b> Fordi de to datasettene har helt ulik karakter. Katalogen er
 * referansedata: den endrer seg sjelden, er den samme for alle, og er nyttig å legge ved i
 * konteksten <em>før</em> samtalen starter — nøyaktig det en ressurs er ment for. Booking-lista er
 * transaksjonsdata: den endres av {@code create_booking}, {@code update_booking_status} og
 * {@code cancel_booking} mens samtalen pågår, den vokser uten øvre grense, og et menneske som
 * blar i {@code resources/list} tenker sjelden «legg ved alle bookinger». En ressurs som legges
 * rått inn i konteksten og deretter blir foreldet er verre enn ingen ressurs — modellen kan ikke
 * se at teksten er utdatert. Modellen har allerede {@code list_bookings} for det behovet, med
 * statusfilter og et ferskt oppslag per kall. Én <em>konkret</em> booking er derimot et
 * dokument et menneske faktisk peker på («se på booking 7»), og derfor er malen alene riktig
 * omfang her.
 */
@Component
public class BookingResources {

    private final BookingService bookings;

    /**
     * Ekstra oppslag utelukkende for å få <em>navnet</em> på reisemålet inn i teksten.
     * {@link Booking} har bare {@code destinationId}, og «reisemål id 3» hjelper verken mennesket
     * eller modellen med å oppsummere bookingen. Kostnaden er én ekstra spørring per lesning;
     * gevinsten er at ressursen står på egne bein og ikke krever et oppfølgende
     * {@code destination://3}.
     */
    private final DestinationRepository destinations;

    public BookingResources(BookingService bookings, DestinationRepository destinations) {
        this.bookings = bookings;
        this.destinations = destinations;
    }

    /**
     * <b>Resource template.</b> URI-en inneholder {@code {id}}, så oppføringen havner i
     * {@code resources/templates/list} — ikke i {@code resources/list}, som bare inneholder
     * statiske URI-er.
     *
     * <p>Innholdet er satt sammen for at en LLM skal kunne oppsummere bookingen for et menneske i
     * én omgang: kunde, reisemål med navn, periode med antall netter, antall reisende, totalpris
     * og status — pluss hvilke statusoverganger som er lovlige nå. Overgangene leses av
     * {@link BookingStatus#canTransitionTo(BookingStatus)}, så tilstandsmaskinen er ikke duplisert
     * her; den er bare gjengitt.
     *
     * <p>Ukjent id håndteres som i T-13: {@link BookingService#get(long)} kaster
     * {@code NotFoundException}, den får boble, og klienten får en {@code -32602} med meldingen
     * vår i {@code data}. En ikke-numerisk id stoppes av vår egen parsing — det finnes ikke noe
     * {@code inputSchema} som kan gjøre det for oss.
     */
    @McpResource(
            uri = "booking://{id}",
            name = "booking",
            description =
                    """
                    Én konkret booking slått opp på id, som lesbar markdown: kunde, reisemål \
                    (navn og id), periode og antall netter, antall reisende, totalpris, status og \
                    hvilke statusoverganger som er lovlige nå. Fyll inn id-en fra svaret på \
                    `create_booking` eller `list_bookings` — f.eks. `booking://7`. Ukjent id gir \
                    en feil, ikke et tomt innhold. Merk at innholdet er et øyeblikksbilde: \
                    statusen kan endres av `update_booking_status` og `cancel_booking` etter at \
                    teksten er lagt i konteksten, så les på nytt før du bekrefter en status.""",
            mimeType = "text/markdown")
    public String booking(String id) {
        long bookingId = parseId(id);
        Booking booking = bookings.get(bookingId);
        long netter = ChronoUnit.DAYS.between(booking.startDate(), booking.endDate());

        return "# Booking %d — %s\n\n".formatted(booking.id(), booking.customerName())
                + "- **id:** %d — bruk denne i `get_booking`, `update_booking_status` og `cancel_booking`.\n"
                        .formatted(booking.id())
                + "- **Kunde:** %s\n".formatted(booking.customerName())
                + "- **Reisemål:** %s\n".formatted(reisemaal(booking.destinationId()))
                + "- **Periode:** %s → %s (%d %s)\n"
                        .formatted(
                                booking.startDate(),
                                booking.endDate(),
                                netter,
                                netter == 1 ? "natt" : "netter")
                + "- **Antall reisende:** %d\n".formatted(booking.numTravelers())
                + "- **Totalpris:** %s kr (låst ved bestilling — inkluderer eventuell sesongpris for perioden)\n"
                        .formatted(kroner(booking.totalPrice()))
                + "- **Status:** %s — %s\n".formatted(booking.status(), forklaring(booking.status()))
                + "- **Lovlige neste statuser:** %s\n".formatted(nesteStatuser(booking.status()));
    }

    /** «Kyoto Machiya (id 3), Japan» — eller bare id-en hvis reisemålet skulle mangle. */
    private String reisemaal(long destinationId) {
        return destinations
                .findById(destinationId)
                .map(d -> "%s (id %d), %s".formatted(d.name(), d.id(), d.country()))
                .orElse("ukjent reisemål (id %d)".formatted(destinationId));
    }

    /** Kort, menneskelig forklaring av statusen — det modellen skal si til brukeren. */
    private static String forklaring(BookingStatus status) {
        return switch (status) {
            case PENDING -> "opprettet, men ikke bekreftet ennå";
            case CONFIRMED -> "bekreftet, men ikke betalt";
            case PAID -> "betalt, reisen er ikke gjennomført ennå";
            case COMPLETED -> "gjennomført og avsluttet";
            case CANCELLED -> "kansellert; plassene er frigjort for andre bookinger";
        };
    }

    /**
     * Leses av tilstandsmaskinen i {@link BookingStatus}, ikke skrevet ned på nytt her. Da kan
     * ikke ressursteksten komme ut av takt med regelen {@code BookingService.updateStatus(...)}
     * faktisk håndhever.
     */
    private static String nesteStatuser(BookingStatus status) {
        String lovlige = Arrays.stream(BookingStatus.values())
                .filter(status::canTransitionTo)
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        return lovlige.isEmpty() ? "ingen — dette er en endestatus" : lovlige;
    }

    /**
     * URI-malen slipper gjennom hva som helst på {@code {id}}-plassen — {@code booking://abc}
     * havner her like fullt. Samme parsing som i {@link DestinationResources}, med en melding som
     * forklarer malen.
     */
    private static long parseId(String id) {
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new ValidationException(
                    "«%s» er ikke en gyldig booking-id. URI-malen er booking://{id} der {id} er et heltall, f.eks. booking://7. Bruk `list_bookings` for å finne gyldige id-er."
                            .formatted(id));
        }
    }

    /** 9600.0 → «9600», 9600.5 → «9600.5». Ren presentasjon, som i {@link DestinationResources}. */
    private static String kroner(double beloep) {
        return beloep == Math.rint(beloep) ? Long.toString((long) beloep) : Double.toString(beloep);
    }
}
