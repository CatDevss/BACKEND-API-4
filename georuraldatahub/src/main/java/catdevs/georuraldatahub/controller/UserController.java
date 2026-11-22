package catdevs.georuraldatahub.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import catdevs.georuraldatahub.security.Permissions;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import catdevs.georuraldatahub.dto.CurrentUserDTO;
import catdevs.georuraldatahub.dto.UserResponseDTO;
import catdevs.georuraldatahub.security.CurrentUserService;
import catdevs.georuraldatahub.service.UserService;

@RestController
@RequestMapping("/usuarios")
public class UserController {

    private final UserService userService;
    private final CurrentUserService currentUserService;

    public UserController(UserService userService, CurrentUserService currentUserService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    /** Lista de usuários (somente ADMIN, ver SecurityConfig). */
    @PreAuthorize(Permissions.USERS_MANAGE)
    @GetMapping
    public List<UserResponseDTO> getAllUsers() {
        return userService.getAllUsers();
    }

    /** Usuário logado: sincroniza com a tabela usuario e devolve id + perfis. */
    @PreAuthorize(Permissions.AUTHENTICATED)
    @GetMapping("/me")
    public CurrentUserDTO me() {
        return currentUserService.current();
    }
}
