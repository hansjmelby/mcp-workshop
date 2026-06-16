package no.computas.vacationmcp.domain;

/** Et feriereisemål fra {@code destinations}-tabellen. */
public record Destination(
        long id,
        String name,
        String country,
        String description,
        double pricePerNight,
        boolean available) {
}
