package catdevs.georuraldatahub.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.dto.FileResponseDTO;
import catdevs.georuraldatahub.entity.DataIngestion;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.entity.Version;
import catdevs.georuraldatahub.entity.enums.IngestionStatus;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.exception.DataSetNotFoundException;
import catdevs.georuraldatahub.exception.DuplicateFileException;
import catdevs.georuraldatahub.exception.UserNotFoundException;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.DictionaryRepository;
import catdevs.georuraldatahub.repository.IngestionRepository;
import catdevs.georuraldatahub.repository.FileRepository;
import catdevs.georuraldatahub.repository.UserRepository;
import catdevs.georuraldatahub.repository.VersionRepository;

@Service
public class FileService {

    private FileRepository fileRepository;
    private VersionRepository versionRepository;
    private DatasetRepository datasetRepository;
    private UserRepository userRepository;
    private BucketService bucketService;
    private IngestionRepository ingestionRepository;
    private DictionaryRepository dictionaryRepository;

    public FileService(FileRepository fileRepository, VersionRepository versionRepository,
            DatasetRepository datasetRepository, UserRepository userRepository, BucketService bucketService,
            IngestionRepository ingestionRepository, DictionaryRepository dictionaryRepository) {
        this.ingestionRepository = ingestionRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.fileRepository = fileRepository;
        this.versionRepository = versionRepository;
        this.datasetRepository = datasetRepository;
        this.userRepository = userRepository;
        this.bucketService = bucketService;
    }

    @Transactional
    public FileResponseDTO uploadFile(MultipartFile multipartFile, Long datasetId, Long userId, Long sourceId)
            throws Exception {

        String hash = calculateHash(multipartFile);

        if (fileRepository.existsByHashInDataset(datasetId, hash)) {
            throw new DuplicateFileException("Este arquivo já foi enviado anteriormente para este conjunto.");
        }

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new DataSetNotFoundException("Conjunto não encontrado."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuário não encontrado."));
        Dictionary dictionary = dictionaryRepository.findFirstBySource_IdOrderByVersionDesc(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Esta fonte não possui dicionário de dados cadastrado."));

        String sanitizedFilename = sanitizeFilename(multipartFile.getOriginalFilename());

        // 1) sobe pro bucket primeiro — se falhar aqui, nada é gravado no banco
        String location = bucketService.uploadToRawZone(multipartFile, datasetId, hash, sanitizedFilename);

        // 2) grava ingestão -> arquivo -> versão bruta e vincula o arquivo ao conjunto
        try {
            DataIngestion ingestion = new DataIngestion();
            ingestion.setDictionary(dictionary);
            ingestion.setUser(user);
            ingestion.setIngStatus(IngestionStatus.CONCLUIDO);
            ingestion = ingestionRepository.save(ingestion);

            File file = new File();
            file.setIngestion(ingestion);
            file.setName(sanitizedFilename);
            file.setFormatFile(extractFormat(sanitizedFilename));
            file.setSize(multipartFile.getSize());
            file.setHash(hash);
            file.setLocation(location);
            file = fileRepository.save(file);

            Version version = new Version();
            version.setFile(file);
            version.setVersionNumber(1);
            version.setStatus(VersionStatus.BRUTA);
            version.setLocation(location);
            versionRepository.save(version);

            dataset.getFiles().add(file);

            return new FileResponseDTO(
                    file.getId(),
                    file.getName(),
                    file.getFormatFile(),
                    file.getSize(),
                    file.getHash(),
                    file.getLocation(),
                    ingestion.getId(),
                    file.getReceivedDate(),
                    user.getId());
        } catch (RuntimeException e) {
            // desfaz o upload pra não deixar arquivo órfão no bucket
            bucketService.deleteObject(location);
            throw e;
        }
    }

    String calculateHash(MultipartFile file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream is = file.getInputStream();
                DigestInputStream dis = new DigestInputStream(is, digest)) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {

            }

        }

        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String extractFormat(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /**
     * Remove acentos e troca espaços/caracteres especiais por "_",
     * pra gerar um nome seguro pra usar como chave de objeto no bucket.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "arquivo_sem_nome";
        }
        String semAcento = Normalizer.normalize(filename, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}