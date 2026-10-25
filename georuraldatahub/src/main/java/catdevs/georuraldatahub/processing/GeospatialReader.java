package catdevs.georuraldatahub.processing;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Leitura local por GeoTools: o original é copiado para uma área temporária e nunca alterado. */
@Component
public class GeospatialReader {
    private final ProcessingProperties limits;

    public GeospatialReader(ProcessingProperties limits) { this.limits = limits; }

    public record Result(byte[] content, JsonNode metadata) { }

    public Result read(InputStream input, String format, int sourceSrid) throws IOException {
        if (!Set.of("zip", "gpkg", "json", "geojson", "tif", "tiff").contains(format))
            throw new IllegalArgumentException("Formato não suportado pelo leitor geoespacial.");
        if (limits.getMaxInputBytes() < 1 || limits.getMaxOutputBytes() < 1
                || limits.getTimeoutSeconds() < 1 || limits.getMaxFeatures() < 1)
            throw new IllegalStateException("Limites geoespaciais inválidos.");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(limits.getTimeoutSeconds());
        Path directory = Files.createTempDirectory("georural-geotools-");
        try {
            Path source = directory.resolve("input." + format);
            try (var output = Files.newOutputStream(source)) {
                copy(input, output, limits.getMaxInputBytes(), deadline);
            }
            try {
                Result result;
                if (format.equals("tif") || format.equals("tiff")) {
                    result = new GeoTiffMetadataReader().read(source, deadline);
                } else {
                    if (format.equals("zip")) source = unpack(source, deadline);
                    result = new GeoToolsVectorReader(limits).read(source, format, sourceSrid, deadline);
                }
                checkDeadline(deadline);
                if (result.content().length > limits.getMaxOutputBytes()
                        || result.metadata().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > ProcessingProperties.MAX_METADATA_BYTES)
                    throw invalid("Metadados ou conteúdo convertido excedem o limite.");
                return result;
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                String detail = Objects.toString(e.getMessage(), "Não foi possível ler o conteúdo.")
                        .replace(directory.toString(), "[temporário]");
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "Arquivo geoespacial inválido: " + detail, e);
            }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private Path unpack(Path source, long deadline) throws IOException {
        Path destination = Files.createDirectory(source.getParent().resolve("shapefile"));
        long total = 0;
        int entries = 0;
        Set<Path> seen = new HashSet<>();
        List<Path> shapes = new ArrayList<>();
        try (var archive = new ZipInputStream(Files.newInputStream(source))) {
            java.util.zip.ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                checkDeadline(deadline);
                if (++entries > 100) throw invalid("ZIP excede 100 componentes.");
                String name = entry.getName();
                Path relative = Path.of(name);
                if (relative.isAbsolute() || name.contains("\\") || name.contains(":")
                        || Arrays.asList(name.split("/")).contains("..")) throw invalid("Caminho inválido no ZIP.");
                Path target = destination.resolve(relative).normalize();
                if (!target.startsWith(destination) || !seen.add(target)) throw invalid("Componente inválido ou duplicado no ZIP.");
                if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                Files.createDirectories(target.getParent());
                try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    total += copy(archive, output, limits.getMaxOutputBytes() - total, deadline);
                }
                if (name.toLowerCase(Locale.ROOT).endsWith(".shp")) shapes.add(target);
            }
        }
        if (shapes.size() != 1) throw invalid("Envie um ZIP com exatamente um Shapefile e seus componentes.");
        return shapes.getFirst();
    }

    private long copy(InputStream input, OutputStream output, long maximum, long deadline) throws IOException {
        byte[] buffer = new byte[65536];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            checkDeadline(deadline);
            if ((total += count) > maximum) throw invalid("Arquivo ou ZIP expandido excede o limite geoespacial.");
            output.write(buffer, 0, count);
        }
        return total;
    }

    static void checkDeadline(long deadline) {
        if (Thread.currentThread().isInterrupted()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Leitura geoespacial interrompida.");
        if (System.nanoTime() >= deadline) throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                "Leitura geoespacial excedeu o prazo configurado.");
    }

    static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, message);
    }
}
