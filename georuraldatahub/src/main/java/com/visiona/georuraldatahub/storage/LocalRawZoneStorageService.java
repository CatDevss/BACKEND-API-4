package com.visiona.georuraldatahub.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalRawZoneStorageService implements RawZoneStorageService {

    private final Path basePath;

    public LocalRawZoneStorageService(@Value("${storage.raw-zone.base-path}") String basePath) {
        this.basePath = Paths.get(basePath);
    }

    @Override
    public String store(MultipartFile file, Long datasetId) throws IOException {
        Path datasetFolder = basePath.resolve(String.valueOf(datasetId));
        Files.createDirectories(datasetFolder);

        String storedFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path destination = datasetFolder.resolve(storedFileName);

        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

        return destination.toString();
    }
}