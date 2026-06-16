package no.computas.vacationmcp.service;

/** Kastes når en etterspurt rad (destinasjon, booking) ikke finnes. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
