package temperature.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity
public class Device {
    @Id
    @GeneratedValue
    private Long id;

    private String deviceName;
    private String location;

    // Constructors
    public Device() {
    }

    public Device(String deviceName, String location) {
        this.deviceName = deviceName;
        this.location = location;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
