package ambient_invisible_intelligence.logic.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.converters.UserConverter;
import ambient_invisible_intelligence.data.UserEntity;
import ambient_invisible_intelligence.data.UserRole;
import ambient_invisible_intelligence.errors.BadRequestException;
import ambient_invisible_intelligence.errors.GoneException;
import ambient_invisible_intelligence.errors.UnauthorizedException;
import ambient_invisible_intelligence.logic.AuthService;
import ambient_invisible_intelligence.logic.UsersService;
import ambient_invisible_intelligence.repositories.UserRepository;

@Service
public class UsersServiceImpl implements UsersService {
	private UserRepository userRepository;
	private UserConverter userConverter;
	private String systemID;
	private AuthService authService;

	public UsersServiceImpl(
			UserRepository userRepository,
			UserConverter userConverter,
			AuthService authService,
			@Value("${spring.application.name}") String systemID) {
		this.userRepository = userRepository;
		this.userConverter = userConverter;
		this.authService = authService;
		this.systemID = systemID;
	}

	@Override
	public UserBoundary createUser(UserBoundary user, String newPassword) {
		if (user == null) {
			throw new BadRequestException("User must not be null");
		}

		validateNotBlank(newPassword, "Password");

		validateNotBlank(user.getRole(), "Role");

		validateNotBlank(user.getUsername(), "Username");
		validateNotBlank(user.getAvatar(), "Avatar");

		// Extract email from userId map
		if (user.getUserId() == null) {
			throw new BadRequestException("userId must not be null");
		}

		String email = user.getUserId().getEmail();
		validateNotBlank(email, "Email");

		validateEmail(email);
		validatePassword(newPassword);

		String internalId = this.systemID + "#" + email;

		if (this.userRepository.existsById(internalId)) {
			throw new BadRequestException("User already exists");
		}

		UserEntity entity = this.userConverter.toEntity(user);
		entity.setId(internalId);
		entity.setSystemID(this.systemID);
		entity.setPassword(newPassword);

		this.userRepository.save(entity);

		return this.userConverter.toBoundary(entity);
	}

	@Override
	public Optional<UserBoundary> loginUser(String systemID, String userEmail, String password) {
		if (systemID == null) {
			throw new BadRequestException("SystemID must not be null");
		}

		if (userEmail == null) {
			throw new BadRequestException("User email must not be null");
		}

		if (password == null) {
			throw new BadRequestException("Password must not be null");
		}

		String internalId = systemID + "#" + userEmail;

		Optional<UserEntity> user = this.userRepository.findById(internalId);

		if (user.isEmpty() || !password.equals(user.get().getPassword())) {
			throw new UnauthorizedException("Invalid credentials");
		}

		return Optional.of(this.userConverter.toBoundary(user.get()));
	}

	@Override
	public void updateUser(String systemID, String userEmail, String existingPassword, UserBoundary update, String updatedPassword) {
		if (systemID == null) {
			throw new BadRequestException("SystemID must not be null");
		}

		if (userEmail == null) {
			throw new BadRequestException("User email must not be null");
		}

		if (existingPassword == null) {
			throw new BadRequestException("Password must not be null");
		}

		if (update == null) {
			throw new BadRequestException("Update must not be null");
		}

		String internalId = systemID + "#" + userEmail;

		Optional<UserEntity> existingUser = this.userRepository.findById(internalId);

		if (existingUser.isEmpty() || !existingPassword.equals(existingUser.get().getPassword())) {
			throw new UnauthorizedException("Invalid credentials");
		}
		UserEntity entity = existingUser.get();

		/*
		 * חסימת שינוי email או systemID:
		 * לא משתמשים ב-update.getUserId()
		 * לא יוצרים userId חדש
		 * לא משנים את id
		 */

		if (update.getRole() != null) {
			try {
				entity.setRole(UserRole.valueOf(update.getRole()));
			} catch (IllegalArgumentException e) {
				throw new BadRequestException("Invalid role: " + update.getRole());
			}
		}

		if (update.getUsername() != null) {
			validateNotBlank(update.getUsername(), "Username");
			entity.setUsername(update.getUsername());
		}

		if (update.getAvatar() != null) {
			validateNotBlank(update.getAvatar(), "Avatar");
			entity.setAvatar(update.getAvatar());
		}

		if (updatedPassword != null) {
			validatePassword(updatedPassword);
			entity.setPassword(updatedPassword);
		}

		this.userRepository.save(entity);
	}

	@Override
	@Deprecated
	public List<UserBoundary> getAllUsers(String userSystemID, String userEmail, String userPassword) {
		throw new GoneException("Version not supported. Please update to 1.4");
	}

	@Override
	public List<UserBoundary> getAllUsers(String userSystemID, String userEmail,
	        String userPassword, int size, int page) {
		this.authService.requireAdmin(userSystemID, userEmail, userPassword);

		return this.userRepository.findAllBy(PageRequest.of(page, size, Sort.by("id")))
				.stream()
				.map(this.userConverter::toBoundary)
				.toList();
	}

	@Override
	public void deleteAllUsers(String userSystemID, String userEmail, String userPassword) {
		this.authService.requireAdmin(userSystemID, userEmail, userPassword);
		this.userRepository.deleteAll();
	}

	private void validateEmail(String email) {
		String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";

		if (!email.matches(emailRegex)) {
			throw new BadRequestException("Invalid email format");
		}
	}

	private void validatePassword(String password) {
		if (password.length() < 5) {
			throw new BadRequestException("Password must be at least 5 characters long");
		}

		if (!password.matches(".*\\d.*")) {
			throw new BadRequestException("Password must contain at least one digit");
		}

		if (!password.matches(".*[^A-Za-z0-9].*")) {
			throw new BadRequestException("Password must contain at least one special character");
		}
	}
	
	
	private void validateNotBlank(String value, String fieldName) {
		if (value == null || value.trim().isEmpty()) {
			throw new BadRequestException(fieldName + " must not be null or empty");
		}
	}
	
	
	
}
