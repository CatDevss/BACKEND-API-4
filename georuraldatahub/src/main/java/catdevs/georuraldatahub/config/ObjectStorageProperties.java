package catdevs.georuraldatahub.config;


import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "oci.objectstorage")
public record ObjectStorageProperties(String namespace, String bucketName, String compartmentId) {
}
