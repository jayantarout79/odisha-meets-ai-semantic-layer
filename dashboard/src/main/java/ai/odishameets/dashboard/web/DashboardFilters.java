package ai.odishameets.dashboard.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Filters coming from the UI. Each filter is applied only to the panels it makes sense for:
 *   persona       -> movement signups
 *   category      -> resource downloads
 *   bookingSource -> bookings
 *   session       -> Build Club registrations
 *   date range    -> every panel (on each entity's created date)
 */
public record DashboardFilters(
        String preset,
        String from,
        String to,
        String granularity,
        String persona,
        String category,
        String bookingSource,
        String session) {

    public DashboardFilters {
        preset = blankTo(preset, "all");
        granularity = "month".equals(granularity) ? "month" : "week";
    }

    /** Resolves the preset into an inclusive [from, to] range, or null for all time. */
    public DateRange range(LocalDate today) {
        return switch (preset) {
            case "30d" -> new DateRange(today.minusDays(29).toString(), today.toString());
            case "90d" -> new DateRange(today.minusDays(89).toString(), today.toString());
            case "ytd" -> new DateRange(today.withDayOfYear(1).toString(), today.toString());
            case "custom" -> customRange();
            default -> null;
        };
    }

    private DateRange customRange() {
        try {
            LocalDate f = LocalDate.parse(from);
            LocalDate t = LocalDate.parse(to);
            if (t.isBefore(f)) {
                throw new IllegalArgumentException("'to' date is before 'from' date");
            }
            return new DateRange(f.toString(), t.toString());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("Custom range needs valid from/to dates (yyyy-MM-dd)");
        }
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record DateRange(String from, String to) {
    }
}
