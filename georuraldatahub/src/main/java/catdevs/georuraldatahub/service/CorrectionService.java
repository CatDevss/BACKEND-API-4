package catdevs.georuraldatahub.service;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.entity.Correction;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.repository.CorrectionRepository;

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

    @Transactional
    public Correction create(
            Long fileId,
            Long actorUserId,
            String fieldName,
            String originalValue,
            String proposedValue) {

        validateRequiredText(
                fieldName,
                "O nome do campo é obrigatório.");

        validateRequiredText(
                proposedValue,
                "O valor proposto é obrigatório.");

        File file = fileService.findEntityById(fileId);
        User actor = userService.findEntityById(actorUserId);

        Correction correction = new Correction();
        correction.setFile(file);
        correction.setUser(actor);
        correction.setFieldName(fieldName.trim());
        correction.setOriginalValue(originalValue);
        correction.setProposedValue(proposedValue);
        correction.setStatus(STATUS_DRAFT);

        return correctionRepository.save(correction);
    }

    @Transactional(readOnly = true)
    public List<Correction> findByFile(Long fileId) {

        fileService.findEntityById(fileId);

        return correctionRepository
                .findByFile_IdOrderByCreatedAtDesc(fileId);
    }

    @Transactional(readOnly = true)
    public Correction findById(
            Long fileId,
            Long correctionId) {

        fileService.findEntityById(fileId);

        return correctionRepository
                .findByIdAndFile_Id(correctionId, fileId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Correção não encontrada para o arquivo informado."));
    }

    @Transactional
    public Correction update(
            Long fileId,
            Long correctionId,
            Long actorUserId,
            String fieldName,
            String proposedValue) {

        validateRequiredText(
                fieldName,
                "O nome do campo é obrigatório.");

        validateRequiredText(
                proposedValue,
                "O valor proposto é obrigatório.");

        Correction correction = findById(fileId, correctionId);

        ensureOwner(correction, actorUserId);
        ensureDraft(correction);

        correction.setFieldName(fieldName.trim());
        correction.setProposedValue(proposedValue);

        // Mantém o valor original para preservar o histórico.
        return correctionRepository.save(correction);
    }

    @Transactional
    public void delete(
            Long fileId,
            Long correctionId,
            Long actorUserId) {

        Correction correction = findById(fileId, correctionId);

        ensureOwner(correction, actorUserId);
        ensureDraft(correction);

        correctionRepository.delete(correction);
    }

    @Transactional
    public Correction markAsApplied(
            Long fileId,
            Long correctionId,
            Long actorUserId) {

        Correction correction = findById(fileId, correctionId);

        ensureOwner(correction, actorUserId);
        ensureDraft(correction);

        correction.setStatus(STATUS_APPLIED);

        return correctionRepository.save(correction);
    }

    private void ensureOwner(
            Correction correction,
            Long actorUserId) {

        if (actorUserId == null
                || correction.getUser() == null
                || !Objects.equals(
                        correction.getUser().getId(),
                        actorUserId)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Você não tem permissão para alterar esta correção.");
        }
    }

    private void ensureDraft(Correction correction) {

        if (!STATUS_DRAFT.equalsIgnoreCase(
                correction.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Somente correções em rascunho podem ser editadas, excluídas ou aplicadas.");
        }
    }

    private void validateRequiredText(
            String value,
            String message) {

        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    message);
        }
    }
}