package temperature.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import temperature.model.User;
import temperature.repository.UserRepository;

import java.util.Collection;

public class ApiKeyAuthentication implements Authentication {

    private final String apiKey;
    private final UserRepository userRepository;
    private User authenticatedUser;

    public ApiKeyAuthentication(String apiKey, UserRepository userRepository) {
        this.apiKey = apiKey;
        this.userRepository = userRepository;
        this.authenticatedUser = authenticate(apiKey);
    }

    private User authenticate(String apiKey) {
        User user = userRepository.findByApiKey(apiKey);
        if (user != null) {
            return user;
        }
        return null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return null;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return authenticatedUser;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticatedUser != null;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        // No need to implement
    }

    @Override
    public String getName() {
        if (authenticatedUser != null) {
            return authenticatedUser.getUsername();
        }
        return null;
    }
}
