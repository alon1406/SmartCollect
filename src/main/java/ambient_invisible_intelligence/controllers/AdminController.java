package ambient_invisible_intelligence.controllers;

import org.springframework.http.MediaType;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ambient_invisible_intelligence.boundaries.CommandBoundary;
import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.logic.CommandsService;
import ambient_invisible_intelligence.logic.ObjectsService;
import ambient_invisible_intelligence.logic.UsersService;

@RestController
@RequestMapping(path = {"/ambient-invisible-intelligence/admin"}, version = "1.4+")
public class AdminController {

	private UsersService userService;
	private ObjectsService objectService;
	private CommandsService commandService;

	public AdminController(
			UsersService userService,
			ObjectsService objectService,
			CommandsService commandService) {

		this.userService = userService;
		this.objectService = objectService;
		this.commandService = commandService;
	}

	@DeleteMapping(
			path = {"/users"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public void deleteAllUsers(
	        @RequestParam(name = "userSystemID", required = true)String userSystemID,
	        @RequestParam(name = "userEmail", required = true) String userEmail,
	        @RequestParam(name = "userPassword", required = true)String userPassword) {
		this.userService.deleteAllUsers(userSystemID, userEmail, userPassword);
	}

	@DeleteMapping(path = {"/objects"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public void deleteAllObjects(
	        @RequestParam(name = "userSystemID", required = true)String userSystemID,
	        @RequestParam(name = "userEmail", required = true) String userEmail,
	        @RequestParam(name = "userPassword", required = true)String userPassword) {
		this.objectService.deleteAllObjects(userSystemID, userEmail, userPassword);
	}

	@DeleteMapping(path = {"/commands"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public void deleteAllCommands(
	        @RequestParam(name = "userSystemID", required = true)String userSystemID,
	        @RequestParam(name = "userEmail", required = true) String userEmail,
	        @RequestParam(name = "userPassword", required = true)String userPassword) {
	    this.commandService.deleteAllCommands(userSystemID, userEmail, userPassword);
	}

	@GetMapping(path = {"/users"},
			params = {"size", "page"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public UserBoundary[] exportAllUsers(
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true) String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword,
	        @RequestParam(name = "size") int size,
	        @RequestParam(name = "page") int page) {
		return this.userService.getAllUsers(userSystemID, userEmail, userPassword, size, page).toArray(new UserBoundary[0]);
	}

	@Deprecated
	@GetMapping(path = {"/users"},
			params = {"!size", "!page"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public UserBoundary[] exportAllUsersDeprecated(
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true) String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword) {
	    return this.userService.getAllUsers(userSystemID, userEmail, userPassword).toArray(new UserBoundary[0]);
	}

	@GetMapping(path = {"/commands"},
			params = {"size", "page"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public CommandBoundary[] exportAllCommands(
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true) String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword,
	        @RequestParam(name = "size") int size,
	        @RequestParam(name = "page") int page) {
		return this.commandService.getAllCommandsHistory(userSystemID, userEmail, userPassword, size, page).toArray(new CommandBoundary[0]);
	}

	@Deprecated
	@GetMapping(path = {"/commands"},
			params = {"!size", "!page"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public CommandBoundary[] exportAllCommandsDeprecated(
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true) String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword) {
	    return this.commandService.getAllCommandsHistory(userSystemID, userEmail, userPassword).toArray(new CommandBoundary[0]);
	}
}