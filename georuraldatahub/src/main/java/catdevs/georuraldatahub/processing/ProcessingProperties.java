package catdevs.georuraldatahub.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@org.springframework.validation.annotation.Validated
@Component
@ConfigurationProperties(prefix = "processing")
public class ProcessingProperties {
    public static final int MAX_METADATA_BYTES = 1048576;
    @jakarta.validation.constraints.Positive
    private int timeoutSeconds = 120;
    @jakarta.validation.constraints.Positive
    private long maxInputBytes = 1073741824L;
    @jakarta.validation.constraints.Positive
    private int maxOutputBytes = 134217728;
    @jakarta.validation.constraints.Positive
    private int maxFeatures = 100000;
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int value) { timeoutSeconds = value; }
    public long getMaxInputBytes() { return maxInputBytes; }
    public void setMaxInputBytes(long value) { maxInputBytes = value; }
    public int getMaxOutputBytes() { return maxOutputBytes; }
    public void setMaxOutputBytes(int value) { maxOutputBytes = value; }
    public int getMaxFeatures() { return maxFeatures; }
    public void setMaxFeatures(int value) { maxFeatures = value; }
}
