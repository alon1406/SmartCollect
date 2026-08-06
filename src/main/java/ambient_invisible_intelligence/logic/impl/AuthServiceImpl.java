package ambient_invisible_intelligence.logic.impl;

import java.util.Optional;

import org.springframework.stereotype.Service;

import ambient_invisible_intelligence.data.UserEntity;
import ambient_invisible_intelligence.data.UserRole;
import ambient_invisible_intelligence.errors.BadRequestException;
import ambient_invisible_intelligence.errors.ForbiddenException;
import ambient_invisible_intelligence.errors.UnauthorizedException;
import ambient_invisible_intelligence.logic.AuthService;
import ambient_invisible_intelligence.repositories.UserRepository;

@Service
public class AuthServiceImpl implements AuthService {
	private UserRepository userRepository;

	public AuthServiceImpl(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserEntity authenticate(String systemID, String email, String password) {
		if (systemID == null || systemID.trim().isEmpty()) {
			throw new BadRequestException("User systemID must not be empty");
		}

		if (email == null || email.trim().isEmpty()) {
			throw new BadRequestException("User email must not be empty");
		}

		if (password == null || password.trim().isEmpty()) {
			throw new BadRequestException("User password must not be empty");
		}

		String internalId = systemID + "#" + email;

		Optional<UserEntity> userOpt = this.userRepository.findById(internalId);
		if (userOpt.isEmpty() || !password.equals(userOpt.get().getPassword())) {
			throw new UnauthorizedException("Invalid credentials");
		}

		return userOpt.get();
	}

	@Override
	public UserEntity requireAdmin(String systemID, String email, String password) {
		UserEntity user = authenticate(systemID, email, password);

		if (user.getRole() != UserRole.ADMIN) {
			throw new ForbiddenException("Access denied");
		}

		return user;
	}

	@Override
	public UserEntity requireNonAdmin(String systemID, String email, String password) {
		UserEntity user = authenticate(systemID, email, password);

		if (user.getRole() == UserRole.ADMIN) {
			throw new ForbiddenException("Access denied");
		}

		return user;
	}

	@Override
	public UserEntity requireOperator(String systemID, String email, String password) {
		UserEntity user = authenticate(systemID, email, password);

		if (user.getRole() != UserRole.OPERATOR) {
			throw new ForbiddenException("Access denied");
		}

		return user;
	}

	@Override
	public UserEntity requireEndUser(String systemID, String email, String password) {
		UserEntity user = authenticate(systemID, email, password);

		if (user.getRole() != UserRole.END_USER) {
			throw new ForbiddenException("Access denied");
		}

		return user;
	}
}
