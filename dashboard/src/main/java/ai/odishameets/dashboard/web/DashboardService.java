package ai.odishameets.dashboard.web;

import ai.odishameets.dashboard.cube.CubeClient;
import ai.odishameets.dashboard.cube.CubeQuery;
import ai.odishameets.dashboard.web.DashboardFilters.DateRange;
import ai.odishameets.dashboard.web.DashboardModels.Chart;
import ai.odishameets.dashboard.web.DashboardModels.DashboardResponse;
import ai.odishameets.dashboard.web.DashboardModels.Dataset;
import ai.odishameets.dashboard.web.DashboardModels.FilterOptions;
import ai.odishameets.dashboard.web.DashboardModels.Kpi;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * All dashboard panels are predefined here as Cube queries (measures + dimensions + filters).
 * Metric logic lives in the Cube model, not in Java: this class only decides which certified
 * metrics each panel shows and which UI filters apply to it.
 */
@Service
public class DashboardService {

    private static final ZoneId CT = ZoneId.of("America/Chicago");
    private static final List<String> HOURS_ORDER = List.of("<5", "5-10", "10-20", ">20");

    private final CubeClient cube;
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    public DashboardService(CubeClient cube) {
        this.cube = cube;
    }

    public DashboardResponse build(DashboardFilters f) {
        long started = System.currentTimeMillis();
        DateRange r = f.range(LocalDate.now(CT));
        String from = r == null ? null : r.from();
        String to = r == null ? null : r.to();
        String g = f.granularity();

        // ---- predefined queries, fired in parallel ----
        var signupKpis = async(() -> one(cube.load(signups(f).measures(
                "signups.count", "signups.wants_call", "signups.with_booking",
                "signups.joined_mission_rate", "signups.booking_conversion_rate")
                .time("signups.created_at", from, to, null))));
        var bookingKpis = async(() -> one(cube.load(bookings(f).measures(
                "bookings.count", "bookings.completed", "bookings.cancelled", "bookings.upcoming",
                "bookings.needs_status_update", "bookings.show_up_rate", "bookings.avg_duration_minutes")
                .time("bookings.created_at", from, to, null))));
        var downloadKpis = async(() -> one(cube.load(downloads(f).measures(
                "downloads.count", "downloads.unique_downloaders")
                .time("downloads.created_at", from, to, null))));
        var launchpadKpis = async(() -> one(cube.load(CubeQuery.create().measures(
                "launchpad_applications.count", "launchpad_applications.avg_ai_rating",
                "launchpad_applications.used_ai_rate")
                .time("launchpad_applications.created_at", from, to, null))));
        var buildClubBySession = async(() -> cube.load(buildClub(f)
                .measures("build_club_registrations.count")
                .dimensions("build_club_sessions.title")
                .time("build_club_registrations.created_at", from, to, null)
                .orderDesc("build_club_registrations.count")));

        var signupTrend = async(() -> cube.load(signups(f).measures("signups.count")
                .time("signups.created_at", from, to, g)));
        var bookingTrend = async(() -> cube.load(bookings(f)
                .measures("bookings.count", "bookings.completed", "bookings.cancelled", "bookings.upcoming",
                        "bookings.needs_status_update")
                .time("bookings.created_at", from, to, g)));
        var downloadTrend = async(() -> cube.load(downloads(f).measures("downloads.count")
                .time("downloads.created_at", from, to, g)));
        var launchpadTrend = async(() -> cube.load(CubeQuery.create().measures("launchpad_applications.count")
                .time("launchpad_applications.created_at", from, to, g)));
        var buildClubTrend = async(() -> cube.load(buildClub(f).measures("build_club_registrations.count")
                .time("build_club_registrations.created_at", from, to, g)));

        var byPersona = async(() -> cube.load(signups(f).measures("signups.count")
                .dimensions("signups.persona").time("signups.created_at", from, to, null)
                .orderDesc("signups.count")));
        var byCategory = async(() -> cube.load(downloads(f).measures("downloads.count")
                .dimensions("resources.category").time("downloads.created_at", from, to, null)
                .orderDesc("downloads.count")));
        var topResources = async(() -> cube.load(downloads(f).measures("downloads.count")
                .dimensions("resources.title").time("downloads.created_at", from, to, null)
                .orderDesc("downloads.count").limit(10)));
        var launchpadHours = async(() -> cube.load(CubeQuery.create().measures("launchpad_applications.count")
                .dimensions("launchpad_applications.weekly_hours")
                .time("launchpad_applications.created_at", from, to, null)));
        var launchpadStatus = async(() -> cube.load(CubeQuery.create().measures("launchpad_applications.count")
                .dimensions("launchpad_applications.status")
                .time("launchpad_applications.created_at", from, to, null)
                .orderDesc("launchpad_applications.count")));
        var buildClubReferral = async(() -> cube.load(buildClub(f).measures("build_club_registrations.count")
                .dimensions("build_club_registrations.referral_source")
                .time("build_club_registrations.created_at", from, to, null)
                .orderDesc("build_club_registrations.count")));

        // ---- KPI tiles ----
        Map<String, Object> s = signupKpis.join();
        Map<String, Object> b = bookingKpis.join();
        Map<String, Object> d = downloadKpis.join();
        Map<String, Object> l = launchpadKpis.join();
        List<Map<String, Object>> bcSessions = buildClubBySession.join();
        double bcTotal = bcSessions.stream().mapToDouble(row -> num(row, "build_club_registrations.count")).sum();

        List<Kpi> kpis = List.of(
                new Kpi("signups", "Movement signups", num(s, "signups.count"), "number",
                        pct(num(s, "signups.joined_mission_rate")) + " joined the mission"),
                new Kpi("conversion", "Signup → booking", num(s, "signups.booking_conversion_rate"), "percent",
                        fmt(num(s, "signups.with_booking")) + " signups booked a call"),
                new Kpi("bookings", "Bookings", num(b, "bookings.count"), "number",
                        fmt(num(b, "bookings.upcoming")) + " upcoming · " + fmt(num(b, "bookings.completed")) + " completed"),
                new Kpi("showup", "Show-up rate", num(b, "bookings.show_up_rate"), "percent",
                        "completed vs cancelled · avg " + fmt(num(b, "bookings.avg_duration_minutes")) + " min"),
                new Kpi("stale", "Bookings needing status update", num(b, "bookings.needs_status_update"), "number",
                        "session time passed, still 'confirmed'"),
                new Kpi("downloads", "Resource downloads", num(d, "downloads.count"), "number",
                        fmt(num(d, "downloads.unique_downloaders")) + " unique people"),
                new Kpi("launchpad", "Launchpad applications", num(l, "launchpad_applications.count"), "number",
                        "avg AI knowledge " + dec(num(l, "launchpad_applications.avg_ai_rating")) + "/5 · "
                                + pct(num(l, "launchpad_applications.used_ai_rate")) + " used AI"),
                new Kpi("buildclub", "Build Club registrations", bcTotal, "number",
                        "across " + bcSessions.size() + " sessions"));

        // ---- charts ----
        List<Chart> charts = new ArrayList<>();
        charts.add(mergeSeries("activity", "Activity over time", "Per " + g + " (by created date)", "line", false, g,
                List.of(
                        new Series("Signups", signupTrend.join(), "signups.created_at", "signups.count"),
                        new Series("Bookings", bookingTrend.join(), "bookings.created_at", "bookings.count"),
                        new Series("Downloads", downloadTrend.join(), "downloads.created_at", "downloads.count"),
                        new Series("Launchpad", launchpadTrend.join(), "launchpad_applications.created_at", "launchpad_applications.count"),
                        new Series("Build Club", buildClubTrend.join(), "build_club_registrations.created_at", "build_club_registrations.count"))));
        List<Map<String, Object>> bt = bookingTrend.join();
        charts.add(mergeSeries("bookingStatus", "Bookings by status", "Per " + g + " (booking created)", "bar", true, g,
                List.of(
                        new Series("Completed", bt, "bookings.created_at", "bookings.completed"),
                        new Series("Upcoming", bt, "bookings.created_at", "bookings.upcoming"),
                        new Series("Needs status update", bt, "bookings.created_at", "bookings.needs_status_update"),
                        new Series("Cancelled", bt, "bookings.created_at", "bookings.cancelled"))));
        charts.add(new Chart("funnel", "Engagement funnel", "Signups that progressed", "hbar", false,
                List.of("Signed up", "Wanted a call", "Booked a call"),
                List.of(new Dataset("Signups", List.of(num(s, "signups.count"), num(s, "signups.wants_call"),
                        num(s, "signups.with_booking"))))));
        charts.add(category("persona", "Signups by persona", null, "hbar", byPersona.join(),
                "signups.persona", "signups.count", "Signups"));
        charts.add(category("downloadCategory", "Downloads by category", null, "doughnut", byCategory.join(),
                "resources.category", "downloads.count", "Downloads"));
        charts.add(category("topResources", "Top 10 resources", "By downloads", "hbar", topResources.join(),
                "resources.title", "downloads.count", "Downloads"));
        charts.add(category("launchpadHours", "Launchpad: weekly hours available", null, "bar",
                sortBy(launchpadHours.join(), "launchpad_applications.weekly_hours", HOURS_ORDER),
                "launchpad_applications.weekly_hours", "launchpad_applications.count", "Applications"));
        charts.add(category("launchpadStatus", "Launchpad pipeline", "Applications by status", "bar",
                launchpadStatus.join(), "launchpad_applications.status", "launchpad_applications.count", "Applications"));
        charts.add(category("buildClubSessions", "Build Club registrations by session", null, "hbar", bcSessions,
                "build_club_sessions.title", "build_club_registrations.count", "Registrations"));
        charts.add(category("buildClubReferral", "Build Club: how people heard about us", null, "doughnut",
                buildClubReferral.join(), "build_club_registrations.referral_source",
                "build_club_registrations.count", "Registrations"));

        Map<String, Object> applied = new LinkedHashMap<>();
        applied.put("from", from);
        applied.put("to", to);
        applied.put("preset", f.preset());
        applied.put("granularity", g);
        applied.put("persona", f.persona());
        applied.put("category", f.category());
        applied.put("bookingSource", f.bookingSource());
        applied.put("session", f.session());

        return new DashboardResponse(applied, kpis, charts,
                ZonedDateTime.now(CT).format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z")),
                System.currentTimeMillis() - started);
    }

    public FilterOptions filterOptions() {
        var personas = async(() -> values(cube.load(CubeQuery.create().measures("signups.count")
                .dimensions("signups.persona").orderDesc("signups.count")), "signups.persona"));
        var categories = async(() -> values(cube.load(CubeQuery.create().measures("resources.count")
                .dimensions("resources.category").orderDesc("resources.count")), "resources.category"));
        var sources = async(() -> values(cube.load(CubeQuery.create().measures("bookings.count")
                .dimensions("bookings.source").orderDesc("bookings.count")), "bookings.source"));
        var sessions = async(() -> values(cube.load(CubeQuery.create().measures("build_club_sessions.count")
                .dimensions("build_club_sessions.title", "build_club_sessions.session_date")
                .orderAsc("build_club_sessions.session_date")), "build_club_sessions.title"));
        return new FilterOptions(personas.join(), categories.join(), sources.join(), sessions.join());
    }

    // ---------- per-entity query starters: one place where each UI filter is applied ----------

    private CubeQuery signups(DashboardFilters f) {
        return CubeQuery.create().equalsIfPresent("signups.persona", f.persona());
    }

    private CubeQuery bookings(DashboardFilters f) {
        return CubeQuery.create().equalsIfPresent("bookings.source", f.bookingSource());
    }

    private CubeQuery downloads(DashboardFilters f) {
        return CubeQuery.create().equalsIfPresent("resources.category", f.category());
    }

    private CubeQuery buildClub(DashboardFilters f) {
        return CubeQuery.create().equalsIfPresent("build_club_sessions.title", f.session());
    }

    // ---------- shaping helpers ----------

    private record Series(String label, List<Map<String, Object>> rows, String timeDim, String measure) {
    }

    /** Merges several time series onto one sorted label axis (missing periods = 0). */
    private Chart mergeSeries(String id, String title, String subtitle, String type, boolean stacked,
                              String granularity, List<Series> series) {
        TreeSet<String> periods = new TreeSet<>();
        for (Series s : series) {
            for (Map<String, Object> row : s.rows()) {
                Object t = row.get(s.timeDim() + "." + granularity);
                if (t != null) periods.add(t.toString());
            }
        }
        List<String> keys = new ArrayList<>(periods);
        List<Dataset> datasets = new ArrayList<>();
        for (Series s : series) {
            Map<String, Double> byPeriod = new LinkedHashMap<>();
            for (Map<String, Object> row : s.rows()) {
                Object t = row.get(s.timeDim() + "." + granularity);
                if (t != null) byPeriod.put(t.toString(), num(row, s.measure()));
            }
            datasets.add(new Dataset(s.label(), keys.stream().map(k -> byPeriod.getOrDefault(k, 0d)).toList()));
        }
        DateTimeFormatter label = "month".equals(granularity)
                ? DateTimeFormatter.ofPattern("MMM yyyy")
                : DateTimeFormatter.ofPattern("MMM d");
        List<String> labels = keys.stream().map(k -> LocalDateTime.parse(k).format(label)).toList();
        return new Chart(id, title, subtitle, type, stacked, labels, datasets);
    }

    private Chart category(String id, String title, String subtitle, String type, List<Map<String, Object>> rows,
                           String dimension, String measure, String seriesLabel) {
        List<String> labels = rows.stream().map(r -> String.valueOf(r.get(dimension))).toList();
        List<Double> data = rows.stream().map(r -> num(r, measure)).toList();
        return new Chart(id, title, subtitle, type, false, labels, List.of(new Dataset(seriesLabel, data)));
    }

    private static List<Map<String, Object>> sortBy(List<Map<String, Object>> rows, String dim, List<String> order) {
        return rows.stream()
                .sorted((a, b) -> Integer.compare(rank(order, a.get(dim)), rank(order, b.get(dim))))
                .toList();
    }

    private static int rank(List<String> order, Object value) {
        int i = order.indexOf(String.valueOf(value));
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    private static List<String> values(List<Map<String, Object>> rows, String dim) {
        return rows.stream().map(r -> r.get(dim)).filter(v -> v != null && !v.toString().isBlank())
                .map(Object::toString).distinct().toList();
    }

    private static Map<String, Object> one(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    /** Cube returns numbers as strings; nulls (e.g. an average over zero rows) become 0. */
    private static double num(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) return 0;
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String fmt(double v) {
        return String.format("%,d", Math.round(v));
    }

    private static String dec(double v) {
        return String.format("%.1f", v);
    }

    private static String pct(double v) {
        return String.format("%.1f%%", v);
    }

    private <T> CompletableFuture<T> async(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, pool);
    }
}
