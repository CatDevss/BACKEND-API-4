package catdevs.georuraldatahub.config;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.retrier.RetryConfiguration;
import com.oracle.bmc.waiter.MaxAttemptsTerminationStrategy;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;

@Configuration
public class ObjectStorageConfig {
    @Bean
    public ObjectStorageClient objectStorageClient(
            @Value("${oci.config-file:}") String configFile,
            @Value("${oci.config-profile:DEFAULT}") String profile) throws IOException {
        var fileConfig = ConfigFileReader.parse(configPath(configFile).toString(), profile);
        return ObjectStorageClient.builder()
                .configuration(ClientConfiguration.builder().connectionTimeoutMillis(10000).readTimeoutMillis(60000)
                        .retryConfiguration(RetryConfiguration.builder()
                                .terminationStrategy(new MaxAttemptsTerminationStrategy(2)).build()).build())
                .build(new ConfigFileAuthenticationDetailsProvider(fileConfig));
    }

    static Path configPath(String configured) {
        if (configured != null && !configured.isBlank()) {
            String path = configured.trim();
            if (path.startsWith("~/")) path = System.getProperty("user.home") + path.substring(1);
            return Path.of(path);
        }
        var homeConfig = Path.of(System.getProperty("user.home"), ".oci", "config");
        return List.of(Path.of(".oci/config"), Path.of("georuraldatahub/.oci/config"), homeConfig).stream()
                .filter(Files::isRegularFile).findFirst().orElse(homeConfig);
    }
}
