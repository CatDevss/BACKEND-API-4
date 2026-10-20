package catdevs.georuraldatahub.collection;

import java.time.Instant;
import java.util.List;
import java.util.HexFormat;
import java.security.MessageDigest;

public record CollectedData(String filename, String format, byte[] content, List<Original> originals,
        Metadata metadata) {
    public record Original(String filename, String origin, String contentType, byte[] content) {
        public String sha256() {
            try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
            catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        }
    }
    public record OriginalMetadata(String filename, String origin, String contentType, long size, String sha256) {}
    public record Metadata(String product, String competence, String format, String originFormat,
            String scope, String collectedAt, String documentation, List<String> limitations,
            List<OriginalMetadata> originals) {}
}
