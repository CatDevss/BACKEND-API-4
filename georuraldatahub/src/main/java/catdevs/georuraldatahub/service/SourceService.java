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
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.LocalDateTime;

@Service
public class SourceService {

    private final SourceRepository sourceRepository;
    private final UserRepository userRepository;

    public SourceService(
            SourceRepository sourceRepository,
            UserRepository userRepository
    ) {
        this.sourceRepository = sourceRepository;
        this.userRepository = userRepository;
    }

    public SourceResponseDTO create(SourceCreateRequestDTO request) {

        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nome da fonte é obrigatório"
            );
        }

        if (request.url() == null || request.url().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "URL da fonte é obrigatória"
            );
        }

        if (request.userId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Usuário é obrigatório"
            );
        }

        if (sourceRepository.existsByName(request.name().trim())) {
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

        Source source = new Source(
                request.name().trim(),
                LocalDateTime.now(),
                request.url().trim(),
                user
        );

        try {
            Source savedSource = sourceRepository.saveAndFlush(source);

            return new SourceResponseDTO(
                    savedSource.getId(),
                    savedSource.getName(),
                    savedSource.getDateCreation(),
                    savedSource.getUrl(),
                    savedSource.getUser().getId()
            );

        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(e)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Já existe uma fonte com esse nome",
                        e
                );
            }

            throw e;
        }
    }

    private boolean isUniqueConstraintViolation(
            DataIntegrityViolationException exception
    ) {
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