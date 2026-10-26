package catdevs.georuraldatahub.service;

import java.sql.SQLException;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.dto.SourceCreateRequestDTO;
import catdevs.georuraldatahub.dto.SourceResponseDTO;
import catdevs.georuraldatahub.dto.SourceUpdateRequestDTO;
import catdevs.georuraldatahub.entity.Source;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.repository.SourceRepository;

@Service
public class SourceService {

    private final SourceRepository sourceRepository;
    private final UserService userService;

    public SourceService(SourceRepository sourceRepository, UserService userService) {
        this.sourceRepository = sourceRepository;
        this.userService = userService;
    }

    // CADASTRAR FONTE
    public SourceResponseDTO create(SourceCreateRequestDTO request) {

        if (sourceRepository.existsByName(request.name().trim())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Já existe uma fonte com esse nome");
        }

        User user = userService.findEntityById(request.userId());

        Source source = new Source();
        source.setName(request.name().trim());
        source.setUrl(request.url().trim());
        source.setUser(user);
        source.setDescription(request.description());
        source.setStatus(1); // Ativo por padrão

        try {
            Source savedSource = sourceRepository.saveAndFlush(source);

            return toResponse(savedSource);

        } catch (DataIntegrityViolationException e) {

            if (isUniqueConstraintViolation(e)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Já existe uma fonte com esse nome",
                        e);
            }

            throw e;
        }
    }

    public Source findEntityById(Long id) {
        return sourceRepository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Fonte não encontrada."));
    }

    // LISTAR FONTES
    public List<SourceResponseDTO> listAll() {
        return sourceRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // BUSCAR FONTE POR ID
    public SourceResponseDTO findById(Long id) {
        Source source = findEntityById(id);
        return toResponse(source);
    }

    // ALTERAR NOME, URL E DESCRIÇÃO
    public SourceResponseDTO update(Long id, SourceUpdateRequestDTO request) {
        Source source = findEntityById(id);
        String name = request.name().trim();
        if (sourceRepository.existsByNameAndIdNot(name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma fonte com esse nome.");
        }
        source.setName(name);
        source.setUrl(request.url().trim());
        source.setDescription(request.description());
        try {
            Source updatedSource = sourceRepository.saveAndFlush(source);
            return toResponse(updatedSource);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(e)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma fonte com esse nome.", e);
            }
            throw e;
        }
    }

    // DESATIVAR FONTE
    public SourceResponseDTO deactivate(Long id) {
        Source source = findEntityById(id);
        source.setStatus(0);
        Source updatedSource = sourceRepository.save(source);
        return toResponse(updatedSource);
    }

    // ATIVAR FONTE
    public SourceResponseDTO activate(Long id) {
        Source source = findEntityById(id);
        source.setStatus(1);
        Source updatedSource = sourceRepository.save(source);
        return toResponse(updatedSource);
    }

    // CONVERTER ENTIDADE PARA DTO
    private SourceResponseDTO toResponse(Source source) {
        return new SourceResponseDTO(source.getId(), source.getName(), source.getDateCreation(), source.getUrl(),
                source.getDescription(), source.getStatus(), source.getUser().getId());
    }

    // VERIFICAR VIOLAÇÃO DE CHAVE ÚNICA NO ORACLE
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