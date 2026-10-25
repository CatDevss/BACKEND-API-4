package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileServiceHashTest {

    private final FileService fileService = new FileService(null, null, null, null, null, null, null, null, null) ;



    @Test
    void sameContentMustReturnSameHash() throws Exception {
        byte[] content = "conteudo de teste para o hash".getBytes();

        MockMultipartFile file1 = new MockMultipartFile("file", "a.txt", "text/plain", content);
        MockMultipartFile file2 = new MockMultipartFile("file", "b.txt", "text/plain", content);

        String hash1 = fileService.calculateHash(file1);
        String hash2 = fileService.calculateHash(file2);

        assertEquals(hash1, hash2);
    }
}
