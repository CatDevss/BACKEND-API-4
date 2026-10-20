package catdevs.georuraldatahub.collection;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@org.springframework.validation.annotation.Validated
@ConfigurationProperties("collection")
public class CollectionProperties {
    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(268435456)
    private int maxDownloadBytes = 64 * 1024 * 1024;
    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(536870912)
    private int maxTotalBytes = 128 * 1024 * 1024;
    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(10000)
    private int pageSize = 1000;
    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(1000000)
    private int maxFeatures = 100000;
    @jakarta.validation.Valid
    private List<Job> jobs = List.of();
    public int getMaxDownloadBytes() { return maxDownloadBytes; }
    public void setMaxDownloadBytes(int value) { maxDownloadBytes = value; }
    public int getMaxTotalBytes() { return maxTotalBytes; }
    public void setMaxTotalBytes(int value) { maxTotalBytes = value; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int value) { pageSize = value; }
    public int getMaxFeatures() { return maxFeatures; }
    public void setMaxFeatures(int value) { maxFeatures = value; }
    public List<Job> getJobs() { return jobs; }
    public void setJobs(List<Job> value) { jobs = value; }
    // competence may be D-1 only for daily INPE_QUEIMADAS jobs.
    public record Job(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long sourceId,
            @jakarta.validation.Valid @jakarta.validation.constraints.NotNull CollectionRequest request) {}
}
