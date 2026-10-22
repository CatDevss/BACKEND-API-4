package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.dto.FileResponseDTO;
import catdevs.georuraldatahub.entity.DataIngestion;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.entity.Version;
import catdevs.georuraldatahub.entity.enums.IngestionStatus;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.exception.DuplicateFileException;
import catdevs.georuraldatahub.exception.UserNotFoundException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.List;

@Service
public class FileService {

    @PersistenceContext
    private EntityManager entityManager;

    private final BucketService bucketService;
    private final DatasetService datasetService;

    public FileService(
            BucketService bucketService,
            DatasetService datasetService) {
        this.bucketService = bucketService;
        this.datasetService = datasetService;
    }

    @Transactional
    public FileResponseDTO uploadFile(
            MultipartFile multipartFile,
            Long datasetId,
            Long userId,
            Long dictionaryId) throws IOException, NoSuchAlgorithmException {

        String hash = calculateHash(multipartFile);

        Dataset dataset = entityManager.find(Dataset.class, datasetId);

        if (dataset == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Conjunto não encontrado."
            );
        }

        User user = entityManager.find(User.class, userId);

        if (user == null) {
            throw new UserNotFoundException("Usuário não encontrado.");
        }

        Dictionary dictionary = entityManager.find(Dictionary.class, dictionaryId);

        if (dictionary == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Dicionário não encontrado."
            );
        }

        if (arquivoJaExisteNoDataset(datasetId, hash)) {
            throw new DuplicateFileException(
                    "Este arquivo já foi enviado anteriormente para este conjunto."
            );
        }

        String sanitizedFilename =
                sanitizeFilename(multipartFile.getOriginalFilename());

        String location = bucketService.uploadToRawZone(
                multipartFile,
                datasetId,
                hash,
                sanitizedFilename
        );

        try {
            DataIngestion ingestion = new DataIngestion();
            ingestion.setDictionary(dictionary);
            ingestion.setUser(user);
            ingestion.setIngStatus(IngestionStatus.CONCLUIDO);

            entityManager.persist(ingestion);
            entityManager.flush();

            File file = new File();
            file.setIngestion(ingestion);
            file.setName(sanitizedFilename);
            file.setFormatFile(extractFormat(sanitizedFilename));
            file.setSize(multipartFile.getSize());
            file.setHash(hash);
            file.setLocation(location);

            entityManager.persist(file);
            entityManager.flush();

            Version version = new Version();
            version.setFile(file);
            version.setVersionNumber(1);
            version.setStatus(VersionStatus.BRUTA);
            version.setLocation(location);

            entityManager.persist(version);

            if (dataset.getFiles() != null) {
                dataset.getFiles().add(file);
            }

            entityManager.flush();

            return toDto(file);

        } catch (RuntimeException e) {
            try {
                bucketService.deleteObject(location);
            } catch (RuntimeException cleanupError) {
                e.addSuppressed(cleanupError);
            }

            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<FileResponseDTO> findByDatasetId(Long datasetId) {

        Dataset dataset = datasetService.findEntityById(datasetId);

        return buscarArquivosPorDataset(dataset.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    private List<File> buscarArquivosPorDataset(Long datasetId) {

        return entityManager.createQuery(
                """
                SELECT f
                FROM Dataset d JOIN d.files f
                WHERE d.id = :datasetId
                ORDER BY f.id DESC
                """,
                File.class
        )
        .setParameter("datasetId", datasetId)
        .getResultList();
    }

    private boolean arquivoJaExisteNoDataset(Long datasetId, String hash) {

        Long quantidade = entityManager.createQuery(
                """
                SELECT COUNT(f)
                FROM Dataset d JOIN d.files f
                WHERE d.id = :datasetId
                  AND f.hash = :hash
                """,
                Long.class
        )
        .setParameter("datasetId", datasetId)
        .setParameter("hash", hash)
        .getSingleResult();

        return quantidade > 0;
    }

    public String calculateHash(MultipartFile file)
            throws IOException, NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream inputStream = file.getInputStream();
             DigestInputStream digestInputStream =
                     new DigestInputStream(inputStream, digest)) {

            byte[] buffer = new byte[8192];

            while (digestInputStream.read(buffer) != -1) {
                // A leitura atualiza o digest.
            }
        }

        StringBuilder result = new StringBuilder();

        for (byte b : digest.digest()) {
            result.append(String.format("%02x", b));
        }

        return result.toString();
    }

    private String extractFormat(String filename) {

        if (filename == null || !filename.contains(".")) {
            return null;
        }

        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private String sanitizeFilename(String filename) {

        if (filename == null || filename.isBlank()) {
            return "arquivo_sem_nome";
        }

        String withoutAccents = Normalizer
                .normalize(filename, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return withoutAccents.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private FileResponseDTO toDto(File file) {

        DataIngestion ingestion = file.getIngestion();

        Long ingestionId = ingestion != null
                ? ingestion.getId()
                : null;

        Long userId = ingestion != null && ingestion.getUser() != null
                ? ingestion.getUser().getId()
                : null;

        return new FileResponseDTO(
                file.getId(),
                file.getName(),
                file.getFormatFile(),
                file.getSize(),
                file.getHash(),
                file.getLocation(),
                ingestionId,
                file.getReceivedDate(),
                userId
        );
    }
}