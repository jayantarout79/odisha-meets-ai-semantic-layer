package ai.odishameets.dashboard.cube;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small fluent builder for Cube REST API queries.
 * The dashboard never writes SQL: it only names measures, dimensions and filters
 * that are defined once in the Cube data model.
 */
public final class CubeQuery {

    private final List<String> measures = new ArrayList<>();
    private final List<String> dimensions = new ArrayList<>();
    private final List<Map<String, Object>> filters = new ArrayList<>();
    private final List<Map<String, Object>> timeDimensions = new ArrayList<>();
    private final Map<String, String> order = new LinkedHashMap<>();
    private Integer limit;

    public static CubeQuery create() {
        return new CubeQuery();
    }

    public CubeQuery measures(String... names) {
        measures.addAll(List.of(names));
        return this;
    }

    public CubeQuery dimensions(String... names) {
        dimensions.addAll(List.of(names));
        return this;
    }

    /** Adds an "equals" filter only when a value is present, so optional UI filters stay simple. */
    public CubeQuery equalsIfPresent(String member, String value) {
        if (value != null && !value.isBlank()) {
            filters.add(Map.of("member", member, "operator", "equals", "values", List.of(value)));
        }
        return this;
    }

    /** Time filter (and optional bucketing). from/to are ISO dates; null means "all time". */
    public CubeQuery time(String dimension, String from, String to, String granularity) {
        Map<String, Object> td = new LinkedHashMap<>();
        td.put("dimension", dimension);
        if (from != null && to != null) {
            td.put("dateRange", List.of(from, to));
        }
        if (granularity != null) {
            td.put("granularity", granularity);
        }
        timeDimensions.add(td);
        return this;
    }

    public CubeQuery orderDesc(String member) {
        order.put(member, "desc");
        return this;
    }

    public CubeQuery orderAsc(String member) {
        order.put(member, "asc");
        return this;
    }

    public CubeQuery limit(int value) {
        this.limit = value;
        return this;
    }

    Map<String, Object> toMap(String timezone) {
        Map<String, Object> q = new LinkedHashMap<>();
        if (!measures.isEmpty()) q.put("measures", measures);
        if (!dimensions.isEmpty()) q.put("dimensions", dimensions);
        if (!filters.isEmpty()) q.put("filters", filters);
        if (!timeDimensions.isEmpty()) q.put("timeDimensions", timeDimensions);
        if (!order.isEmpty()) q.put("order", order);
        if (limit != null) q.put("limit", limit);
        if (timezone != null) q.put("timezone", timezone);
        return q;
    }
}
