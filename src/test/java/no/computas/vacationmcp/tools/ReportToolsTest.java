package no.computas.vacationmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import no.computas.vacationmcp.domain.Booking;
import no.computas.vacationmcp.domain.BookingStatus;
import no.computas.vacationmcp.service.BookingService;
import no.computas.vacationmcp.service.BookingsReport;
import no.computas.vacationmcp.service.NotFoundException;
import no.computas.vacationmcp.service.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MCP-serveren er avskrudd i test, så verktøyet testes som den Spring-beanen det er; at
 * {@code bookings_report} dukker opp i {@code tools/list} og svarer over protokollen er verifisert
 * med stdio-røyktesten (se T-16 i {@code SOLUTION-STATUS.md}).
 *
 * <p>Opprydningen følger {@code BookingToolsTest}: {@code DELETE FROM bookings} både før og etter
 * hver test, siden test-databasen {@code build/test-vacation.db} overlever mellom kjøringer og en
 * rapport summerer <em>alt</em> som ligger der.
 *
 * <p><b>Alle forventede tall er regnet ut for hånd mot {@code data.sql}.</b> Reisemål 3 (Kyoto
 * Machiya) er hovedeksempelet: 1600 per natt, ingen sesongpris, og én periode
 * 2026-10-01→2026-11-30 med kapasitet 3. Perioden er 60 netter, så kapasiteten i plassdøgn er
 * 3 × 60 = <b>180</b> — nevneren i alle beleggstallene under. Reisemål 1 (Lofoten) brukes der
 * flere reisemål må være med; periode 1 (2026-07-01→2026-08-31) har sesongpris 2200 og kapasitet 6.
 */
@SpringBootTest
class ReportToolsTest {

    private static final long LOFOTEN = 1L;
    private static final long KYOTO = 3L;

    /** Kyotos eneste periode: id 4, 2026-10-01→2026-11-30, kapasitet 3, 60 netter. */
    private static final long KYOTO_PERIOD = 4L;
    private static final int KYOTO_CAPACITY_NIGHTS = 180;

    private static final LocalDate FROM = LocalDate.of(2026, 10, 5);
    private static final LocalDate TO = LocalDate.of(2026, 10, 8); // 3 netter

    @Autowired
    private ReportTools tools;

    @Autowired
    private BookingService bookings;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clearBookings() {
        jdbc.update("DELETE FROM bookings");
    }

    @Test
    void withoutBookingsEveryDestinationIsListedWithZeros() {
        BookingsReport report = tools.bookingsReport(null, null, null);

        // Filtrene ekkoes tilbake som null — «ikke filtrert», ikke «ingenting».
        assertNull(report.from());
        assertNull(report.to());
        assertNull(report.destinationId());
        assertEquals(5, report.destinations());
        assertEquals(5, report.perDestination().size());

        assertEquals(0, report.totals().bookings());
        assertEquals(0.0, report.totals().revenue());
        assertEquals(0.0, report.totals().pendingRevenue());
        assertEquals(0, report.totals().bookedNights());
        assertEquals(0.0, report.totals().occupancyRate());
        // Nevneren finnes selv uten bookinger: summen av kapasitet × netter for alle seks
        // periodene i data.sql (366 + 360 + 484 + 180 + 1216 + 476).
        assertEquals(3082, report.totals().capacityNights());
    }

    @Test
    void aPeriodLineDescribesTheAvailabilityRowItWasComputedFrom() {
        BookingsReport.PeriodLine periode = onlyPeriod(tools.bookingsReport(KYOTO, null, null));

        assertEquals(KYOTO_PERIOD, periode.availabilityId());
        assertEquals(LocalDate.of(2026, 10, 1), periode.startDate());
        assertEquals(LocalDate.of(2026, 11, 30), periode.endDate());
        assertEquals(3, periode.capacity());
        assertEquals(60, periode.nights());
        assertEquals(KYOTO_CAPACITY_NIGHTS, periode.capacityNights());
    }

    @Test
    void pendingBookingCountsAsRevenueAndIsFlaggedAsUnconfirmed() {
        bookings.createBooking("Ola", KYOTO, FROM, TO, 2);

        BookingsReport report = tools.bookingsReport(KYOTO, null, null);
        BookingsReport.Totals totals = report.totals();

        // 1600 × 3 netter × 2 reisende = 9600, og hele beløpet er ennå ubekreftet.
        assertEquals(9600.0, totals.revenue());
        assertEquals(9600.0, totals.pendingRevenue());
        assertEquals(1, totals.bookings());
        assertEquals(2, totals.travelers());
        assertEquals(0, totals.cancelledBookings());
        assertEquals(0.0, totals.cancelledRevenue());
        // Belegg i plassdøgn: 2 reisende × 3 netter = 6 av 180.
        assertEquals(6, totals.bookedNights());
        assertEquals(KYOTO_CAPACITY_NIGHTS, totals.capacityNights());
        assertEquals(0.0333, totals.occupancyRate());
        // Periodelinja bærer de samme tallene, og omsetningen føres på perioden som dekker oppholdet.
        assertEquals(1, onlyPeriod(report).bookings());
        assertEquals(9600.0, onlyPeriod(report).revenue());
        assertEquals(6, onlyPeriod(report).bookedNights());
    }

    @Test
    void confirmedBookingStaysInRevenueButLeavesPending() {
        Booking booking = bookings.createBooking("Ola", KYOTO, FROM, TO, 2);
        bookings.updateStatus(booking.id(), BookingStatus.CONFIRMED);

        BookingsReport.Totals totals = tools.bookingsReport(KYOTO, null, null).totals();

        assertEquals(9600.0, totals.revenue());
        assertEquals(0.0, totals.pendingRevenue(), "bekreftet omsetning er ikke lenger usikker");
        assertEquals(6, totals.bookedNights(), "bekreftede bookinger holder fortsatt på plassene");
    }

    @Test
    void cancelledBookingIsKeptOutOfRevenueAndOccupancyButReportedSeparately() {
        Booking booking = bookings.createBooking("Ola", KYOTO, FROM, TO, 2);
        bookings.cancel(booking.id());

        BookingsReport report = tools.bookingsReport(KYOTO, null, null);
        BookingsReport.Totals totals = report.totals();

        assertEquals(0.0, totals.revenue(), "kansellert omsetning er ikke omsetning");
        assertEquals(0.0, totals.pendingRevenue());
        assertEquals(0, totals.bookings());
        assertEquals(0, totals.travelers());
        // Men den er ikke usynlig: modellen skal kunne si hva som gikk tapt.
        assertEquals(1, totals.cancelledBookings());
        assertEquals(9600.0, totals.cancelledRevenue());
        // Og plassene er frigjort — samme regel som kapasitetssjekken i T-11.
        assertEquals(0, totals.bookedNights());
        assertEquals(0.0, totals.occupancyRate());
        assertEquals(0, onlyPeriod(report).bookings());
        assertEquals(0.0, onlyPeriod(report).revenue());
    }

    @Test
    void revenueIsSummedAcrossBookingsAndDestinationsAndSortedHighestFirst() {
        // Lofoten, periode 1 med sesongpris 2200: 2200 × 7 netter × 2 reisende = 30800.
        bookings.createBooking("Nina", LOFOTEN, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 8), 2);
        // Kyoto: 9600 + (1600 × 2 netter × 1 reisende) = 12800.
        bookings.createBooking("Ola", KYOTO, FROM, TO, 2);
        bookings.createBooking("Kari", KYOTO, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22), 1);

        BookingsReport report = tools.bookingsReport(null, null, null);

        assertEquals(43600.0, report.totals().revenue());
        assertEquals(3, report.totals().bookings());
        assertEquals(5, report.totals().travelers(), "reisende telles i hoder, ikke plassdøgn");
        assertEquals(12800.0, line(report, KYOTO).totals().revenue());
        // Lofoten (30800) foran Kyoto (12800); resten står med 0 og sorteres på id.
        assertEquals(
                List.of(1L, 3L, 2L, 4L, 5L),
                report.perDestination().stream().map(BookingsReport.DestinationLine::destinationId).toList());
        assertEquals("Lofoten Rorbuer", report.perDestination().getFirst().name());
        assertEquals("Norge", report.perDestination().getFirst().country());
    }

    @Test
    void destinationFilterNarrowsTheReportToOneLine() {
        bookings.createBooking("Nina", LOFOTEN, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 8), 2);
        bookings.createBooking("Ola", KYOTO, FROM, TO, 2);

        BookingsReport report = tools.bookingsReport(KYOTO, null, null);

        assertEquals(KYOTO, report.destinationId());
        assertEquals(1, report.destinations());
        assertEquals(KYOTO, report.perDestination().getFirst().destinationId());
        // Totalene er da nøyaktig den ene linja — Lofoten-omsetningen er ute.
        assertEquals(9600.0, report.totals().revenue());
        assertEquals(report.perDestination().getFirst().totals(), report.totals());
    }

    @Test
    void unknownDestinationIsAnErrorAndNotAnEmptyReport() {
        NotFoundException feil = assertThrows(
                NotFoundException.class, () -> tools.bookingsReport(999L, null, null));

        assertTrue(feil.getMessage().contains("999"), feil.getMessage());
    }

    @Test
    void fromMustBeBeforeTo() {
        ValidationException feil = assertThrows(
                ValidationException.class,
                () -> tools.bookingsReport(null, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 5)));

        assertEquals("fra-dato må være før til-dato", feil.getMessage());
    }

    @Test
    void dateWindowClipsBothSidesOfTheOccupancyFraction() {
        bookings.createBooking("Ola", KYOTO, FROM, TO, 2);

        // Vindu 2026-10-01→2026-10-11: 10 netter av perioden, altså 3 × 10 = 30 plassdøgn.
        BookingsReport report =
                tools.bookingsReport(KYOTO, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 11));
        BookingsReport.PeriodLine periode = onlyPeriod(report);

        assertEquals(LocalDate.of(2026, 10, 1), periode.startDate());
        assertEquals(LocalDate.of(2026, 10, 11), periode.endDate(), "perioden vises klippet mot vinduet");
        assertEquals(10, periode.nights());
        assertEquals(30, periode.capacityNights());
        assertEquals(6, periode.bookedNights());
        assertEquals(0.2, periode.occupancyRate());
    }

    @Test
    void revenueIsNotProratedWhenOnlyPartOfTheStayIsInsideTheWindow() {
        bookings.createBooking("Ola", KYOTO, FROM, TO, 2); // 10-05 → 10-08

        // Vinduet slutter 10-07, så bare 2 av de 3 nettene er inne.
        BookingsReport.Totals totals = tools.bookingsReport(
                        KYOTO, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7))
                .totals();

        assertEquals(9600.0, totals.revenue(), "beløpet ble fakturert én gang og deles ikke på netter");
        assertEquals(1, totals.bookings());
        assertEquals(4, totals.bookedNights(), "belegget teller bare nettene inne i vinduet");
    }

    @Test
    void bookingsOutsideTheWindowAreNotCounted() {
        bookings.createBooking("Ola", KYOTO, FROM, TO, 2); // oktober

        BookingsReport.Totals totals = tools.bookingsReport(
                        KYOTO, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10))
                .totals();

        assertEquals(0.0, totals.revenue());
        assertEquals(0, totals.bookings());
        assertEquals(0, totals.bookedNights());
        // Nevneren står igjen: 9 netter av perioden × kapasitet 3.
        assertEquals(27, totals.capacityNights());
        assertEquals(0.0, totals.occupancyRate());
    }

    @Test
    void occupancyIsNullWhenNoPeriodOverlapsTheWindow() {
        BookingsReport report =
                tools.bookingsReport(KYOTO, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 7, 1));

        assertEquals(List.of(), report.perDestination().getFirst().periods());
        assertEquals(0, report.totals().capacityNights());
        assertNull(report.totals().occupancyRate(), "uten en nevner er belegg udefinert, ikke 0 %");
    }

    @Test
    void occupancyReachesOneHundredPercentWhenEverySeatIsTaken() {
        // Kyoto har kapasitet 3, og T-11-regelen slipper aldri gjennom flere på samme datoer.
        bookings.createBooking("Ola", KYOTO, FROM, TO, 3);

        // Vinduet er nøyaktig oppholdet: 3 plasser × 3 netter = 9 plassdøgn, alle tatt.
        BookingsReport.Totals totals = tools.bookingsReport(KYOTO, FROM, TO).totals();

        assertEquals(9, totals.capacityNights());
        assertEquals(9, totals.bookedNights());
        assertEquals(1.0, totals.occupancyRate(), "belegget kan ikke overstige 100 %");
    }

    private static BookingsReport.DestinationLine line(BookingsReport report, long destinationId) {
        return report.perDestination().stream()
                .filter(line -> line.destinationId() == destinationId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Ingen linje for reisemål " + destinationId));
    }

    private static BookingsReport.PeriodLine onlyPeriod(BookingsReport report) {
        List<BookingsReport.PeriodLine> perioder = report.perDestination().getFirst().periods();
        assertEquals(1, perioder.size(), "Kyoto har én tilgjengelighetsperiode i data.sql");
        return perioder.getFirst();
    }
}
