package catdevs.georuraldatahub.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.entity.Correction;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.repository.CorrectionRepository;

/**
 * Regras de negócio para criação e gerenciamento de correções.
 *
 * Uma correção só pode ser editada ou excluída enquanto estiver em RASCUNHO.
 */
@Service
public class CorrectionService {

    private static final String STATUS_DRAFT = "RASCUNHO";
    private static final String STATUS_APPLIED = "APLICADA";

    private final CorrectionRepository correctionRepository;
    private final FileService fileService;
    private final UserService userService;

    public CorrectionService(
            CorrectionRepository correctionRepository,
            FileService fileService,
            UserService userService) {

        this.correctionRepository = correctionRepository;
        this.fileService = fileService;
        this.userService = userService;
    }

    /**
     * Cria uma correção em rascunho associada a um arquivo e a um usuário.
     */
    @Transactional
    public Correction create(
            Long fileId,
            Long userId,
            String fieldName,
            String originalValue,
            String proposedValue) {

        validateRequiredText(fieldName, "O nome do campo é obrigatório.");
        validateRequiredText(proposedValue, "O valor proposto é obrigatório.");

        File file = fileService.findEntityById(fileId);
        User user = userService.findEntityById(userId);

        Correction correction = new Correction();
        correction.setFile(file);
        correction.setUser(user);
        correction.setFieldName(fieldName.trim());
        correction.setOriginalValue(originalValue);
        correction.setProposedValue(proposedValue);
        correction.setStatus(STATUS_DRAFT);

        return correctionRepository.save(correction);
    }

    /**
     * Lista todas as correções de um arquivo,
     * da mais recente para a mais antiga.
     */
    @Transactional(readOnly = true)
    public List<Correction> findByFile(Long fileId) {

        fileService.findEntityById(fileId);

        return correctionRepository.findByFile_IdOrderByCreatedAtDesc(fileId);
    }

    /**
     * Consulta uma correção específica pertencente ao arquivo informado.
     */
    @Transactional(readOnly = true)
    public Correction findById(Long fileId, Long correctionId) {

        fileService.findEntityById(fileId);

        return correctionRepository.findByIdAndFile_Id(correctionId, fileId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Correção não encontrada para o arquivo informado."));
    }

    /**
     * Atualiza os dados de uma correção,
     * desde que ela ainda esteja em rascunho.
     */
    @Transactional
    public Correction update(
            Long fileId,
            Long correctionId,
            String fieldName,
            String proposedValue) {

        validateRequiredText(fieldName, "O nome do campo é obrigatório.");
        validateRequiredText(proposedValue, "O valor proposto é obrigatório.");

        Correction correction = findById(fileId, correctionId);

        ensureDraft(correction);

        correction.setFieldName(fieldName.trim());
        correction.setProposedValue(proposedValue);

        // Preserva o valor original para manter o histórico da correção.
        return correctionRepository.save(correction);
    }

    /**
     * Exclui uma correção somente se ela ainda estiver em rascunho.
     */
    @Transactional
    public void delete(Long fileId, Long correctionId) {

        Correction correction = findById(fileId, correctionId);

        ensureDraft(correction);

        correctionRepository.delete(correction);
    }

    /**
     * Marca a correção como aplicada.
     * Depois disso, edição e exclusão são bloqueadas.
     */
    @Transactional
    public Correction markAsApplied(Long fileId, Long correctionId) {

        Correction correction = findById(fileId, correctionId);

        ensureDraft(correction);

        correction.setStatus(STATUS_APPLIED);

        return correctionRepository.save(correction);
    }

    private void ensureDraft(Correction correction) {

        if (!STATUS_DRAFT.equalsIgnoreCase(correction.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Somente correções em rascunho podem ser editadas ou excluídas.");
        }
    }

    private void validateRequiredText(String value, String message) {

        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}