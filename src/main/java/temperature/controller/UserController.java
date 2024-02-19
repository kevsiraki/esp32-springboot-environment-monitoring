package temperature.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;
import temperature.model.User;
import temperature.repository.UserRepository;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Api(tags = "User Management")
public class UserController {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    @ApiOperation("Register a new user")
    public ResponseEntity<EntityModel<String>> register(@RequestBody User user) {
        // Validate user input
        if (user.getUsername() == null || user.getPassword() == null) {
            return ResponseEntity.badRequest().body(EntityModel.of("Username and password are required"));
        }

        // Check if the username already exists
        if (userRepository.findByUsername(user.getUsername()) != null) {
            return ResponseEntity.badRequest().body(EntityModel.of("Username already exists"));
        }

        // Hash password
        String passwordHash = passwordEncoder.encode(user.getPassword());
        user.setPassword(passwordHash);

        // Generate API key
        String apiKey = UUID.randomUUID().toString();

        // Save user to the database
        user.setApiKey(apiKey);
        userRepository.save(user);

        EntityModel<String> response = EntityModel.of("Registration successful. Your API key is: " + apiKey +
                ". Include the 'X-API-Key' header in your requests with the value '" + apiKey
                + "' for authentication.");

        response.add(WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(UserController.class).register(user))
                .withSelfRel());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    @ApiOperation("Login to get the API key")
    public ResponseEntity<EntityModel<String>> login(@RequestBody Map<String, String> credentials,
            @RequestParam(required = false, defaultValue = "false") boolean regen) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        // Validate username and password
        if (username == null || password == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(EntityModel.of("Username and password are required"));
        }

        // Find user by username
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(EntityModel.of("User not found"));
        }

        // Check if password matches
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(EntityModel.of("Invalid password"));
        }

        // Regenerate API key if regen parameter is true
        if (regen) {
            String newApiKey = UUID.randomUUID().toString();
            user.setApiKey(newApiKey);
            userRepository.save(user);
            EntityModel<String> response = EntityModel.of("New API key generated successfully: " + newApiKey +
                    ". Include the 'X-API-Key' header in your requests with the value '" + newApiKey
                    + "' for authentication.");
            response.add(WebMvcLinkBuilder
                    .linkTo(WebMvcLinkBuilder.methodOn(UserController.class).login(credentials, true)).withSelfRel());
            return ResponseEntity.ok(response);
        }

        EntityModel<String> response = EntityModel.of("Your API key is: " + user.getApiKey() +
                ". Include the 'X-API-Key' header in your requests with the value '" + user.getApiKey()
                + "' for authentication.");
        response.add(WebMvcLinkBuilder
                .linkTo(WebMvcLinkBuilder.methodOn(UserController.class).login(credentials, false)).withSelfRel());
        response.add(WebMvcLinkBuilder
                .linkTo(WebMvcLinkBuilder.methodOn(UserController.class).refetchApiKey(user.getApiKey()))
                .withRel("refetchApiKey"));
        response.add(WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(UserController.class).register(user))
                .withRel("register"));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/refetchApiKey")
    @ApiOperation("Refetch API key after login")
    public ResponseEntity<EntityModel<String>> refetchApiKey(@ApiIgnore @RequestParam String apiKey) {
        User user = userRepository.findByApiKey(apiKey);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(EntityModel.of("Invalid API key"));
        }
        EntityModel<String> response = EntityModel.of("Your API key is: " + user.getApiKey() +
                ". Include the 'X-API-Key' header in your requests with the value '" + user.getApiKey()
                + "' for authentication.");
        response.add(WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(UserController.class).refetchApiKey(apiKey))
                .withSelfRel());
        return ResponseEntity.ok(response);
    }
}
