package no.computas.vacationmcp.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import no.computas.vacationmcp.domain.Availability;
import no.computas.vacationmcp.domain.Booking;
import no.computas.vacationmcp.domain.BookingStatus;
import no.computas.vacationmcp.domain.Destination;
import no.computas.vacationmcp.repository.AvailabilityRepository;
import no.computas.vacationmcp.repository.BookingRepository;
import no.computas.vacationmcp.repository.DestinationRepository;
import org.springframework.stereotype.Service;

/**
 * Aggregeringslogikken bak {@code bookings_report} (T-16 i {@code BACKLOG.md}) — omsetning og
 * belegg per reisemål og periode.
 *
 * <h2>Hvorfor det finnes ny kode i tjenestelaget her</h2>
 *
 * <p>Regelen for hele workshopen er at verktøy skal <em>eksponere</em> ferdig forretningslogikk,
 * ikke skrive ny. T-16 er det ene unntaket: ingen eksisterende tjeneste aggregerer, og en rapport
 * er ikke et oppslag man kan sette sammen av {@code list_bookings} uten å definere noe nytt.
 * Definisjonene av «omsetning» og «belegg» <b>er</b> forretningsregler — de svarer på hvilke
 * statuser som teller som inntekt og hva nevneren i et beleggstall er — og hører derfor hjemme
 * her, ved siden av {@link BookingService} og {@link PricingService}, ikke i {@code tools/}-laget.
 * {@code ReportTools} forblir en ren fasade, som alle de andre verktøyklassene.
 *
 * <h2>Hvorfor Java og ikke SQL</h2>
 *
 * <p>Alternativet var en aggregerende spørring i {@code repository/}-laget. Java vant, av fire
 * grunner:
 *
 * <ol>
 *   <li><b>Ingen eksisterende repository-metode måtte endres, og ingen ny SQL kom til.</b>
 *       Rapporten er bygget utelukkende av {@link DestinationRepository#findAllAvailable()},
 *       {@link AvailabilityRepository#findByDestinationId(long)} og
 *       {@link BookingRepository#findAll()} — alle tre fantes fra før. En rapport skal ikke kunne
 *       ødelegge for booking-flyten.
 *   <li><b>Plassdøgn-regnestykket er datoaritmetikk, ikke gruppering.</b> Belegget krever at hver
 *       booking klippes mot både perioden og et eventuelt datofilter før nettene telles. I SQLite
 *       ville det blitt {@code julianday(max(...)) - julianday(min(...))} inne i en korrelert join
 *       mellom {@code bookings} og {@code availability}, med ISO-tekstdatoer. Det er vanskeligere å
 *       lese, vanskeligere å teste og lettere å ta feil av enn ti linjer med
 *       {@link ChronoUnit#DAYS}.
 *   <li><b>Datamengden gjør det til et ikke-tema.</b> Fem reisemål, seks perioder og bookinger i
 *       titallsklassen. En aggregerende spørring ville vært optimalisering uten et problem å løse;
 *       på et datasett der {@code findAll()} ikke er forsvarlig, ville konklusjonen vært motsatt.
 *   <li><b>Definisjonene blir testbare.</b> Reglene står som vanlig Java som
 *       {@code ReportToolsTest} kan pinne ned booking for booking, i stedet for i en SQL-streng.
 * </ol>
 *
 * <p><b>Prisen</b> er at overlapp-predikatet finnes to steder: her, og i
 * {@code BookingRepository.sumActiveTravelers} sin {@code WHERE}-klausul. De er bevisst holdt
 * ordrett like — halvåpent {@code start < to AND end > from}, alt unntatt {@code CANCELLED} — så
 * rapporten teller nøyaktig de bookingene kapasitetssjekken teller. Se {@link #overlaps} og
 * T-11-seksjonen i {@code SOLUTION-STATUS.md}.
 */
@Service
public class ReportingService {

    private final DestinationRepository destinations;
    private final AvailabilityRepository availability;
    private final BookingRepository bookings;

    public ReportingService(
            DestinationRepository destinations,
            AvailabilityRepository availability,
            BookingRepository bookings) {
        this.destinations = destinations;
        this.availability = availability;
        this.bookings = bookings;
    }

    /**
     * Regn ut rapporten. Alle tre parametrene er valgfrie ({@code null} = ikke filtrer).
     *
     * <p><b>Datofilteret virker ulikt på de to tallene, og det er tilsiktet.</b> En booking teller
     * med hvis den overlapper vinduet, og da teller <em>hele</em> {@code totalPrice} — omsetning
     * fordeles ikke forholdsmessig på netter, for det er ett beløp som ble fakturert én gang.
     * Belegget er derimot en rate over tid, så både teller og nevner klippes mot vinduet: bare
     * netter innenfor {@code [from, to)} telles, og nevneren er kapasiteten i den delen av perioden
     * som ligger inne i vinduet. Konsekvensen å kjenne til: et smalt vindu rundt et opphold gir
     * full omsetning, men bare de nettene som er inni.
     *
     * @param destinationId begrens til ett reisemål, eller {@code null} for alle tilgjengelige
     * @param from          tidligste dato som telles med (inklusiv), eller {@code null}
     * @param to            seneste dato som telles med (eksklusiv — utsjekksdagen), eller {@code null}
     * @throws ValidationException hvis {@code from} ikke er før {@code to}
     * @throws NotFoundException   hvis {@code destinationId} ikke finnes
     */
    public BookingsReport report(Long destinationId, LocalDate from, LocalDate to) {
        if (from != null && to != null && !from.isBefore(to)) {
            // Samme melding som PricingService/AvailabilityTools — ett feilspråk for hele serveren.
            throw new ValidationException("fra-dato må være før til-dato");
        }
        // Et utelatt filter blir et ubegrenset vindu, så resten av koden slipper null-sjekker.
        LocalDate windowFrom = from != null ? from : LocalDate.MIN;
        LocalDate windowTo = to != null ? to : LocalDate.MAX;

        List<Destination> selected = destinationId == null
                ? destinations.findAllAvailable()
                : List.of(destinations.findById(destinationId).orElseThrow(
                        () -> new NotFoundException("Fant ingen destinasjon med id " + destinationId)));

        // Ett oppslag for alle bookinger, filtrert på vinduet og gruppert per reisemål. Reisemål
        // uten bookinger får en tom liste og blir stående i rapporten med nuller — «Toscana står
        // tomt» er et svar, en manglende linje er det ikke.
        Map<Long, List<Booking>> perDestination = bookings.findAll().stream()
                .filter(booking -> overlaps(booking.startDate(), booking.endDate(), windowFrom, windowTo))
                .collect(Collectors.groupingBy(Booking::destinationId));

        List<BookingsReport.DestinationLine> lines = new ArrayList<>();
        for (Destination destination : selected) {
            lines.add(line(
                    destination,
                    perDestination.getOrDefault(destination.id(), List.of()),
                    windowFrom,
                    windowTo));
        }
        // Sortert på omsetning, høyest først: «hvilket reisemål tjener vi mest på?» skal kunne
        // leses av den første linja. Lik omsetning (typisk to nuller) sorteres på id, så
        // rekkefølgen er deterministisk.
        lines.sort(Comparator
                .comparingDouble((BookingsReport.DestinationLine line) -> line.totals().revenue())
                .reversed()
                .thenComparingLong(BookingsReport.DestinationLine::destinationId));

        return new BookingsReport(from, to, destinationId, lines.size(), sum(lines), lines);
    }

    /** Én reisemålslinje: periodene regnes først, og reisemålets tall er summen av dem. */
    private BookingsReport.DestinationLine line(
            Destination destination, List<Booking> inWindow, LocalDate windowFrom, LocalDate windowTo) {

        List<Booking> counted = inWindow.stream()
                .filter(booking -> booking.status() != BookingStatus.CANCELLED)
                .toList();
        List<Booking> cancelled = inWindow.stream()
                .filter(booking -> booking.status() == BookingStatus.CANCELLED)
                .toList();

        List<Availability> periods = availability.findByDestinationId(destination.id());
        List<BookingsReport.PeriodLine> periodLines = new ArrayList<>();
        for (Availability period : periods) {
            // Perioden klippet mot vinduet. Ligger den helt utenfor, er den ikke en del av
            // rapporten i det hele tatt — verken i nevneren eller som en linje med nuller.
            LocalDate start = max(period.startDate(), windowFrom);
            LocalDate end = min(period.endDate(), windowTo);
            if (!start.isBefore(end)) {
                continue;
            }

            int nights = nights(start, end);
            int capacityNights = period.capacity() * nights;
            int bookedNights = 0;
            for (Booking booking : counted) {
                // Plassdøgn = reisende × netter som faller innenfor både perioden og vinduet.
                bookedNights += booking.numTravelers()
                        * nights(max(booking.startDate(), start), min(booking.endDate(), end));
            }

            // Omsetning fordeles ikke på netter: hele beløpet føres på den ene perioden som dekker
            // oppholdet — samme rad som PricingService.findCoveringPeriod fant da bookingen ble
            // opprettet.
            List<Booking> belongsHere = counted.stream()
                    .filter(booking -> covering(periods, booking)
                            .map(match -> match.id() == period.id())
                            .orElse(false))
                    .toList();

            periodLines.add(new BookingsReport.PeriodLine(
                    period.id(),
                    start,
                    end,
                    period.capacity(),
                    nights,
                    capacityNights,
                    bookedNights,
                    rate(bookedNights, capacityNights),
                    belongsHere.size(),
                    kroner(belongsHere.stream().mapToDouble(Booking::totalPrice).sum())));
        }

        int capacityNights = periodLines.stream()
                .mapToInt(BookingsReport.PeriodLine::capacityNights).sum();
        int bookedNights = periodLines.stream()
                .mapToInt(BookingsReport.PeriodLine::bookedNights).sum();

        BookingsReport.Totals totals = new BookingsReport.Totals(
                counted.size(),
                counted.stream().mapToInt(Booking::numTravelers).sum(),
                kroner(counted.stream().mapToDouble(Booking::totalPrice).sum()),
                kroner(counted.stream()
                        .filter(booking -> booking.status() == BookingStatus.PENDING)
                        .mapToDouble(Booking::totalPrice).sum()),
                cancelled.size(),
                kroner(cancelled.stream().mapToDouble(Booking::totalPrice).sum()),
                capacityNights,
                bookedNights,
                rate(bookedNights, capacityNights));

        return new BookingsReport.DestinationLine(
                destination.id(), destination.name(), destination.country(), totals, periodLines);
    }

    /**
     * Totalsummene. Belegget regnes på nytt fra de summerte plassdøgnene — ikke som et snitt av
     * ratene per reisemål, som ville gitt et lite, tomt reisemål like mye å si som et stort fullt.
     */
    private BookingsReport.Totals sum(List<BookingsReport.DestinationLine> lines) {
        List<BookingsReport.Totals> totals = lines.stream()
                .map(BookingsReport.DestinationLine::totals).toList();
        int capacityNights = totals.stream().mapToInt(BookingsReport.Totals::capacityNights).sum();
        int bookedNights = totals.stream().mapToInt(BookingsReport.Totals::bookedNights).sum();
        return new BookingsReport.Totals(
                totals.stream().mapToInt(BookingsReport.Totals::bookings).sum(),
                totals.stream().mapToInt(BookingsReport.Totals::travelers).sum(),
                kroner(totals.stream().mapToDouble(BookingsReport.Totals::revenue).sum()),
                kroner(totals.stream().mapToDouble(BookingsReport.Totals::pendingRevenue).sum()),
                totals.stream().mapToInt(BookingsReport.Totals::cancelledBookings).sum(),
                kroner(totals.stream().mapToDouble(BookingsReport.Totals::cancelledRevenue).sum()),
                capacityNights,
                bookedNights,
                rate(bookedNights, capacityNights));
    }

    /**
     * Perioden som dekker <em>hele</em> oppholdet, med samme regel som
     * {@code AvailabilityRepository.findCovering}: {@code start <= from AND end >= to}, og den
     * med lavest startdato vinner. Lista fra repository-et er allerede sortert på startdato, så
     * {@code findFirst()} tilsvarer {@code ORDER BY start_date LIMIT 1}.
     *
     * <p>Er den tom — en booking lagt inn utenom {@code create_booking} — teller bookingen fortsatt
     * i reisemålets omsetning, men havner ikke på noen periodelinje. Summen av periodelinjene kan
     * altså være lavere enn reisemålets {@code revenue}; det er ærligere enn å tvinge beløpet inn i
     * en periode det ikke hører hjemme i.
     */
    private static Optional<Availability> covering(List<Availability> periods, Booking booking) {
        return periods.stream()
                .filter(period -> !period.startDate().isAfter(booking.startDate())
                        && !period.endDate().isBefore(booking.endDate()))
                .findFirst();
    }

    /**
     * Halvåpen overlapp, ordrett den samme regelen som {@code sumActiveTravelers} bruker i SQL:
     * {@code start_date < to AND end_date > from}. Strenge ulikheter, så utsjekksdagen er fri — et
     * opphold som starter der et annet slutter, overlapper ikke.
     */
    private static boolean overlaps(LocalDate start, LocalDate end, LocalDate from, LocalDate to) {
        return start.isBefore(to) && end.isAfter(from);
    }

    /** Antall netter i {@code [start, end)}; 0 hvis intervallet er tomt eller snudd. */
    private static int nights(LocalDate start, LocalDate end) {
        return start.isBefore(end) ? (int) ChronoUnit.DAYS.between(start, end) : 0;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    /** Belegg med fire desimaler, eller {@code null} når det ikke finnes en nevner å dele på. */
    private static Double rate(int bookedNights, int capacityNights) {
        return capacityNights == 0 ? null : Math.round(bookedNights * 10000.0 / capacityNights) / 10000.0;
    }

    /** Kroner med to desimaler — summer av double-priser skal ikke lekke 9599.999999 ut i JSON. */
    private static double kroner(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
