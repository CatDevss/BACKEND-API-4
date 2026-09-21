package com.visiona.georuraldatahub.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface RawZoneStorageService {

    String store(MultipartFile file, Long datasetId) throws IOException;
}