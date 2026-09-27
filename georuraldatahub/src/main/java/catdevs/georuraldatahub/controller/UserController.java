package catdevs.georuraldatahub.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import catdevs.georuraldatahub.dto.UserResponseDTO;
import catdevs.georuraldatahub.service.UserService;

@RestController 
@CrossOrigin 
@RequestMapping ("usuarios")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping 
    public List<UserResponseDTO> getAllUsers() {
        return userService.getAllUsers();
    }
}
