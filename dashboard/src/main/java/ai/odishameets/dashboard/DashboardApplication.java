package ai.odishameets.dashboard;

import ai.odishameets.dashboard.cube.CubeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CubeProperties.class)
public class DashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(DashboardApplication.class, args);
    }
}
