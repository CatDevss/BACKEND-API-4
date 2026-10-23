package catdevs.georuraldatahub.service;

import java.sql.SQLException;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.dto.SourceCreateRequestDTO;
import catdevs.georuraldatahub.dto.SourceResponseDTO;
import catdevs.georuraldatahub.entity.Source;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.repository.DictionaryRepository;
import catdevs.georuraldatahub.repository.SourceRepository;

@Service
public class SourceService {

    private final SourceRepository sourceRepository;
    private final DictionaryRepository dictionaryRepository;
    private final UserService userService;

    public SourceService(
            SourceRepository sourceRepository,
            DictionaryRepository dictionaryRepository,
            UserService userService) {
        this.sourceRepository = sourceRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.userService = userService;
    }

    // CREATE
    @Transactional
    public SourceResponseDTO create(SourceCreateRequestDTO request) {
        validateRequest(request);

        String name = request.name().trim();
        String url = request.url().trim();

        if (sourceRepository.existsByName(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Já existe uma fonte com esse nome.");
        }

        User user = userService.findEntityById(request.userId());

        Source source = new Source();
        source.setName(name);
        source.setUrl(url);
        source.setDescription(normalizeDescription(request.description()));
        source.setUser(user);

        try {
            Source savedSource = sourceRepository.saveAndFlush(source);
            return toResponseDTO(savedSource);

        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(e)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Já existe uma fonte com esse nome.",
                        e);
            }

            throw e;
        }
    }

    // READ: buscar por ID
    @Transactional(readOnly = true)
    public SourceResponseDTO findById(Long id) {
        return toResponseDTO(findEntityById(id));
    }

    // Busca a entidade para uso interno
    @Transactional(readOnly = true)
    public Source findEntityById(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O ID da fonte deve ser válido.");
        }

        return sourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Fonte não encontrada."));
    }

    // READ: listar todas
    @Transactional(readOnly = true)
    public List<SourceResponseDTO> listAll() {
        return sourceRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    // UPDATE
    @Transactional
    public SourceResponseDTO update(
            Long id,
            SourceCreateRequestDTO request) {

        validateRequest(request);

        Source source = findEntityById(id);

        String name = request.name().trim();
        String url = request.url().trim();

        boolean nameChanged =
                !source.getName().equalsIgnoreCase(name);

        if (nameChanged && sourceRepository.existsByName(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Já existe uma fonte com esse nome.");
        }

        User user = userService.findEntityById(request.userId());

        source.setName(name);
        source.setUrl(url);
        source.setDescription(normalizeDescription(request.description()));
        source.setUser(user);

        try {
            Source updatedSource = sourceRepository.saveAndFlush(source);
            return toResponseDTO(updatedSource);

        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(e)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Já existe uma fonte com esse nome.",
                        e);
            }

            throw e;
        }
    }

    // DELETE
    @Transactional
    public void delete(Long id) {
        Source source = findEntityById(id);

        try {
            sourceRepository.delete(source);
            sourceRepository.flush();

        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Não foi possível excluir a fonte porque existem "
                            + "registros relacionados a ela.",
                    e);
        }
    }

    // Validação dos dados recebidos
    private void validateRequest(SourceCreateRequestDTO request) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Os dados da fonte são obrigatórios.");
        }

        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O nome da fonte é obrigatório.");
        }

        if (request.url() == null || request.url().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A URL da fonte é obrigatória.");
        }

        if (request.userId() == null || request.userId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O ID do usuário deve ser válido.");
        }
    }

    // Converte a entidade para o DTO de resposta
    private SourceResponseDTO toResponseDTO(Source source) {
        Long userId = source.getUser() != null
                ? source.getUser().getId()
                : null;

        return new SourceResponseDTO(
                source.getId(),
                source.getName(),
                source.getDateCreation(),
                source.getUrl(),
                source.getDescription(),
                userId);
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        return description.trim();
    }

    // Identifica violação de restrição única no Oracle
    private boolean isUniqueConstraintViolation(
            DataIntegrityViolationException exception) {

        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && sqlException.getErrorCode() == 1) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}