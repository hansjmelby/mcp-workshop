package no.computas.vacationmcp.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Livssyklus for en booking, med en eksplisitt tilstandsmaskin.
 *
 * <pre>
 *   PENDING ──▶ CONFIRMED ──▶ PAID ──▶ COMPLETED
 *      │            │           │
 *      └────────────┴───────────┴──▶ CANCELLED
 * </pre>
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    PAID,
    COMPLETED,
    CANCELLED;

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(CONFIRMED, CANCELLED),
            CONFIRMED, EnumSet.of(PAID, CANCELLED),
            PAID, EnumSet.of(COMPLETED, CANCELLED),
            COMPLETED, EnumSet.noneOf(BookingStatus.class),
            CANCELLED, EnumSet.noneOf(BookingStatus.class));

    /** True hvis det er lov å gå fra denne statusen til {@code target}. */
    public boolean canTransitionTo(BookingStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }
}
