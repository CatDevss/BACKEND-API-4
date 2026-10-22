package catdevs.georuraldatahub.service;

import java.util.List;

import catdevs.georuraldatahub.dto.UserResponseDTO;
import catdevs.georuraldatahub.entity.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<UserResponseDTO> getAllUsers() {

        return entityManager.createQuery(
                """
                SELECT u
                FROM User u
                ORDER BY u.id
                """,
                User.class
        )
        .getResultList()
        .stream()
        .map(user -> new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getUserType(),
                user.getUserStatus()
        ))
        .toList();
    }

    @Transactional(readOnly = true)
    public User findEntityById(Long id) {

        User user = entityManager.find(User.class, id);

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Usuário não encontrado"
            );
        }

        return user;
    }
}