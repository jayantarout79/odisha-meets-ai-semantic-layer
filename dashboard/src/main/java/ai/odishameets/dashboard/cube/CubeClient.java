package ai.odishameets.dashboard.cube;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Thin client for Cube's REST API (/load).
 *
 * - Signs a short-lived HS256 JWT with CUBEJS_API_SECRET (required once dev mode is off).
 * - Handles Cube's "Continue wait" long-polling: Cube answers 200 + {"error":"Continue wait"}
 *   while a query is still running, and the client is expected to retry.
 */
@Component
public class CubeClient {

    private static final Logger log = LoggerFactory.getLogger(CubeClient.class);
    private static final String CONTINUE_WAIT = "Continue wait";
    private static final int MAX_POLLS = 60;

    private final CubeProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public CubeClient(CubeProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public List<Map<String, Object>> load(CubeQuery query) {
        String body;
        try {
            body = mapper.writeValueAsString(Map.of("query", query.toMap(props.timezone())));
        } catch (IOException e) {
            throw new CubeException("Could not serialize Cube query", e);
        }

        for (int attempt = 1; attempt <= MAX_POLLS; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(props.apiUrl() + "/load"))
                    .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            String token = token();
            if (!token.isEmpty()) {
                builder.header("Authorization", token);
            }
            HttpRequest request = builder.build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                Map<String, Object> json = mapper.readValue(response.body(), new TypeReference<>() {});
                Object error = json.get("error");
                if (CONTINUE_WAIT.equals(error)) {
                    Thread.sleep(500);
                    continue;
                }
                if (error != null || response.statusCode() >= 400) {
                    throw new CubeException("Cube returned " + response.statusCode() + ": " + error);
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) json.get("data");
                return data == null ? List.of() : data;
            } catch (IOException e) {
                throw new CubeException("Cube is unreachable at " + props.apiUrl(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CubeException("Interrupted while waiting for Cube", e);
            }
        }
        log.warn("Cube query still running after {} polls: {}", MAX_POLLS, body);
        throw new CubeException("Cube query timed out");
    }

    /** Minimal HS256 JWT so we don't pull in a JWT library for one header. */
    private String token() {
        if (props.apiSecret() == null || props.apiSecret().isBlank()) {
            return ""; // dev mode accepts unauthenticated calls
        }
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        long now = Instant.now().getEpochSecond();
        String header = enc.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(
                ("{\"iat\":" + now + ",\"exp\":" + (now + 300) + ",\"app\":\"oma-dashboard\"}")
                        .getBytes(StandardCharsets.UTF_8));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.apiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = enc.encodeToString(
                    mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8)));
            return header + "." + payload + "." + signature;
        } catch (Exception e) {
            throw new CubeException("Could not sign Cube token", e);
        }
    }
}
