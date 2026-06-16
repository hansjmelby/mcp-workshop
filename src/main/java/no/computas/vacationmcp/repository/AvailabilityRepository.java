package no.computas.vacationmcp.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import no.computas.vacationmcp.domain.Availability;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Dataaksess for {@code availability}. Datoer lagres som ISO-tekst (yyyy-MM-dd). */
@Repository
public class AvailabilityRepository {

    private static final RowMapper<Availability> MAPPER = (rs, rowNum) -> {
        long id = rs.getLong("id");
        long destinationId = rs.getLong("destination_id");
        LocalDate startDate = LocalDate.parse(rs.getString("start_date"));
        LocalDate endDate = LocalDate.parse(rs.getString("end_date"));
        int capacity = rs.getInt("capacity");
        // Les season_price sist og sjekk wasNull() umiddelbart — wasNull() gjelder siste leste kolonne.
        double seasonPrice = rs.getDouble("season_price");
        Double season = rs.wasNull() ? null : seasonPrice;
        return new Availability(id, destinationId, startDate, endDate, capacity, season);
    };

    private final JdbcTemplate jdbc;

    public AvailabilityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Availability> findByDestinationId(long destinationId) {
        return jdbc.query(
                "SELECT * FROM availability WHERE destination_id = ? ORDER BY start_date",
                MAPPER, destinationId);
    }

    /** Perioder som overlapper [from, to) — nyttig for å vise hva som er ledig. */
    public List<Availability> findOverlapping(long destinationId, LocalDate from, LocalDate to) {
        return jdbc.query(
                "SELECT * FROM availability WHERE destination_id = ? AND start_date < ? AND end_date > ? "
                        + "ORDER BY start_date",
                MAPPER, destinationId, to.toString(), from.toString());
    }

    /** Den (første) perioden som fullt ut dekker [from, to], hvis noen. */
    public Optional<Availability> findCovering(long destinationId, LocalDate from, LocalDate to) {
        return jdbc.query(
                "SELECT * FROM availability WHERE destination_id = ? AND start_date <= ? AND end_date >= ? "
                        + "ORDER BY start_date LIMIT 1",
                MAPPER, destinationId, from.toString(), to.toString()).stream().findFirst();
    }
}
