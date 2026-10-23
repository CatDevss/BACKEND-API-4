package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.config.ObjectStorageProperties;
import catdevs.georuraldatahub.dto.CollectionResponseDTO;
import catdevs.georuraldatahub.entity.CollectionExecution;
import catdevs.georuraldatahub.entity.Source;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.repository.CollectionExecutionRepository;
import catdevs.georuraldatahub.repository.UserRepository;

import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class CollectionService {

    private final SourceService sources;
    private final CollectionExecutionRepository executions;
    private final UserRepository users;
    private final ObjectStorageClient oci;
    private final ObjectStorageProperties storage;

    private final ConcurrentMap<Long, Boolean> running =
            new ConcurrentHashMap<>();

    @Value("${coleta.timeout-seconds:20}")
    private int timeoutSeconds;

    @Value("${coleta.max-bytes:52428800}")
    private long maxBytes;

    public CollectionService(
            SourceService sources,
            CollectionExecutionRepository executions,
            UserRepository users,
            ObjectStorageClient oci,
            ObjectStorageProperties storage
    ) {
        this.sources = sources;
        this.executions = executions;
        this.users = users;
        this.oci = oci;
        this.storage = storage;
    }

    @Transactional
    public CollectionResponseDTO collect(Long sourceId, Long userId) {

        Source source = sources.findActive(sourceId);

        User requester = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Usuário solicitante não encontrado"
                ));

        if (running.putIfAbsent(sourceId, true) != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Já existe uma coleta em execução para esta fonte"
            );
        }

        CollectionExecution execution = new CollectionExecution();
        execution.setSource(source);
        execution.setRequester(requester);

        execution = executions.saveAndFlush(execution);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(
                            Math.max(1, timeoutSeconds)
                    ))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(source.getUrl()))
                    .timeout(Duration.ofSeconds(
                            Math.max(1, timeoutSeconds)
                    ))
                    .GET()
                    .header(
                            "User-Agent",
                            "GeoRuralDataHub-Collector/1.0"
                    )
                    .build();

            HttpResponse<byte[]> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            execution.setHttpStatus(response.statusCode());

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Falha HTTP: " + response.statusCode()
                );
            }

            byte[] body = response.body();

            if (body.length > maxBytes) {
                throw new IllegalStateException(
                        "Arquivo excede o limite configurado"
                );
            }

            String hash = sha256(body);
            execution.setHash(hash);

            if (executions.existsBySourceIdAndHashAndStatus(
                    sourceId,
                    hash,
                    "CONCLUIDA"
            )) {
                throw new DuplicateCollectionException(
                        "Conteúdo duplicado para esta fonte"
                );
            }

            String objectName = "coletas/fonte-"
                    + sourceId + "/" + hash + ".bin";

            oci.putObject(
                    PutObjectRequest.builder()
                            .namespaceName(storage.namespace())
                            .bucketName(storage.bucketName())
                            .objectName(objectName)
                            .putObjectBody(
                                    new ByteArrayInputStream(body)
                            )
                            .contentLength((long) body.length)
                            .build()
            );

            execution.setObjectName(objectName);
            execution.setStatus("CONCLUIDA");

        } catch (Exception ex) {

            execution.setStatus("ERRO");

            String message;

            if (ex instanceof HttpTimeoutException) {
                message = "Timeout na coleta";
            } else if (ex instanceof DuplicateCollectionException) {
                message = ex.getMessage();
            } else {
                message = ex.getMessage();
            }

            if (message == null || message.isBlank()) {
                message = "Falha na coleta";
            }

            execution.setError(
                    message.substring(0, Math.min(990, message.length()))
            );

        } finally {
            execution.setFinishedAt(LocalDateTime.now());
            running.remove(sourceId);
        }

        return dto(executions.saveAndFlush(execution));
    }

    @Transactional(readOnly = true)
    public List<CollectionResponseDTO> history(Long sourceId) {

        sources.getById(sourceId);

        return executions
                .findBySourceIdOrderByStartedAtDesc(sourceId)
                .stream()
                .map(CollectionService::dto)
                .toList();
    }

    @Transactional(readOnly = true)
    public CollectionResponseDTO status(Long sourceId, Long id) {

        CollectionExecution execution = executions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Coleta não encontrada"
                ));

        if (!execution.getSource().getId().equals(sourceId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Coleta não encontrada para esta fonte"
            );
        }

        return dto(execution);
    }

    private static String sha256(byte[] bytes) throws Exception {

        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)
        );
    }

    private static CollectionResponseDTO dto(
            CollectionExecution execution
    ) {
        return new CollectionResponseDTO(
                execution.getId(),
                execution.getSource().getId(),
                execution.getStatus(),
                execution.getHash(),
                execution.getObjectName(),
                execution.getHttpStatus(),
                execution.getError(),
                execution.getRequester().getId(),
                execution.getStartedAt(),
                execution.getFinishedAt()
        );
    }

    private static class DuplicateCollectionException
            extends RuntimeException {

        DuplicateCollectionException(String message) {
            super(message);
        }
    }
}
