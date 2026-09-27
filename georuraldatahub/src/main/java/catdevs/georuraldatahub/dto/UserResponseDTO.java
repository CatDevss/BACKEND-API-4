package catdevs.georuraldatahub.dto;

import catdevs.georuraldatahub.entity.UserType;

public record UserResponseDTO(
        Long id,
        String name,
        String email,
        UserType userType,
        int userStatus
) {
}