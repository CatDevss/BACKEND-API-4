package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.dto.SourceCreateRequestDTO;
import catdevs.georuraldatahub.dto.SourceResponseDTO;
import catdevs.georuraldatahub.entity.Source;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.repository.SourceRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import java.sql.SQLException;

@Service
public class SourceService {

    private final SourceRepository sourceRepository;
    private final UserService userService;

    public SourceService(SourceRepository sourceRepository, UserService userService) {
        this.sourceRepository = sourceRepository;
        this.userService = userService;
    }

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

        try {
            Source savedSource = sourceRepository.saveAndFlush(source);

            return new SourceResponseDTO(
                    savedSource.getId(),
                    savedSource.getName(),
                    savedSource.getDateCreation(),
                    savedSource.getUrl(),
                    savedSource.getUser().getId());

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

    public List<SourceResponseDTO> listAll() {
        return sourceRepository.findAll()
                .stream()
                .map(source -> new SourceResponseDTO(
                        source.getId(),
                        source.getName(),
                        source.getDateCreation(),
                        source.getUrl(),
                        source.getUser().getId()))
                .toList();
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