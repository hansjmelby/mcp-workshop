package no.computas.vacationmcp.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import no.computas.vacationmcp.domain.Booking;
import no.computas.vacationmcp.domain.BookingStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** Dataaksess for {@code bookings}. */
@Repository
public class BookingRepository {

    private static final RowMapper<Booking> MAPPER = (rs, rowNum) -> new Booking(
            rs.getLong("id"),
            rs.getString("customer_name"),
            rs.getLong("destination_id"),
            LocalDate.parse(rs.getString("start_date")),
            LocalDate.parse(rs.getString("end_date")),
            rs.getInt("num_travelers"),
            rs.getDouble("total_price"),
            BookingStatus.valueOf(rs.getString("status")));

    private final JdbcTemplate jdbc;

    public BookingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Setter inn en ny booking og returnerer den genererte id-en. */
    public long insert(Booking booking) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO bookings "
                            + "(customer_name, destination_id, start_date, end_date, num_travelers, total_price, status) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, booking.customerName());
            ps.setLong(2, booking.destinationId());
            ps.setString(3, booking.startDate().toString());
            ps.setString(4, booking.endDate().toString());
            ps.setInt(5, booking.numTravelers());
            ps.setDouble(6, booking.totalPrice());
            ps.setString(7, booking.status().name());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert returnerte ingen generert nøkkel");
        }
        return key.longValue();
    }

    public Optional<Booking> findById(long id) {
        return jdbc.query("SELECT * FROM bookings WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<Booking> findAll() {
        return jdbc.query("SELECT * FROM bookings ORDER BY id", MAPPER);
    }

    public List<Booking> findByStatus(BookingStatus status) {
        return jdbc.query("SELECT * FROM bookings WHERE status = ? ORDER BY id", MAPPER, status.name());
    }

    public void updateStatus(long id, BookingStatus status) {
        jdbc.update("UPDATE bookings SET status = ? WHERE id = ?", status.name(), id);
    }

    /**
     * Sum av reisende i ikke-kansellerte bookinger som overlapper [from, to) for et reisemål.
     * Brukes til kapasitetssjekk.
     */
    public int sumActiveTravelers(long destinationId, LocalDate from, LocalDate to) {
        Integer sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(num_travelers), 0) FROM bookings "
                        + "WHERE destination_id = ? AND status <> 'CANCELLED' AND start_date < ? AND end_date > ?",
                Integer.class, destinationId, to.toString(), from.toString());
        return sum == null ? 0 : sum;
    }
}
