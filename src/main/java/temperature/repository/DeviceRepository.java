package temperature.repository;

import temperature.model.Device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, String> {
    Optional<Device> findByDeviceName(String deviceName);

    Optional<Device> findByDeviceNameAndApiKey(String deviceName, String apiKey);

    Optional<Device> findByApiKey(String apiKey);

    Optional<Device> findByLocation(String location);
}
