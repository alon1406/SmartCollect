package ambient_invisible_intelligence.rest;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;

import ambient_invisible_intelligence.boundaries.CommandBoundary;
import ambient_invisible_intelligence.boundaries.UserBoundary;

public interface AdminApi {

	@DeleteExchange(url = "/users")
	void deleteAllUsers(
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword);

	@DeleteExchange(url = "/objects")
	void deleteAllObjects(
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword);

	@DeleteExchange(url = "/commands")
	void deleteAllCommands(
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword);

	@GetExchange(url = "/users", accept = {MediaType.APPLICATION_JSON_VALUE})
	List<UserBoundary> exportAllUsers(
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestParam("size") int size,
			@RequestParam("page") int page);

	@GetExchange(url = "/users", accept = {MediaType.APPLICATION_JSON_VALUE})
	List<UserBoundary> exportAllUsersDeprecated(
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword);

	@GetExchange(url = "/commands", accept = {MediaType.APPLICATION_JSON_VALUE})
	List<CommandBoundary> exportAllCommands(
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestParam("size") int size,
			@RequestParam("page") int page);

	@GetExchange(url = "/commands", accept = {MediaType.APPLICATION_JSON_VALUE})
	List<CommandBoundary> exportAllCommandsDeprecated(
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword);
}
