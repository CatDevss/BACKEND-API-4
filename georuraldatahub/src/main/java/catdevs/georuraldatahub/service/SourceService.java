package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.dto.SourceCreateRequestDTO;
import catdevs.georuraldatahub.dto.SourceResponseDTO;
import catdevs.georuraldatahub.entity.Source;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.entity.DataIngestion;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.enums.IngestionStatus;
import catdevs.georuraldatahub.repository.SourceRepository;
import catdevs.georuraldatahub.repository.UserRepository;
import catdevs.georuraldatahub.repository.DictionaryRepository;
import catdevs.georuraldatahub.repository.IngestionRepository;
import catdevs.georuraldatahub.repository.FileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.sql.SQLException;

@Service
public class SourceService {

    private final SourceRepository sourceRepository;
    private final UserRepository userRepository;
    private final DictionaryRepository dictionaryRepository;
    private final IngestionRepository ingestionRepository;
    private final FileRepository fileRepository;
    private final BucketService bucketService;

    public SourceService(SourceRepository sourceRepository,
                         UserRepository userRepository,
                         DictionaryRepository dictionaryRepository,
                         IngestionRepository ingestionRepository,
                         FileRepository fileRepository,
                         BucketService bucketService) {
        this.sourceRepository = sourceRepository;
        this.userRepository = userRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.ingestionRepository = ingestionRepository;
        this.fileRepository = fileRepository;
        this.bucketService = bucketService;
    }

    @Transactional
    public SourceResponseDTO create(SourceCreateRequestDTO request) {
        validateRequest(request);

        String name = request.name().trim();
        String url = request.url().trim();

        if (sourceRepository.existsByName(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Já existe uma fonte com esse nome"
            );
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Usuário não encontrado"
                ));

        Source source = new Source();
        source.setName(name);
        source.setUrl(url);
        source.setDescription(request.description());
        source.setUser(user);
        source.setActive(true);

        try {
            return toDTO(sourceRepository.saveAndFlush(source));
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueConstraintViolation(exception)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Já existe uma fonte com esse nome",
                        exception
                );
            }

            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<SourceResponseDTO> listAll() {
        return sourceRepository.findAll()
                .stream()
                .map(SourceService::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public SourceResponseDTO getById(Long id) {
        return toDTO(findSource(id));
    }

    @Transactional
    public SourceResponseDTO update(
            Long id,
            SourceCreateRequestDTO request) {

        validateRequest(request);

        Source source = findSource(id);

        String name = request.name().trim();
        String url = request.url().trim();

        if (sourceRepository.existsByNameAndIdNot(name, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Já existe uma fonte com esse nome"
            );
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Usuário não encontrado"
                ));

        source.setName(name);
        source.setUrl(url);
        source.setDescription(request.description());
        source.setUser(user);

        try {
            return toDTO(sourceRepository.saveAndFlush(source));
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueConstraintViolation(exception)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Já existe uma fonte com esse nome",
                        exception
                );
            }

            throw exception;
        }
    }

    @Transactional
    public SourceResponseDTO deactivate(Long id) {
        Source source = findSource(id);

        source.setActive(false);

        return toDTO(sourceRepository.save(source));
    }

    @Transactional(readOnly = true)
    public Source findActive(Long id) {
        Source source = findSource(id);

        if (!source.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A fonte está inativa"
            );
        }

        return source;
    }


    /** Executa uma coleta manual e registra a execução e o arquivo bruto. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String, Object> collect(Long sourceId, Long dictionaryId, Long userId) {
        Source source = findActive(sourceId);
        if (dictionaryId == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "dictionaryId e userId são obrigatórios");
        }

        Dictionary dictionary = dictionaryRepository.findByIdAndSourceId(dictionaryId, sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "O dicionário não pertence à fonte informada"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Usuário não encontrado"));

        DataIngestion ingestion = new DataIngestion();
        ingestion.setDictionary(dictionary);
        ingestion.setUser(user);
        // A enumeração existente não possui estado EM_EXECUCAO.
        ingestion.setIngStatus(IngestionStatus.ERRO);
        ingestion = ingestionRepository.saveAndFlush(ingestion);

        String location = null;
        try {
            URI uri = validateSourceUri(source.getUrl());
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "GeoRuralDataHub/1.0")
                    .GET().build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "A fonte respondeu com HTTP " + response.statusCode());
            }
            byte[] content = response.body();
            if (content.length == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "A fonte retornou um arquivo vazio");
            }
            if (content.length > 25 * 1024 * 1024) {
                throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
                        "O arquivo coletado excede o limite de 25 MB");
            }

            String hash = sha256(content);
            if (fileRepository.existsByHashAndIngestionDictionarySourceId(hash, sourceId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Esta fonte já gerou um arquivo com o mesmo hash");
            }

            String filename = filenameFromUri(uri);
            location = bucketService.uploadBytesToRawZone(content, sourceId, hash, filename);
            File file = new File();
            file.setIngestion(ingestion);
            file.setName(filename);
            file.setFormatFile(extractFormat(filename));
            file.setSize((long) content.length);
            file.setHash(hash);
            file.setLocation(location);
            file = fileRepository.save(file);

            ingestion.setIngStatus(IngestionStatus.CONCLUIDO);
            ingestionRepository.save(ingestion);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ingestionId", ingestion.getId());
            result.put("sourceId", source.getId());
            result.put("dictionaryId", dictionary.getId());
            result.put("userId", user.getId());
            result.put("status", ingestion.getIngStatus());
            result.put("startTime", ingestion.getStartTime());
            result.put("fileId", file.getId());
            result.put("fileName", file.getName());
            result.put("hash", file.getHash());
            result.put("size", file.getSize());
            result.put("location", file.getLocation());
            return result;
        } catch (ResponseStatusException e) {
            cleanupObject(location);
            ingestion.setIngStatus(IngestionStatus.ERRO);
            ingestionRepository.save(ingestion);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanupObject(location);
            ingestion.setIngStatus(IngestionStatus.ERRO);
            ingestionRepository.save(ingestion);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Coleta interrompida", e);
        } catch (Exception e) {
            cleanupObject(location);
            ingestion.setIngStatus(IngestionStatus.ERRO);
            ingestionRepository.save(ingestion);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível coletar a fonte", e);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> collectionHistory(Long sourceId) {
        findSource(sourceId);
        return ingestionRepository.findByDictionarySourceIdOrderByStartTimeDesc(sourceId)
                .stream().map(ingestion -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("ingestionId", ingestion.getId());
                    item.put("sourceId", sourceId);
                    item.put("dictionaryId", ingestion.getDictionary().getId());
                    item.put("dictionaryName", ingestion.getDictionary().getName());
                    item.put("userId", ingestion.getUser().getId());
                    item.put("status", ingestion.getIngStatus());
                    item.put("startTime", ingestion.getStartTime());
                    return item;
                }).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> collectionStatus(Long sourceId, Long ingestionId) {
        DataIngestion ingestion = ingestionRepository.findByIdAndDictionarySourceId(ingestionId, sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Coleta não encontrada para esta fonte"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ingestionId", ingestion.getId());
        item.put("sourceId", sourceId);
        item.put("dictionaryId", ingestion.getDictionary().getId());
        item.put("userId", ingestion.getUser().getId());
        item.put("status", ingestion.getIngStatus());
        item.put("startTime", ingestion.getStartTime());
        return item;
    }

    private void cleanupObject(String location) {
        if (location != null) {
            try { bucketService.deleteObject(location); } catch (RuntimeException ignored) { }
        }
    }

    private URI validateSourceUri(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || host.equalsIgnoreCase("localhost") || host.endsWith(".localhost")
                    || host.endsWith(".local") || host.endsWith(".internal")) {
                throw new IllegalArgumentException();
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new IllegalArgumentException();
                }
            }
            return uri;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "URL da fonte inválida ou não permitida");
        }
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private String filenameFromUri(URI uri) {
        String path = uri.getPath();
        String filename = (path == null || path.isBlank() || path.endsWith("/"))
                ? "coleta-" + LocalDateTime.now().toString().replace(':', '-') + ".bin"
                : path.substring(path.lastIndexOf('/') + 1);
        filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return filename.isBlank() ? "coleta.bin" : filename;
    }

    private String extractFormat(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1
                ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "bin";
    }

    private Source findSource(Long id) {
        if (id == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O ID da fonte é obrigatório"
            );
        }

        return sourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Fonte não encontrada"
                ));
    }

    private void validateRequest(SourceCreateRequestDTO request) {
        if (request == null
                || request.name() == null
                || request.name().isBlank()
                || request.url() == null
                || request.url().isBlank()
                || request.userId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nome, URL e usuário são obrigatórios"
            );
        }
    }

    private static SourceResponseDTO toDTO(Source source) {
        return new SourceResponseDTO(
                source.getId(),
                source.getName(),
                source.getDateCreation(),
                source.getUrl(),
                source.getUser().getId(),
                source.getDescription(),
                source.isActive()
        );
    }

    private boolean isUniqueConstraintViolation(
            DataIntegrityViolationException exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && sqlException.getErrorCode() == 1) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}