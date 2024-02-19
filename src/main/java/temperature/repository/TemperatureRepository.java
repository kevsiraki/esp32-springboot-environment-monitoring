package temperature.repository;

import temperature.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TemperatureRepository extends JpaRepository<Temperature, String> {
    List<Temperature> findByTimestampBetween(long startTimestamp, long endTimestamp);

    Optional<Temperature> findFirstByDevice_ApiKeyOrderByTimestampDesc(String apiKey);

    Optional<Temperature> findFirstByOrderByTimestampDesc();
}
