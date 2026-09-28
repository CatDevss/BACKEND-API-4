package catdevs.georuraldatahub.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import catdevs.georuraldatahub.dto.FileResponseDTO;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.entity.Version;
import catdevs.georuraldatahub.exception.DataSetNotFoundException;
import catdevs.georuraldatahub.exception.DuplicateFileException;
import catdevs.georuraldatahub.exception.UserNotFoundException;
import catdevs.georuraldatahub.repository.DatasetRepository;
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

    public FileService(FileRepository fileRepository, VersionRepository versionRepository,
            DatasetRepository datasetRepository, UserRepository userRepository, BucketService bucketService) {
        this.fileRepository = fileRepository;
        this.versionRepository = versionRepository;
        this.datasetRepository = datasetRepository;
        this.userRepository = userRepository;
        this.bucketService = bucketService;
    }

    @Transactional
    public FileResponseDTO uploadFile(MultipartFile multipartFile, Long datasetId, Long userId) throws Exception {

        String hash = calculateHash(multipartFile);

        if (fileRepository.existsByHashInDataset(datasetId, hash)) {
            throw new DuplicateFileException("Este arquivo já foi enviado anteriormente para este conjunto.");
        }

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new DataSetNotFoundException("Conjunto não encontrado."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuário não encontrado."));

        String sanitizedFilename = sanitizeFilename(multipartFile.getOriginalFilename());

        // 1) sobe pro bucket primeiro — se falhar aqui, nenhuma versão é criada
        String location = bucketService.uploadToRawZone(multipartFile, datasetId, hash, sanitizedFilename);

        // 2) só grava versão + arquivo depois que o upload deu certo
        try {
            Version version = versionRepository.save(new Version(dataset, LocalDateTime.now(), user));

            File file = fileRepository.save(new File(
                    version,
                    sanitizedFilename,
                    extractFormat(sanitizedFilename),
                    multipartFile.getSize(),
                    hash,
                    location));

            return new FileResponseDTO(
                    file.getId(),
                    file.getName(),
                    file.getFormatFile(),
                    file.getSize(),
                    file.getHash(),
                    file.getLocation(),
                    version.getId(),
                    version.getDateCreation(),
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