package no.computas.vacationmcp.service;

/** Kastes når input eller en forretningsregel ikke er oppfylt (f.eks. ugyldige datoer, full kapasitet). */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
