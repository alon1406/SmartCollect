package ambient_invisible_intelligence.logic;

import java.util.List;
import java.util.Optional;

import ambient_invisible_intelligence.boundaries.UserBoundary;

public interface UsersService {
	public UserBoundary createUser(UserBoundary user, String newPassword);

	public Optional<UserBoundary> loginUser(String systemID, String userEmail, String password);

	public void updateUser(String systemID, String userEmail, String existingPassword, UserBoundary update, String updatedPassword);

	@Deprecated
	public List<UserBoundary> getAllUsers(String userSystemID, String userEmail, String userPassword);

	public List<UserBoundary> getAllUsers(String userSystemID, String userEmail,
            String userPassword, int size, int page);

	public void deleteAllUsers(String userSystemID, String userEmail, String userPassword);
}
