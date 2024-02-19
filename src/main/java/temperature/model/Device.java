package temperature.model;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Column;

import java.util.UUID;

@Entity
public class Device {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "VARCHAR(36)")
    private String id; // Use String type for UUID

    private String deviceName;
    private String location;
    private String apiKey;

    // Constructors
    public Device() {
    }

    public Device(String deviceName, String location) {
        this.deviceName = deviceName;
        this.location = location;
    }

    public Device(String deviceName, String location, String apiKey) {
        this.deviceName = deviceName;
        this.location = location;
        this.apiKey = apiKey;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    // Method to append a unique suffix to the device name
    private String appendUniqueSuffix(String deviceName, String apiKey) {
        // Implement your logic here to append a unique suffix based on the apiKey
        return deviceName + "_" + apiKey.substring(0, 5); // Example logic, customize as needed
    }

    // Add getter for API key
    public String getApiKey() {
        return apiKey;
    }

    // Add setter for API key
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
