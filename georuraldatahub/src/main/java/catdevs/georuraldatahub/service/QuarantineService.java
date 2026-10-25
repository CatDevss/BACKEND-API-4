package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.dto.*;
import catdevs.georuraldatahub.entity.Quarantine;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.repository.QuarantineRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuarantineService {
    private static final String IN_ANALYSIS = "EM_ANALISE";
    private static final String RESOLVED = "RESOLVIDA";
    private static final String CLOSED_WITHOUT_CORRECTION = "ENCERRADA_SEM_CORRECAO";
    private final QuarantineRepository quarantines;
    private final UserService users;

    public QuarantineService(QuarantineRepository quarantines, UserService users) {
        this.quarantines = quarantines;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public QuarantinePageDTO list(String status, Long responsibleId, Long fileId, boolean unassigned, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Informe page >= 0 e size entre 1 e 100.");
        optionalPositive(responsibleId); optionalPositive(fileId);
        if (unassigned && responsibleId != null) throw new IllegalArgumentException("Escolha sem responsável ou um responsável específico.");
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalizedStatus != null && normalizedStatus.length() > 30) throw new IllegalArgumentException("Status excede 30 caracteres.");
        var result = quarantines.search(normalizedStatus, responsibleId, fileId, unassigned,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateEntry", "id")));
        return new QuarantinePageDTO(result.getContent().stream().map(this::response).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public QuarantineResponseDTO get(Long id) {
        positive(id);
        return response(quarantines.findById(id).orElseThrow(this::missing));
    }

    @Transactional
    public QuarantineResponseDTO claim(Long id, Long userId) {
        User user = activeUser(userId);
        Quarantine quarantine = locked(id);
        ensureOpen(quarantine);
        if (quarantine.getUser() != null) {
            if (Objects.equals(quarantine.getUser().getId(), userId)) return response(quarantine);
            throw conflict("Esta quarentena já foi assumida por outra pessoa.");
        }
        quarantine.setUser(user);
        quarantine.setStatus(IN_ANALYSIS);
        return response(quarantines.save(quarantine));
    }

    @Transactional
    public QuarantineResponseDTO updateTreatment(Long id, Long userId, String treatment) {
        activeUser(userId);
        Quarantine quarantine = locked(id);
        ensureOwner(quarantine, userId);
        quarantine.setTreatment(validTreatment(treatment));
        return response(quarantines.save(quarantine));
    }

    /** A resolução exige uma revisão aprovada e vinculada, ainda pendente da BE-06. */
    @Transactional
    public QuarantineResponseDTO resolve(Long id, Long userId, String treatment) {
        activeUser(userId);
        Quarantine quarantine = locked(id);
        ensureOwner(quarantine, userId);
        validTreatment(treatment);
        throw conflict("Não é possível resolver apenas com uma observação. É necessária uma revisão corrigida "
                + "e aprovada em revalidação vinculada à quarentena; esse fluxo ainda não está disponível. "
                + "Para encerrar a análise sem corrigir os dados, use a ação encerrar.");
    }

    /** Encerra a análise sem aprovar dados ou modificar o arquivo e o relatório originais. */
    @Transactional
    public QuarantineResponseDTO closeWithoutCorrection(Long id, Long userId, String treatment) {
        activeUser(userId);
        Quarantine quarantine = locked(id);
        ensureOwner(quarantine, userId);
        quarantine.setTreatment(validTreatment(treatment));
        quarantine.setStatus(CLOSED_WITHOUT_CORRECTION);
        quarantine.setDateResolution(LocalDateTime.now());
        return response(quarantines.save(quarantine));
    }

    private Quarantine locked(Long id) {
        positive(id);
        return quarantines.findLockedById(id).orElseThrow(this::missing);
    }
    private User activeUser(Long id) {
        positive(id);
        User user = users.findEntityById(id);
        if (user.getUserStatus() != 1) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário inativo não pode assumir ou alterar quarentenas.");
        return user;
    }
    private void ensureOpen(Quarantine quarantine) {
        if (quarantine.getDateResolution() != null || RESOLVED.equals(quarantine.getStatus())
                || CLOSED_WITHOUT_CORRECTION.equals(quarantine.getStatus()))
            throw conflict("A análise desta quarentena já foi encerrada.");
    }
    private void ensureOwner(Quarantine quarantine, Long userId) {
        ensureOpen(quarantine);
        if (quarantine.getUser() == null) throw conflict("Assuma a quarentena antes de registrar observações, resolver ou encerrar.");
        if (!Objects.equals(quarantine.getUser().getId(), userId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente o responsável pode alterar esta quarentena.");
    }
    private String validTreatment(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Descreva o que foi feito na análise.");
        String text = value.trim();
        if (text.getBytes(StandardCharsets.UTF_8).length > 500)
            throw new IllegalArgumentException("A observação excede o limite de 500 bytes do banco.");
        return text;
    }
    private void positive(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Informe um ID positivo.");
    }
    private void optionalPositive(Long id) { if (id != null) positive(id); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Quarentena não encontrada."); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    private QuarantineResponseDTO response(Quarantine q) {
        var file = q.getFile();
        var ingestion = q.getIngestion();
        var dictionary = ingestion == null ? null : ingestion.getDictionary();
        var source = dictionary == null ? null : dictionary.getSource();
        var user = q.getUser();
        return new QuarantineResponseDTO(q.getId(), ingestion == null ? null : ingestion.getId(),
                file == null ? null : file.getId(), file == null ? null : file.getName(),
                source == null ? null : source.getId(), source == null ? null : source.getName(),
                q.getStatus(), q.getReason(), q.getTreatment(), q.getDateEntry(), q.getDateResolution(),
                user == null ? null : user.getId(), user == null ? null : user.getName(),
                file == null ? null : "/arquivos/" + file.getId() + "/validacao");
    }
}
