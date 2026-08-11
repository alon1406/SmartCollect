package ambient_invisible_intelligence.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ambient_invisible_intelligence.boundaries.UserIdBoundary;

import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.boundaries.UserWithPasswordBoundary;
import ambient_invisible_intelligence.errors.UnauthorizedException;
import ambient_invisible_intelligence.logic.UsersService;

@RestController
@RequestMapping(path = {"/ambient-invisible-intelligence/users"}, version = "1.4+")
public class UserController {

	private UsersService userService;
	private boolean allowAdminRegistration;

	public UserController(
			UsersService userService,
			@Value("${smartcollect.users.allow-admin-registration:true}") boolean allowAdminRegistration) {
		this.userService = userService;
		this.allowAdminRegistration = allowAdminRegistration;
	}

	@PostMapping(
			consumes = {MediaType.APPLICATION_JSON_VALUE},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public UserBoundary createUser(
			@RequestBody UserWithPasswordBoundary user) {

		// Build a UserBoundary from the incoming UserWithPasswordBoundary
		UserBoundary boundary = new UserBoundary();
		UserIdBoundary userId = new UserIdBoundary();
		userId.setEmail(user.getEmail());
		boundary.setUserId(userId);
		boundary.setRole(downgradeAdminRole(user.getRole()));
		boundary.setUsername(user.getUsername());
		boundary.setAvatar(user.getAvatar());

		return this.userService.createUser(boundary, user.getPassword());
	}

	/**
	 * Registration is unauthenticated, so on a public deployment the requested
	 * role cannot be trusted: without this, anyone could self-register as ADMIN
	 * and reach the /admin endpoints.
	 *
	 * It is a toggle rather than an unconditional rule because the API is also
	 * the only way to create an administrator - the integration tests bootstrap
	 * their ADMIN through POST /users, and closing that off unconditionally
	 * leaves them unable to clean up between tests. Left open by default,
	 * switched off in application-prod.properties.
	 *
	 * The demo seeder is unaffected either way: it calls UsersService directly
	 * and never passes through this controller.
	 */
	private String downgradeAdminRole(String requestedRole) {
		if (this.allowAdminRegistration) {
			return requestedRole;
		}
		return "ADMIN".equals(requestedRole) ? "END_USER" : requestedRole;
	}

	@GetMapping(
			path = {"/login/{systemID}/{userEmail}"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public UserBoundary login(
			@PathVariable("systemID") String systemID,
			@PathVariable("userEmail") String userEmail,
			@RequestParam(name = "password", required = true) String password) {

		return this.userService.loginUser(systemID, userEmail, password)
				.orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
	}

	@PutMapping(
			path = {"/{systemID}/{userEmail}"},
			consumes = {MediaType.APPLICATION_JSON_VALUE})
	public void updateUser(
			@PathVariable("systemID") String systemID,
			@PathVariable("userEmail") String userEmail,
			@RequestParam(name = "password", required = true) String password,
			@RequestBody UserWithPasswordBoundary update) {

		// Build a UserBoundary from the incoming UserWithPasswordBoundary
		UserBoundary boundary = new UserBoundary();
		boundary.setRole(update.getRole());
		boundary.setUsername(update.getUsername());
		boundary.setAvatar(update.getAvatar());

		this.userService.updateUser(systemID, userEmail, password, boundary, update.getPassword());
	}
}
