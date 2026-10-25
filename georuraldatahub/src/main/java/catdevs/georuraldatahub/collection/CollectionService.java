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

    public CollectionService(
            SourceService sources,
            DatasetService datasets,
            UserService users,
            DictionaryService dictionaries,
            PublicDataConnectors connectors,
            FileService files) {

        this.sources = sources;
        this.datasets = datasets;
        this.users = users;
        this.dictionaries = dictionaries;
        this.connectors = connectors;
        this.files = files;
    }

    public record Result(
            FileResponseDTO file,
            CollectedData.Metadata collection) {
    }

    // A transferência de dados da rede acontece antes de o FileService
    // iniciar sua transação ou bloquear registros no banco.
    public Result collect(Long sourceId, CollectionRequest request)
            throws Exception {

        if (sourceId == null
                || request == null
                || request.product() == null
                || request.userId() == null) {

            throw new IllegalArgumentException(
                    "Fonte, produto e usuário são obrigatórios.");
        }

        var source = sources.findEntityById(sourceId);

        if (!matchesAgency(source.getUrl(), request.product())) {
            throw new IllegalArgumentException(
                    "A URL da fonte cadastrada não corresponde ao órgão "
                    + "do conector. Use o CRUD de fontes existente.");
        }

        if (request.datasetId() != null) {
            datasets.findEntityById(request.datasetId());
        }

        // CORREÇÃO: o método correto existente no UserService é findEntityById.
        users.findEntityById(request.userId());

        dictionaries.rules(
                dictionaries.findForValidation(
                        sourceId,
                        request.dictionaryId()));

        var log = org.slf4j.LoggerFactory.getLogger(CollectionService.class);

        log.info(
                "Iniciando coleta manual: fonte={}, produto={}, competência={}",
                sourceId,
                request.product(),
                request.competence());

        CollectedData data;

        try {
            data = connectors.collect(request);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Coleta interrompida.",
                    e);

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Falha ao ler os dados do canal oficial: "
                            + e.getMessage(),
                    e);
        }

        var file = files.uploadCollectedFile(
                data,
                request.datasetId(),
                request.userId(),
                sourceId,
                request.dictionaryId(),
                request.product().name());

        log.info(
                "Coleta concluída: fonte={}, produto={}, bytes={}, arquivo={}",
                sourceId,
                request.product(),
                data.content().length,
                file == null ? null : file.id());

        return new Result(file, data.metadata());
    }

    static boolean matchesAgency(
            String value,
            CollectionRequest.Product product) {

        try {
            URI uri = URI.create(value);
            String host = uri.getHost();

            if (host == null
                    || uri.getUserInfo() != null
                    || !("https".equals(uri.getScheme())
                    || "http".equals(uri.getScheme()))) {

                return false;
            }

            boolean ibge =
                    product == CollectionRequest.Product.IBGE_BIOMAS
                    || product == CollectionRequest.Product.IBGE_MUNICIPIOS;

            String agency = ibge ? "ibge.gov.br" : "inpe.br";

            if (host.equals(agency)
                    || host.endsWith("." + agency)) {

                return true;
            }

            return !ibge
                    && host.equals("www.gov.br")
                    && (uri.getPath().equals("/inpe")
                    || uri.getPath().startsWith("/inpe/"));

        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }
}