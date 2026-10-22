package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.Mockito.mock;

import catdevs.georuraldatahub.validation.FileValidationService;

class FileServiceTest {

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(
                mock(BucketService.class),
                mock(DatasetService.class),
                mock(DictionaryService.class),
                mock(FileValidationService.class)
        );
    }

    @Test
    void calculateHashShouldReturnSameHashForSameContent()
            throws IOException, NoSuchAlgorithmException {

        MockMultipartFile firstFile = new MockMultipartFile(
                "file", "dados.csv", "text/csv", "nome,idade\nAna,20".getBytes()
        );

        MockMultipartFile secondFile = new MockMultipartFile(
                "file", "outro.csv", "text/csv", "nome,idade\nAna,20".getBytes()
        );

        assertEquals(
                fileService.calculateHash(firstFile),
                fileService.calculateHash(secondFile)
        );
    }

    @Test
    void calculateHashShouldReturnDifferentHashForDifferentContent()
            throws IOException, NoSuchAlgorithmException {

        MockMultipartFile firstFile = new MockMultipartFile(
                "file", "dados.csv", "text/csv", "Ana".getBytes()
        );

        MockMultipartFile secondFile = new MockMultipartFile(
                "file", "dados.csv", "text/csv", "Maria".getBytes()
        );

        org.junit.jupiter.api.Assertions.assertNotEquals(
                fileService.calculateHash(firstFile),
                fileService.calculateHash(secondFile)
        );
    }
}