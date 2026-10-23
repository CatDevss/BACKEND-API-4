
package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.dto.SourceCreateRequestDTO;
import catdevs.georuraldatahub.dto.SourceResponseDTO;
import catdevs.georuraldatahub.entity.Source;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.repository.DictionaryRepository;
import catdevs.georuraldatahub.repository.SourceRepository;
import catdevs.georuraldatahub.repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

@Service
public class SourceService {

    private final SourceRepository sourceRepository;
    private final UserRepository userRepository;
    private final DictionaryRepository dictionaryRepository;

    public SourceService(
            SourceRepository sourceRepository,
            UserRepository userRepository,
            DictionaryRepository dictionaryRepository) {
        this.sourceRepository = sourceRepository;
        this.userRepository = userRepository;
        this.dictionaryRepository = dictionaryRepository;
    }

    @Transactional
    public SourceResponseDTO create(SourceCreateRequestDTO request) {
        validateSourceRequest(request);

        String name = request.name().trim();

        if (sourceRepository.existsByName(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Já existe uma fonte com esse nome");
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Usuário não encontrado"));

        Source source = new Source();
        source.setName(name);
        source.setUrl(request.url().trim());
        source.setUser(user);

        try {
            return toDTO(sourceRepository.saveAndFlush(source));
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

    @Transactional(readOnly = true)
    public List<SourceResponseDTO> listAll() {
        return sourceRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public SourceResponseDTO getById(Long id) {
        return toDTO(findSource(id));
    }

    @Transactional
    public SourceResponseDTO update(Long id, SourceCreateRequestDTO request) {
        validateSourceRequest(request);

        Source source = findSource(id);
        String name = request.name().trim();

        boolean nameUsedByAnother = sourceRepository.findAll().stream()
                .anyMatch(other ->
                        !other.getId().equals(id)
                                && other.getName() != null
                                && other.getName().equalsIgnoreCase(name));

        if (nameUsedByAnother) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Já existe uma fonte com esse nome");
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Usuário não encontrado"));

        source.setName(name);
        source.setUrl(request.url().trim());
        source.setUser(user);

        try {
            return toDTO(sourceRepository.saveAndFlush(source));
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

    @Transactional
    public void delete(Long id) {
        Source source = findSource(id);

        if (dictionaryRepository.existsBySourceId(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A fonte possui dicionários associados e não pode ser excluída para preservar o histórico.");
        }

        sourceRepository.delete(source);
    }

    private Source findSource(Long id) {
        return sourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Fonte não encontrada."));
    }

    private void validateSourceRequest(SourceCreateRequestDTO request) {
        if (request == null
                || request.name() == null
                || request.name().isBlank()
                || request.url() == null
                || request.url().isBlank()
                || request.userId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nome, URL e usuário são obrigatórios.");
        }

        validateSourceUri(request.url().trim());
    }

    private URI validateSourceUri(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null
                    || host == null
                    || !(scheme.equalsIgnoreCase("http")
                    || scheme.equalsIgnoreCase("https"))
                    || uri.getUserInfo() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "A URL deve ser HTTP ou HTTPS válida.");
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);

            if (normalizedHost.equals("localhost")
                    || normalizedHost.endsWith(".localhost")
                    || normalizedHost.endsWith(".local")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "A URL aponta para um host local.");
            }

            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "A URL não pode apontar para um endereço de rede privado.");
                }
            }

            return uri;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "URL inválida ou host não resolvido.",
                    e);
        }
    }

    private SourceResponseDTO toDTO(Source source) {
        return new SourceResponseDTO(
                source.getId(),
                source.getName(),
                source.getDateCreation(),
                source.getUrl(),
                source.getUser() == null ? null : source.getUser().getId());
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
