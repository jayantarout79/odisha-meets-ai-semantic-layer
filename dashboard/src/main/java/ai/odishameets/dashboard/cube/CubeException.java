package ai.odishameets.dashboard.cube;

public class CubeException extends RuntimeException {
    public CubeException(String message) {
        super(message);
    }

    public CubeException(String message, Throwable cause) {
        super(message, cause);
    }
}
