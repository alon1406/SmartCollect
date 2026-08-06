package ambient_invisible_intelligence.controllers;

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

	public UserController(UsersService userService) {
		this.userService = userService;
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
		boundary.setRole(user.getRole());
		boundary.setUsername(user.getUsername());
		boundary.setAvatar(user.getAvatar());

		return this.userService.createUser(boundary, user.getPassword());
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
