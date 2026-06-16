package no.computas.vacationmcp.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import no.computas.vacationmcp.domain.Destination;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Dataaksess for {@code destinations}. */
@Repository
public class DestinationRepository {

    private static final RowMapper<Destination> MAPPER = (rs, rowNum) -> new Destination(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("country"),
            rs.getString("description"),
            rs.getDouble("price_per_night"),
            rs.getInt("available") == 1);

    private final JdbcTemplate jdbc;

    public DestinationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Destination> findAllAvailable() {
        return jdbc.query("SELECT * FROM destinations WHERE available = 1 ORDER BY id", MAPPER);
    }

    public Optional<Destination> findById(long id) {
        return jdbc.query("SELECT * FROM destinations WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    /**
     * Søk blant tilgjengelige reisemål. Alle parametre er valgfrie ({@code null} = ikke filtrer).
     *
     * @param query            fritekst i navn/beskrivelse
     * @param country          eksakt land
     * @param maxPricePerNight maks pris pr. natt
     */
    public List<Destination> search(String query, String country, Double maxPricePerNight) {
        StringBuilder sql = new StringBuilder("SELECT * FROM destinations WHERE available = 1");
        List<Object> args = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            sql.append(" AND (name LIKE ? OR description LIKE ?)");
            String like = "%" + query.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (country != null && !country.isBlank()) {
            sql.append(" AND country = ?");
            args.add(country.trim());
        }
        if (maxPricePerNight != null) {
            sql.append(" AND price_per_night <= ?");
            args.add(maxPricePerNight);
        }
        sql.append(" ORDER BY id");
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }
}
