package ambient_invisible_intelligence.rest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.boundaries.UserWithPasswordBoundary;

public interface UserApi {

	@PostExchange(
			contentType = MediaType.APPLICATION_JSON_VALUE,
			accept = {MediaType.APPLICATION_JSON_VALUE})
	UserBoundary createUser(@RequestBody UserWithPasswordBoundary user);

	@GetExchange(
			url = "/login/{systemID}/{userEmail}",
			accept = {MediaType.APPLICATION_JSON_VALUE})
	UserBoundary login(
			@PathVariable("systemID") String systemID,
			@PathVariable("userEmail") String userEmail,
			@RequestParam("password") String password);

	@PutExchange(url = "/{systemID}/{userEmail}")
	void updateUser(
			@PathVariable("systemID") String systemID,
			@PathVariable("userEmail") String userEmail,
			@RequestParam("password") String password,
			@RequestBody UserWithPasswordBoundary update);
}
