package ai.odishameets.dashboard.web;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardFiltersTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private DashboardFilters filters(String preset, String from, String to, String granularity) {
        return new DashboardFilters(preset, from, to, granularity, null, null, null, null);
    }

    @Test
    void allTimeHasNoRange() {
        assertThat(filters(null, null, null, null).range(TODAY)).isNull();
    }

    @Test
    void last30DaysIsInclusiveOfToday() {
        var r = filters("30d", null, null, null).range(TODAY);
        assertThat(r.from()).isEqualTo("2026-10-18");
        assertThat(r.to()).isEqualTo("2026-11-16");
    }

    @Test
    void yearToDateStartsJanFirst() {
        assertThat(filters("ytd", null, null, null).range(TODAY).from()).isEqualTo("2026-01-01");
    }

    @Test
    void customRangeValidatesOrder() {
        assertThatThrownBy(() -> filters("custom", "2026-11-10", "2026-11-01", null).range(TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void granularityDefaultsToWeek() {
        assertThat(filters(null, null, null, "quarter").granularity()).isEqualTo("week");
        assertThat(filters(null, null, null, "month").granularity()).isEqualTo("month");
    }
}
