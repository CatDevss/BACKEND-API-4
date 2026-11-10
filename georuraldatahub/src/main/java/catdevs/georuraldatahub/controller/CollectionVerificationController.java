package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.collection.*;
import catdevs.georuraldatahub.validation.CsvRecordValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Diagnóstico por download real, sem fonte cadastrada, dicionário, Oracle ou OCI. */
@RestController
@CrossOrigin
@RequestMapping("/fontes/conectores")
public class CollectionVerificationController {
    private final PublishedResources resources;
    private final PublicDataConnectors connectors;
    private final ObjectMapper mapper = new ObjectMapper();
    public CollectionVerificationController(PublishedResources resources, PublicDataConnectors connectors) {
        this.resources = resources; this.connectors = connectors;
    }
    public record Request(@NotBlank String competence, String municipality, String biome, List<Double> bbox) {}
    public record Verification(String status, long records, long bytes, CollectedData.Metadata collection) {}

    @GetMapping("/{product}/edicoes")
    public PublishedResources.Availability editions(@PathVariable CollectionRequest.Product product) throws IOException, InterruptedException {
        try { return resources.availability(product); }
        catch (InterruptedException e) { throw interrupted(e); }
        catch (IOException e) { throw upstream(e); }
    }

    @PostMapping("/{product}/verificacao")
    public Verification verify(@PathVariable CollectionRequest.Product product, @Valid @RequestBody Request request)
            throws IOException, InterruptedException {
        CollectedData data;
        try { data = connectors.collect(new CollectionRequest(product, null, null, request.competence(),
                request.municipality(), request.biome(), request.bbox())); }
        catch (InterruptedException e) { throw interrupted(e); }
        catch (IOException e) { throw upstream(e); }
        long count = 0;
        if (data.format().equals("csv")) {
            try (var reader = new CsvRecordValidator.TableReader(new ByteArrayInputStream(data.content()))) {
                reader.readRecord();
                while (reader.readRecord() != null) count++;
            }
        } else count = mapper.readTree(data.content()).path("features").size();
        return new Verification(count == 0 ? "SEM_REGISTROS" : "DISPONIVEL", count, data.content().length, data.metadata());
    }
    private org.springframework.web.server.ResponseStatusException interrupted(InterruptedException error) {
        Thread.currentThread().interrupt();
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Verificação interrompida.", error);
    }
    private org.springframework.web.server.ResponseStatusException upstream(IOException error) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                "Falha ao ler o canal oficial: " + error.getMessage(), error);
    }
}
