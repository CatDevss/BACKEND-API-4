package catdevs.georuraldatahub.dto;

import catdevs.georuraldatahub.entity.enums.UserType;

public record UserResponseDTO(
        Long id,
        String name,
        String email,
        UserType userType,
        int userStatus
) {
}