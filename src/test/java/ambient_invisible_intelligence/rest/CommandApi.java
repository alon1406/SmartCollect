package ambient_invisible_intelligence.rest;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.PostExchange;

import ambient_invisible_intelligence.boundaries.CommandBoundary;

public interface CommandApi {

	@PostExchange(
			contentType = MediaType.APPLICATION_JSON_VALUE,
			accept = {MediaType.APPLICATION_JSON_VALUE})
	List<CommandBoundary> invokeCommand(
			@RequestParam("userPassword") String userPassword,
			@RequestBody CommandBoundary command);
}
