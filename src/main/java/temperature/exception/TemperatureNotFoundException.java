package temperature.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TemperatureNotFoundException extends RuntimeException {

    public TemperatureNotFoundException(Long id) {
        super("Could not find temperature reading " + id);
    }

    public TemperatureNotFoundException(String e) {
        super(e);
    }
}
