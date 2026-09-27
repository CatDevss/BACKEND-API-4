package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Testes unitários para o cálculo de hash de arquivos em {@link FileService}.
 *
 * Cobre a task T2.24: garante que o hash gerado depende apenas do
 * conteúdo do arquivo, e não do nome ou de qualquer outro metadado.
 * Essa propriedade é essencial pra checagem de duplicado
 */
class FileServiceHashTest {

    private final FileService fileService = new FileService(null, null, null, null, null);


        /**
     * Verifica que dois arquivos com o mesmo conteúdo, mas nomes
     * diferentes (a.txt e b.txt), geram exatamente
     * o mesmo hash SHA-256.
     *
     * Se esse teste falhar, significa que {@link FileService#calculateHash}
     * passou a depender de algo além dos bytes do arquivo (nome,
     * tipo de conteúdo, etc.), o que quebraria a detecção de
     * arquivos duplicados.
     */

    @Test 
    void sameContentMustReturnSameHash() throws Exception {
        byte[] content = "conteudo de teste para o hash".getBytes();

        MockMultipartFile file1 = new MockMultipartFile("file", "a.txt", "text/plain", content);
        MockMultipartFile file2 = new MockMultipartFile("file", "b.txt", "text/plain", content);

        String hash1 = fileService.calculateHash(file1);
        String hash2 = fileService.calculateHash(file2);
        System.out.println("Hash 1: " + hash1);
        System.out.println("Hash 2: " + hash2);

        assertEquals(hash1, hash2);
    }
}