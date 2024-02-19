package temperature.model;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Column;

import java.util.UUID;

@Entity
public class Temperature {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "VARCHAR(36)")
    private String id; // Use String type for UUID

    private double temperatureC;
    private double humidityPercent;

    private double dewPoint;

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

    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    // Method to calculate and set the dew point
    public void calculateAndSetDewPoint() {
        // Constants for dew point calculation
        final double A = 17.27;
        final double B = 237.7;

        double alpha = ((A * temperatureC) / (B + temperatureC)) + Math.log(humidityPercent / 100.0);
        double dewPoint = (B * alpha) / (A - alpha);

        // Round dew point to the nearest decimal place
        this.dewPoint = Math.round(dewPoint * 10.0) / 10.0;
    }

    // Getter and setter for dew point
    public double getDewPoint() {
        return dewPoint;
    }

    public void setDewPoint(double dewPoint) {
        this.dewPoint = dewPoint;
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
