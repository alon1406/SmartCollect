package ambient_invisible_intelligence.controllers;

import org.springframework.http.MediaType;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.beans.factory.annotation.Value;

import ambient_invisible_intelligence.boundaries.CommandBoundary;
import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.errors.ForbiddenException;
import ambient_invisible_intelligence.logic.CommandsService;
import ambient_invisible_intelligence.logic.ObjectsService;
import ambient_invisible_intelligence.logic.UsersService;

@RestController
@RequestMapping(path = {"/ambient-invisible-intelligence/admin"}, version = "1.4+")
public class AdminController {

	private UsersService userService;
	private ObjectsService objectService;
	private CommandsService commandService;
	private boolean bulkDeleteEnabled;

	public AdminController(
			UsersService userService,
			ObjectsService objectService,
			CommandsService commandService,
			@Value("${smartcollect.admin.bulk-delete-enabled:true}") boolean bulkDeleteEnabled) {

		this.userService = userService;
		this.objectService = objectService;
		this.commandService = commandService;
		this.bulkDeleteEnabled = bulkDeleteEnabled;
	}

	/**
	 * The bulk-delete routes wipe every user, object or command in one call.
	 * That is fine locally, but on a public demo any account that reaches ADMIN
	 * can empty the whole system - and a user can still promote itself through
	 * PUT /users, which the operator dashboard depends on. Disabling these three
	 * routes in the deployed profile removes the damage without changing the
	 * role model the frontend relies on.
	 *
	 * The demo seeder is unaffected: it calls the service layer directly rather
	 * than going through this controller.
	 */
	private void requireBulkDeleteEnabled() {
		if (!this.bulkDeleteEnabled) {
			throw new ForbiddenException("Bulk delete is disabled in this deployment");
		}
	}

	@DeleteMapping(
			path = {"/users"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public void deleteAllUsers(
	        @RequestParam(name = "userSystemID", required = true)String userSystemID,
	        @RequestParam(name = "userEmail", required = true) String userEmail,
	        @RequestParam(name = "userPassword", required = true)String userPassword) {
		requireBulkDeleteEnabled();
		this.userService.deleteAllUsers(userSystemID, userEmail, userPassword);
	}

	@DeleteMapping(path = {"/objects"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public void deleteAllObjects(
	        @RequestParam(name = "userSystemID", required = true)String userSystemID,
	        @RequestParam(name = "userEmail", required = true) String userEmail,
	        @RequestParam(name = "userPassword", required = true)String userPassword) {
		requireBulkDeleteEnabled();
		this.objectService.deleteAllObjects(userSystemID, userEmail, userPassword);
	}

	@DeleteMapping(path = {"/commands"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public void deleteAllCommands(
	        @RequestParam(name = "userSystemID", required = true)String userSystemID,
	        @RequestParam(name = "userEmail", required = true) String userEmail,
	        @RequestParam(name = "userPassword", required = true)String userPassword) {
		requireBulkDeleteEnabled();
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