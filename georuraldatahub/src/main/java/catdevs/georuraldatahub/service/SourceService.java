package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.dto.SourceCreateRequestDTO;
import catdevs.georuraldatahub.dto.SourceResponseDTO;
import catdevs.georuraldatahub.entity.Source;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.repository.SourceRepository;
import catdevs.georuraldatahub.repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;

@Service
public class SourceService {

    private final SourceRepository sourceRepository;
    private final UserRepository userRepository;

    public SourceService(
            SourceRepository sourceRepository,
            UserRepository userRepository) {
        this.sourceRepository = sourceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public SourceResponseDTO create(SourceCreateRequestDTO request) {
        validateRequest(request);

        String name = request.name().trim();
        String url = request.url().trim();

        if (sourceRepository.existsByName(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Já existe uma fonte com esse nome"
            );
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Usuário não encontrado"
                ));

        Source source = new Source();
        source.setName(name);
        source.setUrl(url);
        source.setDescription(request.description());
        source.setUser(user);
        source.setActive(true);

        try {
            return toDTO(sourceRepository.saveAndFlush(source));
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueConstraintViolation(exception)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Já existe uma fonte com esse nome",
                        exception
                );
            }

            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<SourceResponseDTO> listAll() {
        return sourceRepository.findAll()
                .stream()
                .map(SourceService::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public SourceResponseDTO getById(Long id) {
        return toDTO(findSource(id));
    }

    @Transactional
    public SourceResponseDTO update(
            Long id,
            SourceCreateRequestDTO request) {

        validateRequest(request);

        Source source = findSource(id);

        String name = request.name().trim();
        String url = request.url().trim();

        if (sourceRepository.existsByNameAndIdNot(name, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Já existe uma fonte com esse nome"
            );
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Usuário não encontrado"
                ));

        source.setName(name);
        source.setUrl(url);
        source.setDescription(request.description());
        source.setUser(user);

        try {
            return toDTO(sourceRepository.saveAndFlush(source));
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueConstraintViolation(exception)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Já existe uma fonte com esse nome",
                        exception
                );
            }

            throw exception;
        }
    }

    @Transactional
    public SourceResponseDTO deactivate(Long id) {
        Source source = findSource(id);

        source.setActive(false);

        return toDTO(sourceRepository.save(source));
    }

    @Transactional(readOnly = true)
    public Source findActive(Long id) {
        Source source = findSource(id);

        if (!source.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A fonte está inativa"
            );
        }

        return source;
    }

    private Source findSource(Long id) {
        if (id == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O ID da fonte é obrigatório"
            );
        }

        return sourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Fonte não encontrada"
                ));
    }

    private void validateRequest(SourceCreateRequestDTO request) {
        if (request == null
                || request.name() == null
                || request.name().isBlank()
                || request.url() == null
                || request.url().isBlank()
                || request.userId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nome, URL e usuário são obrigatórios"
            );
        }
    }

    private static SourceResponseDTO toDTO(Source source) {
        return new SourceResponseDTO(
                source.getId(),
                source.getName(),
                source.getDateCreation(),
                source.getUrl(),
                source.getUser().getId(),
                source.getDescription(),
                source.isActive()
        );
    }

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