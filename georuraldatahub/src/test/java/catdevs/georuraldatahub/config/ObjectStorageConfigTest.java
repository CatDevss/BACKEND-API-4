package catdevs.georuraldatahub.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ObjectStorageConfigTest {
    @Test void explicitPathOverridesDevcontainerLayoutWithoutReadingCredentials() {
        assertEquals(Path.of("/tmp/academic-oci-config"), ObjectStorageConfig.configPath("/tmp/academic-oci-config"));
    }
    @Test void homeShorthandIsExpandedWithoutHardcodedWorkspacePath() {
        assertEquals(Path.of(System.getProperty("user.home"), ".oci", "config"), ObjectStorageConfig.configPath("~/.oci/config"));
    }
}
