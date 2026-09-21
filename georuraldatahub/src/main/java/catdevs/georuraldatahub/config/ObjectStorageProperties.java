package catdevs.georuraldatahub.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Guarda as informações do bucket (nome, namespace, etc.) definidas no application.properties.
 */

@ConfigurationProperties(prefix = "oci.objectstorage")
public record ObjectStorageProperties(String namespace, String bucketName, String compartmentId) {
}
