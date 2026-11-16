package ai.odishameets.dashboard.web;

import java.util.List;
import java.util.Map;

/** JSON shapes returned to the browser. */
public final class DashboardModels {

    private DashboardModels() {
    }

    /** format: "number" | "percent" | "decimal" */
    public record Kpi(String id, String label, double value, String format, String hint) {
    }

    public record Dataset(String label, List<Double> data) {
    }

    /** type: "line" | "bar" | "hbar" | "doughnut" */
    public record Chart(String id, String title, String subtitle, String type, boolean stacked,
                        List<String> labels, List<Dataset> datasets) {
    }

    public record DashboardResponse(Map<String, Object> applied, List<Kpi> kpis, List<Chart> charts,
                                    String generatedAt, long queryMillis) {
    }

    public record FilterOptions(List<String> personas, List<String> categories,
                                List<String> bookingSources, List<String> sessions) {
    }
}
