package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.config.ObjectStorageProperties;
import catdevs.georuraldatahub.dto.CollectionRequestDTO;
import catdevs.georuraldatahub.dto.CollectionResponseDTO;
import catdevs.georuraldatahub.entity.DataIngestion;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.Source;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.entity.enums.IngestionStatus;
import catdevs.georuraldatahub.repository.DictionaryRepository;
import catdevs.georuraldatahub.repository.FileRepository;
import catdevs.georuraldatahub.repository.IngestionRepository;
import catdevs.georuraldatahub.repository.UserRepository;

import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Service
public class CollectionService {

    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final SourceService sourceService;
    private final DictionaryRepository dictionaryRepository;
    private final UserRepository userRepository;
    private final IngestionRepository ingestionRepository;
    private final FileRepository fileRepository;
    private final ObjectStorageClient objectStorageClient;
    private final ObjectStorageProperties storageProperties;

    public CollectionService(
            SourceService sourceService,
            DictionaryRepository dictionaryRepository,
            UserRepository userRepository,
            IngestionRepository ingestionRepository,
            FileRepository fileRepository,
            ObjectStorageClient objectStorageClient,
            ObjectStorageProperties storageProperties
    ) {
        this.sourceService = sourceService;
        this.dictionaryRepository = dictionaryRepository;
        this.userRepository = userRepository;
        this.ingestionRepository = ingestionRepository;
        this.fileRepository = fileRepository;
        this.objectStorageClient = objectStorageClient;
        this.storageProperties = storageProperties;
    }

    @Transactional
    public CollectionResponseDTO collect(
            Long sourceId,
            CollectionRequestDTO request
    ) {
        if (request == null
                || request.dictionaryId() == null
                || request.userId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "dictionaryId e userId são obrigatórios"
            );
        }

        Source source = sourceService.findActive(sourceId);

        Dictionary dictionary = dictionaryRepository
                .findById(request.dictionaryId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Dicionário não encontrado"
                ));

        if (dictionary.getSource() == null
                || !dictionary.getSource().getId().equals(source.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O dicionário informado não pertence à fonte"
            );
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Usuário não encontrado"
                ));

        DataIngestion ingestion = new DataIngestion();
        ingestion.setDictionary(dictionary);
        ingestion.setUser(user);

        // O enum existente não possui EM_EXECUCAO.
        // ERRO é usado como estado inicial conservador.
        ingestion.setIngStatus(IngestionStatus.ERRO);
        ingestion = ingestionRepository.saveAndFlush(ingestion);

        try {
            URI uri = validateUrl(source.getUrl());

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = client.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            byte[] content;
            try (InputStream body = response.body()) {
                content = body.readNBytes(MAX_FILE_SIZE + 1);
            }

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw new CollectionFailure(
                        "A fonte respondeu com HTTP " + response.statusCode()
                );
            }

            if (content.length > MAX_FILE_SIZE) {
                throw new CollectionFailure(
                        "O arquivo excede o limite de 10 MB"
                );
            }

            String hash = sha256(content);

            if (fileRepository.existsByHash(hash)) {
                throw new CollectionFailure(
                        "O conteúdo já existe no cadastro de arquivos"
                );
            }

            String objectName =
                    "coletas/fonte-" + sourceId + "/" + hash + ".bin";

            objectStorageClient.putObject(
                    PutObjectRequest.builder()
                            .namespaceName(storageProperties.namespace())
                            .bucketName(storageProperties.bucketName())
                            .objectName(objectName)
                            .putObjectBody(new ByteArrayInputStream(content))
                            .contentLength((long) content.length)
                            .build()
            );

            File file = new File();
            file.setIngestion(ingestion);
            file.setName("fonte-" + sourceId + "-" + hash + ".bin");
            file.setFormatFile("bin");
            file.setSize((long) content.length);
            file.setHash(hash);
            file.setLocation(objectName);

            fileRepository.save(file);

            ingestion.setIngStatus(IngestionStatus.CONCLUIDO);
            ingestionRepository.save(ingestion);

            return new CollectionResponseDTO(
                    ingestion.getId(),
                    sourceId,
                    dictionary.getId(),
                    ingestion.getIngStatus().name(),
                    ingestion.getStartTime(),
                    file.getId(),
                    file.getName(),
                    file.getHash(),
                    file.getLocation(),
                    "Coleta concluída"
            );

        } catch (CollectionFailure exception) {
            ingestion.setIngStatus(IngestionStatus.ERRO);
            ingestionRepository.save(ingestion);

            return responseWithoutFile(
                    ingestion,
                    sourceId,
                    dictionary.getId(),
                    exception.getMessage()
            );

        } catch (Exception exception) {
            ingestion.setIngStatus(IngestionStatus.ERRO);
            ingestionRepository.save(ingestion);

            return responseWithoutFile(
                    ingestion,
                    sourceId,
                    dictionary.getId(),
                    "Não foi possível concluir a coleta"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<CollectionResponseDTO> history(Long sourceId) {
        sourceService.getById(sourceId);

        return ingestionRepository
                .findByDictionarySourceIdOrderByStartTimeDesc(sourceId)
                .stream()
                .map(ingestion -> responseWithoutFile(
                        ingestion,
                        sourceId,
                        ingestion.getDictionary().getId(),
                        "Consulte os metadados do arquivo separadamente"
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public CollectionResponseDTO status(
            Long sourceId,
            Long ingestionId
    ) {
        sourceService.getById(sourceId);

        DataIngestion ingestion = ingestionRepository
                .findByIdAndDictionarySourceId(ingestionId, sourceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Execução não encontrada para esta fonte"
                ));

        return responseWithoutFile(
                ingestion,
                sourceId,
                ingestion.getDictionary().getId(),
                "Status da execução"
        );
    }

    private CollectionResponseDTO responseWithoutFile(
            DataIngestion ingestion,
            Long sourceId,
            Long dictionaryId,
            String message
    ) {
        return new CollectionResponseDTO(
                ingestion.getId(),
                sourceId,
                dictionaryId,
                ingestion.getIngStatus().name(),
                ingestion.getStartTime(),
                null,
                null,
                null,
                null,
                message
        );
    }

    private URI validateUrl(String value) throws Exception {
        URI uri = URI.create(value).normalize();

        if (!"https".equalsIgnoreCase(uri.getScheme())
                && !"http".equalsIgnoreCase(uri.getScheme())) {
            throw new CollectionFailure(
                    "A URL deve utilizar HTTP ou HTTPS"
            );
        }

        if (uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getHost().equalsIgnoreCase("localhost")) {
            throw new CollectionFailure("URL da fonte inválida");
        }

        InetAddress[] addresses =
                InetAddress.getAllByName(uri.getHost());

        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new CollectionFailure(
                        "A URL aponta para um endereço não permitido"
                );
            }
        }

        return uri;
    }

    private String sha256(byte[] content) throws Exception {
        byte[] digest = MessageDigest
                .getInstance("SHA-256")
                .digest(content);

        return HexFormat.of().formatHex(digest);
    }

    private static class CollectionFailure extends Exception {
        CollectionFailure(String message) {
            super(message);
        }
    }
}