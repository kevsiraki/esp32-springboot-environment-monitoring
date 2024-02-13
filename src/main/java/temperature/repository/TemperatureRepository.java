package temperature.repository;

import temperature.model.*;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TemperatureRepository extends JpaRepository<Temperature, Long> {
    List<Temperature> findByTimestampBetween(long startTimestamp, long endTimestamp);

    Optional<Temperature> findFirstByOrderByTimestampDesc();
}
