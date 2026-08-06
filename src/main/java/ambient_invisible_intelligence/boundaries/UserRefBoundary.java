package ambient_invisible_intelligence.boundaries;

public class UserRefBoundary {
    private UserIdBoundary userId;

    public UserRefBoundary() {}

    public UserIdBoundary getUserId() { return userId; }
    public void setUserId(UserIdBoundary userId) { this.userId = userId; }
}
