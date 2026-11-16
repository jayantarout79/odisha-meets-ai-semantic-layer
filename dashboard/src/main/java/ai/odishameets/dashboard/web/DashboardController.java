package ai.odishameets.dashboard.web;

import ai.odishameets.dashboard.cube.CubeException;
import ai.odishameets.dashboard.web.DashboardModels.DashboardResponse;
import ai.odishameets.dashboard.web.DashboardModels.FilterOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletionException;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String persona,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String bookingSource,
            @RequestParam(required = false) String session) {
        return service.build(new DashboardFilters(preset, from, to, granularity, persona, category, bookingSource, session));
    }

    @GetMapping("/filters")
    public FilterOptions filters() {
        return service.filterOptions();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({CubeException.class, CompletionException.class})
    public ResponseEntity<Map<String, String>> cubeDown(RuntimeException e) {
        Throwable root = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
        log.error("Dashboard query failed", root);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Semantic layer error: " + root.getMessage()));
    }
}
