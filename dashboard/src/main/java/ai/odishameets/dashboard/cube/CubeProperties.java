package ai.odishameets.dashboard.cube;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cube")
public record CubeProperties(String apiUrl, String apiSecret, String timezone, int requestTimeoutSeconds) {
}
