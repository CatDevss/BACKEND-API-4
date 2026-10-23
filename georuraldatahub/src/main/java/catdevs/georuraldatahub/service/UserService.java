package catdevs.georuraldatahub.service;

import java.util.List;

import org.springframework.stereotype.Service;

import catdevs.georuraldatahub.dto.UserResponseDTO;
import catdevs.georuraldatahub.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponseDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(user -> new UserResponseDTO(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getUserType(),
                        user.getUserStatus()))
                .toList();
    }
}