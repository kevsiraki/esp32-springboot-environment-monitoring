package temperature.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
class TemperatureNotFoundAdvice {

	@ResponseBody
	@ExceptionHandler(TemperatureNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	String temperatureNotFoundHandler(TemperatureNotFoundException ex) {
		return ex.getMessage();
	}
}
