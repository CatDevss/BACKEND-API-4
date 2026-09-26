package catdevs.georuraldatahub.exception;

public class VersionNotFoundException extends RuntimeException {
    public VersionNotFoundException(String message) {
        super(message);
    }
}