package temperature.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TemperatureNotFoundException extends RuntimeException {

    public TemperatureNotFoundException(String id) {
        super("Could not find temperature reading " + id);
    }
}
