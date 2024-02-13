package temperature.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

@Entity
public class Temperature {
    @Id
    @GeneratedValue
    private Long id;

    private double temperatureC;
    private double humidityPercent;
    private long timestamp;

    @ManyToOne
    private Device device;

    // Constructors
    public Temperature() {
    }

    public Temperature(double temperatureC, double humidityPercent, long timestamp, Device device) {
        this.temperatureC = temperatureC;
        this.humidityPercent = humidityPercent;
        this.timestamp = timestamp;
        this.device = device;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public double getTemperatureC() {
        return temperatureC;
    }

    public void setTemperatureC(double temperatureC) {
        this.temperatureC = temperatureC;
    }

    public double getHumidityPercent() {
        return humidityPercent;
    }

    public void setHumidityPercent(double humidityPercent) {
        this.humidityPercent = humidityPercent;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }
}
