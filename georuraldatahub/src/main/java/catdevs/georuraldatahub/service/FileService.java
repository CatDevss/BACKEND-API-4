package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.entity.Version;
import catdevs.georuraldatahub.exception.DuplicateFileException;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.FileRepository;
import catdevs.georuraldatahub.repository.UserRepository;
import catdevs.georuraldatahub.repository.VersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    public File uploadFile(MultipartFile multipartFile, Long datasetId, Long userId) throws Exception {

        byte[] content = multipartFile.getBytes();
        String hash = calculateHash(content);

        if (fileRepository.existsByHashInDataset(datasetId, hash)) {
            throw new DuplicateFileException("Este arquivo já foi enviado anteriormente para este conjunto.");
        }

        Version version = createVersion(datasetId, userId);

        String location = bucketService.uploadToRawZone(multipartFile, datasetId, hash);

        File file = new File(
                version,
                multipartFile.getOriginalFilename(),
                extractFormat(multipartFile.getOriginalFilename()),
                hash,
                location
        );

        return fileRepository.save(file);
    }

    private Version createVersion(Long datasetId, Long userId) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Conjunto não encontrado."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        Version version = new Version(dataset, LocalDateTime.now(), user);
        return versionRepository.save(version);
    }

    private String calculateHash(byte[] content) throws NoSuchAlgorithmException {
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
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}