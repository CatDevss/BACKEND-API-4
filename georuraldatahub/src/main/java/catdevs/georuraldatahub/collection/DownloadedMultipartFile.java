package catdevs.georuraldatahub.collection;

import java.io.*;
import java.nio.file.Files;
import org.springframework.web.multipart.MultipartFile;

/** Replays the downloaded bytes through the same ingest API as a manual upload. */
public final class DownloadedMultipartFile implements MultipartFile {
    private final String filename;
    private final String contentType;
    private final byte[] content;
    public DownloadedMultipartFile(String filename, String contentType, byte[] content) {
        this.filename = filename; this.contentType = contentType; this.content = content;
    }
    public String getName() { return "file"; }
    public String getOriginalFilename() { return filename; }
    public String getContentType() { return contentType; }
    public boolean isEmpty() { return content.length == 0; }
    public long getSize() { return content.length; }
    public byte[] getBytes() { return content.clone(); }
    public InputStream getInputStream() { return new ByteArrayInputStream(content); }
    public void transferTo(File destination) throws IOException { Files.write(destination.toPath(), content); }
}
