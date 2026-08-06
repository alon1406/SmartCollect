package ambient_invisible_intelligence.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClientResponseException;

import ambient_invisible_intelligence.TestContainersConfiguration;
import ambient_invisible_intelligence.boundaries.CommandBoundary;
import ambient_invisible_intelligence.boundaries.LocationBoundary;
import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.TargetObjectIdBoundary;
import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.boundaries.UserIdBoundary;
import ambient_invisible_intelligence.boundaries.UserRefBoundary;
import ambient_invisible_intelligence.boundaries.UserWithPasswordBoundary;
import ambient_invisible_intelligence.rest.AdminApi;
import ambient_invisible_intelligence.rest.CommandApi;
import ambient_invisible_intelligence.rest.ObjectApi;
import ambient_invisible_intelligence.rest.UserApi;

/**
 * Shared plumbing for every Sprint 4 integration test class: boots a fresh ADMIN
 * user before each test, wipes commands/objects/users after each test, and exposes
 * builder/assertion helpers so individual test classes can focus on behavior.
 */
@Import(TestContainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
public abstract class BaseIntegrationTest {

	protected UserApi userApi;
	protected ObjectApi objectApi;
	protected CommandApi commandApi;
	protected AdminApi adminApi;
	protected RecursiveComparisonConfiguration recursiveConfig;

	protected UserBoundary adminUser;
	protected String adminSystemID;

	@Autowired
	public void setApis(UserApi userApi, ObjectApi objectApi, CommandApi commandApi, AdminApi adminApi) {
		this.userApi = userApi;
		this.objectApi = objectApi;
		this.commandApi = commandApi;
		this.adminApi = adminApi;

		this.recursiveConfig = new RecursiveComparisonConfiguration();
		this.recursiveConfig.registerComparatorForType((t1, t2) -> {
			long diff = t1.toInstant().toEpochMilli() - t2.toInstant().toEpochMilli();
			return Long.signum(diff);
		}, ZonedDateTime.class);
	}

	@BeforeEach
	public void baseSetUp() {
		bootstrapAdmin();
	}

	@AfterEach
	public void baseTearDown() {
		if (this.adminSystemID == null) {
			return;
		}
		try {
			this.adminApi.deleteAllCommands(this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD);
		} catch (RestClientResponseException ignored) {
		}
		try {
			this.adminApi.deleteAllObjects(this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD);
		} catch (RestClientResponseException ignored) {
		}
		try {
			this.adminApi.deleteAllUsers(this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD);
		} catch (RestClientResponseException ignored) {
		}
		this.adminUser = null;
		this.adminSystemID = null;
	}

	/**
	 * The DB is expected to be empty at the start of every test (previous test's
	 * tearDown wiped it). If a previous run crashed mid-test and left the admin
	 * behind, recover by logging in with the same well-known credentials instead
	 * of failing the whole suite.
	 */
	private void bootstrapAdmin() {
		UserWithPasswordBoundary request = new UserWithPasswordBoundary(
				TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD,
				TestConstants.ADMIN_ROLE, TestConstants.ADMIN_USERNAME, TestConstants.ADMIN_AVATAR);
		try {
			this.adminUser = this.userApi.createUser(request);
			this.adminSystemID = this.adminUser.getUserId().getSystemID();
		} catch (RestClientResponseException e) {
			this.adminSystemID = TestConstants.SYSTEM_ID;
			this.adminUser = this.userApi.login(this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD);
		}
	}

	protected UserBoundary createUser(String email, String password, String role, String username, String avatar) {
		return this.userApi.createUser(new UserWithPasswordBoundary(email, password, role, username, avatar));
	}

	protected UserBoundary createOperator(String email) {
		return createUser(email, TestConstants.DEFAULT_PASSWORD, TestConstants.OPERATOR_ROLE, "Operator-" + email, "operator.png");
	}

	protected UserBoundary createEndUser(String email) {
		return createUser(email, TestConstants.DEFAULT_PASSWORD, TestConstants.END_USER_ROLE, "EndUser-" + email, "user.png");
	}

	/** A user-shaped reference with no matching row in the DB, for identity-mismatch tests. */
	protected UserBoundary fakeUser(String systemID, String email) {
		UserBoundary user = new UserBoundary();
		UserIdBoundary userId = new UserIdBoundary();
		userId.setSystemID(systemID);
		userId.setEmail(email);
		user.setUserId(userId);
		return user;
	}

	protected ObjectBoundary buildObject(UserBoundary creator, String type, String alias, String status, Boolean active) {
		ObjectBoundary object = new ObjectBoundary();
		object.setType(type);
		object.setAlias(alias);
		object.setStatus(status);
		object.setActive(active);
		UserRefBoundary createdBy = new UserRefBoundary();
		createdBy.setUserId(creator.getUserId());
		object.setCreatedBy(createdBy);
		object.setObjectDetails(new HashMap<>());
		return object;
	}

	protected ObjectBoundary createObjectAs(UserBoundary operator, String type, String alias, String status, boolean active) {
		return this.objectApi.createObject(TestConstants.DEFAULT_PASSWORD, buildObject(operator, type, alias, status, active));
	}

	protected ObjectBoundary createObjectWithLocation(UserBoundary operator, String type, String alias, String status,
			boolean active, double lat, double lng) {
		ObjectBoundary object = buildObject(operator, type, alias, status, active);
		LocationBoundary location = new LocationBoundary();
		location.setLat(lat);
		location.setLng(lng);
		object.setLocation(location);
		return this.objectApi.createObject(TestConstants.DEFAULT_PASSWORD, object);
	}

	protected CommandBoundary buildCommand(UserBoundary invoker, ObjectBoundary target, String commandName, Map<String, Object> attributes) {
		CommandBoundary command = new CommandBoundary();
		command.setCommand(commandName);
		UserRefBoundary invokedBy = new UserRefBoundary();
		invokedBy.setUserId(invoker.getUserId());
		command.setInvokedBy(invokedBy);
		TargetObjectIdBoundary targetRef = new TargetObjectIdBoundary();
		targetRef.setObjectId(target.getObjectId());
		command.setTargetObject(targetRef);
		command.setCommandAttributes(attributes);
		return command;
	}

	/** Asserts that invoking the given call fails with exactly the given HTTP status. */
	protected void assertStatus(int expectedStatus, ThrowingCallable action) {
		assertThatThrownBy(action)
				.isInstanceOfSatisfying(RestClientResponseException.class,
						e -> assertThat(e.getStatusCode().value()).isEqualTo(expectedStatus));
	}
}
