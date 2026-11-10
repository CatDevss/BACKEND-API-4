package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.collection.*;
import catdevs.georuraldatahub.dto.*;
import catdevs.georuraldatahub.entity.enums.UserType;
import catdevs.georuraldatahub.processing.*;
import catdevs.georuraldatahub.validation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DictionaryGenerationService {
    private final SourceService sources;
    private final UserService users;
    private final DictionaryService dictionaries;
    private final PublicDataConnectors connectors;
    private final GeospatialReader geospatial;
    private final ProcessingProperties limits;
    private final DictionaryInference inference;
    private final FileValidationService validation;
    private final FileService files;

    public DictionaryGenerationService(SourceService sources, UserService users, DictionaryService dictionaries,
            PublicDataConnectors connectors, GeospatialReader geospatial, ProcessingProperties limits,
            DictionaryInference inference, FileValidationService validation, FileService files) {
        this.sources = sources; this.users = users; this.dictionaries = dictionaries;
        this.connectors = connectors; this.geospatial = geospatial; this.limits = limits;
        this.inference = inference; this.validation = validation; this.files = files;
    }
    public record Issue(int recordIndex, List<String> errors) { }
    public record Check(String status, long validRecords, long rejectedRecords, List<Issue> issues) { }
    public record Result(DictionaryResponseDTO dictionary, String filename, String sha256,
            Check validation, List<String> warnings) { }

    public Result fromUpload(Long sourceId, Long userId, MultipartFile file) throws Exception {
        requireGenerator(sourceId, userId);
        return generate(sourceId, file, null);
    }

    // Download e inferência acontecem antes da transação curta de criação do dicionário.
    public Result fromCollection(Long sourceId, CollectionRequest request) throws Exception {
        var source = requireGenerator(sourceId, request.userId());
        if (!CollectionService.matchesAgency(source.getUrl(), request.product()))
            throw new IllegalArgumentException("A URL da fonte não corresponde ao órgão do conector.");
        CollectedData data;
        try { data = connectors.collect(request); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Geração interrompida.", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao ler o canal oficial: " + e.getMessage(), e);
        }
        return generate(sourceId, new DownloadedMultipartFile(data.filename(),
                "csv".equals(data.format()) ? "text/csv" : "application/geo+json", data.content()), data);
    }

    private catdevs.georuraldatahub.entity.Source requireGenerator(Long sourceId, Long userId) {
        if (sourceId == null || sourceId <= 0 || userId == null || userId <= 0)
            throw new IllegalArgumentException("Informe fonte e usuário válidos.");
        var user = users.findEntityById(userId);
        if (!Integer.valueOf(1).equals(user.getUserStatus())
                || user.getUserType() != UserType.GESTOR && user.getUserType() != UserType.OPERADOR)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente Gestores e Operadores ativos podem gerar dicionários.");
        var source = sources.findEntityById(sourceId);
        if (!Integer.valueOf(1).equals(source.getStatus())) throw new IllegalArgumentException("Ative a fonte antes de gerar o dicionário.");
        return source;
    }

    private Result generate(Long sourceId, MultipartFile file, CollectedData collected) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Selecione um arquivo com conteúdo.");
        if (file.getSize() > limits.getMaxInputBytes()) throw new IllegalArgumentException("Arquivo excede o limite de leitura configurado.");
        String filename = Objects.toString(file.getOriginalFilename(), "arquivo").replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        String format = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!Set.of("csv", "json", "geojson", "zip", "gpkg", "tif", "tiff").contains(format))
            throw new IllegalArgumentException("Formato não suportado para geração de dicionário.");
        if (Set.of("csv", "json", "geojson").contains(format) && file.getSize() > limits.getMaxOutputBytes())
            throw new IllegalArgumentException("Conteúdo excede o limite de leitura para geração de dicionário.");
        var preparation = IngestionContent.prepare(file, format, collected, geospatial);
        if (preparation.errors() != null)
            throw new IllegalArgumentException("Não foi possível converter o arquivo: " + preparation.errors().getFirst().errors());
        byte[] content;
        if (preparation.content() != null) {
            content = preparation.content().content();
            format = Set.of("tif", "tiff").contains(format) ? "json" : "geojson";
        } else try (var input = file.getInputStream()) {
            content = input.readNBytes(Math.min(limits.getMaxOutputBytes(), Integer.MAX_VALUE - 1) + 1);
        }
        if (content.length > limits.getMaxOutputBytes())
            throw new IllegalArgumentException("Conteúdo excede o limite de leitura para geração de dicionário.");
        List<FieldRule> rules = inference.infer(new ByteArrayInputStream(content), format, limits.getMaxFeatures());
        var records = validation.validate(new ByteArrayInputStream(content), format, rules);
        var report = FileValidationReport.of(null, null, records);
        if (records.stream().anyMatch(r -> r.recordIndex() < 0))
            throw new IllegalArgumentException("Arquivo com erro estrutural; nenhum dicionário foi salvo.");
        String hash = files.calculateHash(file);
        String description = "Inferido de " + fit(filename, 200) + ". SHA-256: " + hash
                + ". Campos/tipos observados; sem inferência de domínios ou unicidade.";
        var saved = dictionaries.create(sourceId, new DictionaryRequestDTO(fit("Inferido · " + filename, 100), description, rules));
        var issues = records.stream().filter(r -> !r.valid()).limit(20)
                .map(r -> new Issue(r.recordIndex(), r.errors())).toList();
        return new Result(saved, filename, hash, new Check(report.status(), report.validRecords(), report.rejectedRecords(), issues),
                List.of("Campos e tipos foram detectados em todos os registros. Revise as regras de negócio, os domínios e a unicidade.",
                        "O dicionário foi salvo como uma nova versão. O arquivo será validado novamente ao realizar a ingestão."));
    }

    private static String fit(String value, int maxBytes) {
        var result = new StringBuilder();
        int bytes = 0;
        for (int cp : value.codePoints().toArray()) {
            String character = new String(Character.toChars(cp));
            bytes += character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > maxBytes) break;
            result.append(character);
        }
        return result.toString();
    }
}
