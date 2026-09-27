package catdevs.georuraldatahub.service;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;

@Service
public class FileService {

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private VersionRepository versionRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BucketService bucketService;

    public FileResponseDTO uploadFile(
            MultipartFile multipartFile,
            Long datasetId,
            Long userId
    ) throws Exception {

        // Validar existência do conjunto antes de processar o arquivo
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() ->
                        new DataSetNotFoundException("Conjunto não encontrado."));

        // Validar existência do usuário
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException("Usuário não encontrado."));

        // Ler o conteúdo do arquivo
        byte[] content = multipartFile.getBytes();

        // Calcular hash SHA-256
        String hash = calculateHash(content);

        // Verificar se o arquivo já existe nesse conjunto
        if (fileRepository.existsByHashInDataset(datasetId, hash)) {
            throw new DuplicateFileException(
                    "Este arquivo já foi enviado anteriormente para este conjunto."
            );
        }

        // Criar nova versão
        Version version = versionRepository.save(
                new Version(
                        dataset,
                        LocalDateTime.now(),
                        user
                )
        );

        // Sanitizar nome do arquivo
        String sanitizedFilename =
                sanitizeFilename(multipartFile.getOriginalFilename());

        // Enviar arquivo para o Object Storage
        String location = bucketService.uploadToRawZone(
                multipartFile,
                datasetId,
                hash,
                sanitizedFilename
        );

        // Criar registro do arquivo
        File file = new File(
                version,
                sanitizedFilename,
                extractFormat(sanitizedFilename),
                multipartFile.getSize(),
                hash,
                location
        );

        File savedFile = fileRepository.save(file);

        // Retornar resposta
        return new FileResponseDTO(
                savedFile.getId(),
                savedFile.getName(),
                savedFile.getFormatFile(),
                savedFile.getSize(),
                savedFile.getHash(),
                savedFile.getLocation(),
                version.getId(),
                version.getDateCreation(),
                version.getUser().getId()
        );
    }

    private String calculateHash(byte[] content)
            throws NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] hashBytes = digest.digest(content);

        StringBuilder sb = new StringBuilder();

        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    private String extractFormat(String filename) {

        if (filename == null || !filename.contains(".")) {
            return null;
        }

        return filename.substring(
                filename.lastIndexOf(".") + 1
        );
    }

    /**
     * Remove acentos e troca espaços/caracteres especiais
     * por "_" para gerar um nome seguro para o bucket.
     */
    private String sanitizeFilename(String filename) {

        if (filename == null || filename.isBlank()) {
            return "arquivo_sem_nome";
        }

        String semAcento = Normalizer
                .normalize(filename, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return semAcento.replaceAll(
                "[^a-zA-Z0-9._-]",
                "_"
        );
    }
}