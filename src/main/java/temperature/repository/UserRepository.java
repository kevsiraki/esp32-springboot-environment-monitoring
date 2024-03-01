package temperature.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import temperature.exception.*;
import temperature.model.*;
import temperature.startup.*;
import temperature.repository.*;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);

    User findByApiKey(String apiKey);
}
