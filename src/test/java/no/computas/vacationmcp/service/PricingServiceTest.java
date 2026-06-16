package no.computas.vacationmcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PricingServiceTest {

    private static final long LOFOTEN = 1L; // 1850/natt; periode 1 har sesongpris 2200

    @Autowired
    private PricingService pricing;

    @Test
    void usesSeasonPriceWhenSet() {
        // Periode 1: 2026-07-01..08-31, sesongpris 2200
        Quote quote = pricing.quote(LOFOTEN, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 13), 2);
        assertEquals(2200.0, quote.pricePerNight(), 0.001);
        assertEquals(3, quote.nights());
        assertEquals(2200.0 * 3 * 2, quote.totalPrice(), 0.001);
    }

    @Test
    void fallsBackToNightlyPriceWhenNoSeasonPrice() {
        // Periode 2: 2026-09-01..10-31, ingen sesongpris -> bruk 1850
        Quote quote = pricing.quote(LOFOTEN, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13), 1);
        assertEquals(1850.0, quote.pricePerNight(), 0.001);
        assertEquals(1850.0 * 3, quote.totalPrice(), 0.001);
    }

    @Test
    void rejectsFromNotBeforeTo() {
        LocalDate day = LocalDate.of(2026, 7, 10);
        assertThrows(ValidationException.class, () -> pricing.quote(LOFOTEN, day, day, 1));
    }

    @Test
    void rejectsDatesOutsideAvailability() {
        assertThrows(ValidationException.class,
                () -> pricing.quote(LOFOTEN, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5), 1));
    }

    @Test
    void rejectsZeroTravelers() {
        assertThrows(ValidationException.class,
                () -> pricing.quote(LOFOTEN, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 13), 0));
    }

    @Test
    void rejectsUnknownDestination() {
        assertThrows(NotFoundException.class,
                () -> pricing.quote(999L, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 13), 1));
    }
}
