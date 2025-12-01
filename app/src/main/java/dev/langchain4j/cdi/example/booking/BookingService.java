package dev.langchain4j.cdi.example.booking;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class BookingService {

    private static final Logger LOGGER = Logger.getLogger(BookingService.class.getName());

    DataSource dataSource;

    private DataSource resolveDataSource() {
        if (dataSource != null) {
            return dataSource;
        }
        try {
            InitialContext ic = new InitialContext();
            try {
                dataSource = (DataSource) ic.lookup("java:comp/env/jdbc/CarBookingDS");
                System.out.println("Resolved DS via java:comp/env/jdbc/CarBookingDS");
                return dataSource;
            } catch (NamingException ignore) {
                // fall through
            }
            dataSource = (DataSource) ic.lookup("jdbc/CarBookingDS");
            System.out.println("Resolved DS via global jdbc/CarBookingDS");
            return dataSource;
        } catch (NamingException ne) {
            throw new RuntimeException("Unable to resolve DataSource jdbc/CarBookingDS via JNDI", ne);
        }
    }

    // Database access
    private Booking checkBookingExists(String bookingNumber, String name, String surname) {
        String sql = """
                SELECT b.BOOKING_NUMBER, b.START_DATE, b.END_DATE, b.CANCELED, b.CAR_MODEL, c.NAME, c.SURNAME
                FROM BOOKING b
                JOIN CUSTOMER c ON b.CUSTOMER_ID = c.ID
                WHERE TRIM(b.BOOKING_NUMBER) = TRIM(?) 
                  AND UPPER(c.NAME) = UPPER(TRIM(?)) 
                  AND UPPER(c.SURNAME) = UPPER(TRIM(?))
                """;
        System.out.println("SQL (checkBookingExists): " + sql.replace('\n', ' '));
        try (Connection con = resolveDataSource().getConnection()) {
            System.out.println("DB URL (checkBookingExists): " + con.getMetaData().getURL());

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, bookingNumber);
                ps.setString(2, name);
                ps.setString(3, surname);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Booking booking = new Booking();
                        booking.setBookingNumber(rs.getString("BOOKING_NUMBER"));
                        Date start = rs.getDate("START_DATE");
                        Date end = rs.getDate("END_DATE");
                        System.out.println("Start date: " + start + " end date: " + end);
                        booking.setStart(start != null ? start.toLocalDate() : null);
                        booking.setEnd(end != null ? end.toLocalDate() : null);
                        booking.setCanceled(rs.getShort("CANCELED") != 0);
                        booking.setCarModel(rs.getString("CAR_MODEL"));
                        booking.setCustomer(new Customer(rs.getString("NAME"), rs.getString("SURNAME")));
                        System.out.println(booking);
                        return booking;
                    }
                }
            }

            // Second attempt: swapped order (surname, name) to be user-friendly
            /*try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, bookingNumber);
                ps.setString(2, surname);
                ps.setString(3, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Booking booking = new Booking();
                        booking.setBookingNumber(rs.getString("BOOKING_NUMBER"));
                        Date start = rs.getDate("START_DATE");
                        Date end = rs.getDate("END_DATE");
                        booking.setStart(start != null ? start.toLocalDate() : null);
                        booking.setEnd(end != null ? end.toLocalDate() : null);
                        booking.setCanceled(rs.getShort("CANCELED") != 0);
                        booking.setCarModel(rs.getString("CAR_MODEL"));
                        booking.setCustomer(new Customer(rs.getString("NAME"), rs.getString("SURNAME")));
                        return booking;
                    }
                }
            }*/
            throw new BookingNotFoundException(bookingNumber);
        } catch (SQLException e) {
            throw new RuntimeException("DB error while fetching booking " + bookingNumber, e);
        }
    }

    @Tool("Get booking details given a booking number and customer name and surname")
    public Booking getBookingDetails(String bookingNumber, String name, String surname) {
        System.out.println(
                "DEMO: Calling Tool-getBookingDetails: " + bookingNumber + " and customer: " + name + " " + surname);
        return checkBookingExists(bookingNumber, name, surname);
    }

    @Tool("Get all booking ids for a customer given his name and surname")
    public List<String> getBookingsForCustomer(String name, String surname) {
        System.out.println("DEMO: Calling Tool-getBookingsForCustomer: " + name + " " + surname);

        String sql = """
                SELECT b.BOOKING_NUMBER
                FROM BOOKING b
                JOIN CUSTOMER c ON b.CUSTOMER_ID = c.ID
                WHERE UPPER(c.NAME) = UPPER(TRIM(?))
                  AND UPPER(c.SURNAME) = UPPER(TRIM(?))
                """;
        System.out.println("SQL (getBookingsForCustomer): " + sql.replace('\n', ' '));
        List<String> bookingIds = new ArrayList<>();
        try (Connection con = resolveDataSource().getConnection()) {
            System.out.println("DB URL (getBookingsForCustomer): " + con.getMetaData().getURL());
            // Try provided order
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, surname);
                System.out.println("Bookings query try1 name:" + name + " surname:" + surname);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String bookingId = rs.getString("BOOKING_NUMBER");
                        System.out.println("Booking ID: " + bookingId);
                        if (!bookingIds.contains(bookingId)) {
                            bookingIds.add(bookingId);
                        }
                    }
                }
            }
            // Try swapped order
            /*try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, surname);
                ps.setString(2, name);
                System.out.println("Bookings query try2 (swapped) name:" + surname + " surname:" + name);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String bookingId = rs.getString("BOOKING_NUMBER");
                        if (!bookingIds.contains(bookingId)) {
                            bookingIds.add(bookingId);
                        }
                    }
                }
            }*/
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            throw new RuntimeException("DB error while listing bookings for customer " + name + " " + surname, e);
        }
        return bookingIds;
    }

    public void checkCancelPolicy(Booking booking) {

        System.out.println("checkCancelPolicy: " + booking);
        // Reservations can be cancelled up to 7 days prior to the start of the booking
        // period
        if (LocalDate.now().plusDays(7).isAfter(booking.getStart())) {
            System.out.println("checkCancelPolicy: it is too late to cancel booking");
            throw new BookingCannotBeCanceledException(booking.getBookingNumber() + " Too late");
        }

        // If the booking period is less than 3 days, cancellations are not permitted.
        if (booking.getEnd().compareTo(booking.getStart().plusDays(3)) < 0) {
            System.out.println("checkCancelPolicy: it is too short to cancel booking");
            throw new BookingCannotBeCanceledException(booking.getBookingNumber() + " Too short");
        }
    }

    @Tool("Cancel a booking given its booking number and customer name and surname")
    public Booking cancelBooking(String bookingNumber, String name, String surname) {
        LOGGER.info("DEMO: Calling Tool-cancelBooking " + bookingNumber + " for customer: " + name + " " + surname);

        Booking booking = checkBookingExists(bookingNumber, name, surname);
        System.out.println("bookingNumber : " + bookingNumber + " exists");
        if (booking.isCanceled()) {
            System.out.println("bookingNumber : " + bookingNumber + " is already canceled");
            throw new BookingCannotBeCanceledException(bookingNumber);
        }

        checkCancelPolicy(booking);
        System.out.println("Attempt cancelling booking number : " + bookingNumber);

        String sql = "UPDATE BOOKING SET CANCELED = 1 WHERE BOOKING_NUMBER = ?";
        try (Connection con = resolveDataSource().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, bookingNumber);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new BookingNotFoundException(bookingNumber);
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB error while canceling booking " + bookingNumber, e);
        }
        booking.setCanceled(true);
        System.out.println("Booking " + booking.getBookingNumber());
        return booking;
    }
}
