package ambient_invisible_intelligence.logic;

import ambient_invisible_intelligence.data.UserEntity;

public interface AuthService {
	public UserEntity authenticate(String systemID, String email, String password);

	public UserEntity requireAdmin(String systemID, String email, String password);

	public UserEntity requireNonAdmin(String systemID, String email, String password);

	public UserEntity requireOperator(String systemID, String email, String password);

	public UserEntity requireEndUser(String systemID, String email, String password);
}
