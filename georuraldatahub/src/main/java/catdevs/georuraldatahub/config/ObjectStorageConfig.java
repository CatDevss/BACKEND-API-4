package catdevs.georuraldatahub.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;

/**
 * Configura a conexão com o Object Storage da OCI (onde os arquivos ficam
 * guardados).
 */

@Configuration
public class ObjectStorageConfig {

    /**
     * Cria o client que a aplicação usa para falar com o bucket.
     */

    @Bean
    public ObjectStorageClient objectStorageClient() throws IOException {
        var fileConfig = ConfigFileReader.parse(
                "/workspaces/BACKEND-API-4/georuraldatahub/.oci/config",
                "DEFAULT");

        var provider = new ConfigFileAuthenticationDetailsProvider(fileConfig);

        return ObjectStorageClient.builder()
                .build(provider);
    }
}