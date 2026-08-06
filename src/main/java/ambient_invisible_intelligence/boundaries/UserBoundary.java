package ambient_invisible_intelligence.boundaries;

import org.springframework.data.annotation.Id;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "userId", "role", "username", "avatar" })
public class UserBoundary {
	@Id
	private String id; // key for RAM Map storage

	private UserIdBoundary userId;
	private String role;
	private String username;
	private String avatar;

	public UserBoundary() {
	}
	
	@JsonIgnore
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public UserIdBoundary getUserId() { return userId; }

	public void setUserId(UserIdBoundary userId) { this.userId = userId; }

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getAvatar() {
		return avatar;
	}

	public void setAvatar(String avatar) {
		this.avatar = avatar;
	}

	@Override
	public String toString() {
		return "{"
				+ "id:" + id
				+ ", userId:" + userId
				+ ", role:" + role
				+ ", username:" + username
				+ ", avatar:" + avatar
				+ "}";
	}
}