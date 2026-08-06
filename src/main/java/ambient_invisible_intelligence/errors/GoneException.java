package ambient_invisible_intelligence.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.GONE)
public class GoneException extends RuntimeException {
	public GoneException(String message) {
		super(message);
	}
}
