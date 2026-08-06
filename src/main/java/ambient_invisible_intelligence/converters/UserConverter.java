package ambient_invisible_intelligence.converters;

import org.springframework.stereotype.Component;

import ambient_invisible_intelligence.boundaries.UserIdBoundary;
import ambient_invisible_intelligence.errors.BadRequestException;
import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.data.UserEntity;
import ambient_invisible_intelligence.data.UserRole;

@Component
public class UserConverter {

	public UserBoundary toBoundary(UserEntity entity) {
		UserBoundary boundary = new UserBoundary();

		boundary.setId(entity.getId());

		UserIdBoundary userId = new UserIdBoundary();
		userId.setEmail(entity.getEmail());
		userId.setSystemID(entity.getSystemID());
		boundary.setUserId(userId);

		if (entity.getRole() != null) {
			boundary.setRole(entity.getRole().name());
		}

		boundary.setUsername(entity.getUsername());
		boundary.setAvatar(entity.getAvatar());

		return boundary;
	}

	public UserEntity toEntity(UserBoundary boundary) {
		UserEntity entity = new UserEntity();

		entity.setId(boundary.getId());

		if (boundary.getUserId() != null) {
			entity.setEmail(boundary.getUserId().getEmail());
			entity.setSystemID(boundary.getUserId().getSystemID());
		}

		if (boundary.getRole() != null) {
			try {
				entity.setRole(UserRole.valueOf(boundary.getRole()));
			} catch (IllegalArgumentException e) {
				throw new BadRequestException("Invalid role: " + boundary.getRole());
			}
		}

		entity.setUsername(boundary.getUsername());
		entity.setAvatar(boundary.getAvatar());

		return entity;
	}
}
