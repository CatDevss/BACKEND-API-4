package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileService {

    private final UserRepository userRepository;

    public FileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User identifyUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Usuário é obrigatório"
            );
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Usuário não encontrado"
                ));
    }
}