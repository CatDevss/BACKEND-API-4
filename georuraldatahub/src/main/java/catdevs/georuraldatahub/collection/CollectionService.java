package catdevs.georuraldatahub.collection;

import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import catdevs.georuraldatahub.dto.FileResponseDTO;
import catdevs.georuraldatahub.service.*;

@Service
public class CollectionService {
    private final SourceService sources;
    private final DatasetService datasets;
    private final UserService users;
    private final DictionaryService dictionaries;
    private final PublicDataConnectors connectors;
    private final FileService files;
    public CollectionService(SourceService sources, DatasetService datasets, UserService users,
            DictionaryService dictionaries, PublicDataConnectors connectors, FileService files) {
        this.sources = sources; this.datasets = datasets; this.users = users;
        this.dictionaries = dictionaries; this.connectors = connectors; this.files = files;
    }
    public record Result(FileResponseDTO file, CollectedData.Metadata collection) {}

    // Network transfers happen before FileService starts its database transaction/locks.
    public Result collect(Long sourceId, CollectionRequest request) throws Exception {
        if (sourceId == null || request == null || request.product() == null
                || request.userId() == null) throw new IllegalArgumentException("Fonte, produto e usuário são obrigatórios.");
        var source = sources.findEntityById(sourceId);
        if (!Integer.valueOf(1).equals(source.getStatus())) throw new IllegalArgumentException("Fonte inativa; ative-a antes de coletar.");
        if (!matchesAgency(source.getUrl(), request.product())) throw new IllegalArgumentException(
                "A URL da fonte cadastrada não corresponde ao órgão do conector. Use o CRUD de fontes existente.");
        if (request.datasetId() != null) datasets.findEntityById(request.datasetId());
        users.findEntityById(request.userId());
        dictionaries.rules(dictionaries.findForValidation(sourceId, request.dictionaryId()));
        var log = org.slf4j.LoggerFactory.getLogger(CollectionService.class);
        log.info("Iniciando coleta: fonte={}, produto={}, competência={}", sourceId, request.product(), request.competence());
        CollectedData data;
        try { data = connectors.collect(request); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Coleta interrompida.", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao ler os dados do canal oficial: " + e.getMessage(), e);
        }
        var file = files.uploadCollectedFile(data, request.datasetId(), request.userId(), sourceId, request.dictionaryId(), request.product().name());
        log.info("Coleta concluída: fonte={}, produto={}, bytes={}, arquivo={}", sourceId, request.product(), data.content().length,
                file == null ? null : file.id());
        return new Result(file, data.metadata());
    }
    public static boolean matchesAgency(String value, CollectionRequest.Product product) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(java.util.Locale.ROOT);
            if (host == null || uri.getUserInfo() != null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) return false;
            String agency = switch (product) {
                case IBGE_BIOMAS, IBGE_MUNICIPIOS -> "ibge.gov.br";
                case INPE_PRODES, INPE_QUEIMADAS -> "inpe.br";
                case ICMBIO, ICMBIO_EMBARGOS -> "icmbio.gov.br";
                case MMA -> "mma.gov.br";
                case FUNAI -> "funai.gov.br";
                case INCRA -> "incra.gov.br";
                case SFB -> "florestal.gov.br";
                case IBAMA -> "ibama.gov.br";
            };
            if (host.equals(agency) || host.endsWith("." + agency)) return true;
            if ((product == CollectionRequest.Product.ICMBIO || product == CollectionRequest.Product.ICMBIO_EMBARGOS)
                    && host.equals("geoservicos.inde.gov.br") && uri.getPath().startsWith("/geoserver/ICMBio/")) return true;
            String slug = agency.substring(0, agency.indexOf('.'));
            return host.equals("www.gov.br") && (uri.getPath().equals("/" + slug) || uri.getPath().startsWith("/" + slug + "/"));
        } catch (IllegalArgumentException | NullPointerException e) { return false; }
    }
}
