package ambient_invisible_intelligence.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.ObjectChildIdBoundary;
import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.support.BaseIntegrationTest;
import ambient_invisible_intelligence.support.TestConstants;

/**
 * Sprint 4 coverage for the Admin API: ADMIN-only export/delete of users,
 * objects and commands, the 410 Gone deprecation of the un-paginated user
 * export, pagination, and a cross-cutting check that OPERATOR/END_USER are
 * locked out of every admin sub-endpoint.
 */
class AdminAndRoleMatrixTests extends BaseIntegrationTest {

	@Test
	void contextLoads() {
	}

	@Test
	void testExportAllUsersAsAdminSucceeds() {
		createEndUser("user1@example.com");
		createOperator("operator1@example.com");

		List<UserBoundary> users = this.adminApi.exportAllUsers(
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, 20, 0);

		// admin itself + the two created users
		assertThat(users).hasSize(3);
	}

	@Test
	void testExportAllUsersAsOperatorForbidden() {
		UserBoundary operator = createOperator("operator2@example.com");

		assertStatus(403, () -> this.adminApi.exportAllUsers(
				operator.getUserId().getSystemID(), "operator2@example.com", TestConstants.DEFAULT_PASSWORD, 20, 0));
	}

	@Test
	void testExportAllUsersAsEndUserForbidden() {
		UserBoundary endUser = createEndUser("enduser3@example.com");

		assertStatus(403, () -> this.adminApi.exportAllUsers(
				endUser.getUserId().getSystemID(), "enduser3@example.com", TestConstants.DEFAULT_PASSWORD, 20, 0));
	}

	@Test
	void testExportAllUsersDeprecatedReturns410() {
		assertStatus(410, () -> this.adminApi.exportAllUsersDeprecated(
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD));
	}

	@Test
	void testExportAllUsersWithWrongPasswordFails() {
		assertStatus(401, () -> this.adminApi.exportAllUsers(
				this.adminSystemID, TestConstants.ADMIN_EMAIL, "WrongPass1!", 20, 0));
	}

	@Test
	void testDeleteAllUsersAsAdminSucceeds() {
		createEndUser("user4@example.com");

		this.adminApi.deleteAllUsers(this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD);

		// The admin itself was wiped along with everyone else; tearDown's best-effort
		// cleanup tolerates this, and the next test's setUp recreates the admin fresh.
		assertStatus(401, () -> this.userApi.login(this.adminSystemID, "user4@example.com", TestConstants.DEFAULT_PASSWORD));
		assertStatus(401, () -> this.userApi.login(this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD));
	}

	@Test
	void testDeleteAllUsersAsOperatorForbidden() {
		UserBoundary operator = createOperator("operator5@example.com");

		assertStatus(403, () -> this.adminApi.deleteAllUsers(
				operator.getUserId().getSystemID(), "operator5@example.com", TestConstants.DEFAULT_PASSWORD));
	}

	@Test
	void testDeleteAllObjectsAsAdminSucceeds() {
		UserBoundary operator = createOperator("operator6@example.com");
		createObjectAs(operator, "Bin", "Bin6", "ACTIVE", true);

		this.adminApi.deleteAllObjects(this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD);

		List<ObjectBoundary> remaining = this.objectApi.getAllObjects(
				operator.getUserId().getSystemID(), "operator6@example.com", TestConstants.DEFAULT_PASSWORD, 20, 0);
		assertThat(remaining).isEmpty();
	}

	@Test
	void testDeleteAllObjectsAsEndUserForbidden() {
		UserBoundary endUser = createEndUser("enduser7@example.com");

		assertStatus(403, () -> this.adminApi.deleteAllObjects(
				endUser.getUserId().getSystemID(), "enduser7@example.com", TestConstants.DEFAULT_PASSWORD));
	}

	@Test
	void testDeleteAllObjectsSucceedsWhenParentChildRelationsExist() {
		// Regression check: object_relations join-table rows must be cleared
		// before the objects themselves, otherwise this would fail with a
		// foreign-key violation instead of succeeding.
		UserBoundary operator = createOperator("operator8@example.com");
		ObjectBoundary parent = createObjectAs(operator, "Route", "Route8", "ACTIVE", true);
		ObjectBoundary child = createObjectAs(operator, "Bin", "Bin8", "ACTIVE", true);
		ObjectChildIdBoundary ref = new ObjectChildIdBoundary();
		ref.setChildId(child.getObjectId());
		this.objectApi.bindObjects(parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), "operator8@example.com", TestConstants.DEFAULT_PASSWORD, ref);

		this.adminApi.deleteAllObjects(this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD);

		List<ObjectBoundary> remaining = this.objectApi.getAllObjects(
				operator.getUserId().getSystemID(), "operator8@example.com", TestConstants.DEFAULT_PASSWORD, 20, 0);
		assertThat(remaining).isEmpty();
	}

	@Test
	void testExportAllUsersPaginationAcrossPages() {
		Set<String> expectedEmails = IntStream.range(0, 5)
				.mapToObj(i -> createEndUser("paged" + i + "@example.com"))
				.map(u -> u.getUserId().getEmail())
				.collect(Collectors.toSet());
		expectedEmails.add(TestConstants.ADMIN_EMAIL);

		List<String> collected = new ArrayList<>();
		int page = 0;
		int size = 2;
		List<UserBoundary> pageResult;
		do {
			pageResult = this.adminApi.exportAllUsers(
					this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, size, page);
			assertThat(pageResult.size()).isLessThanOrEqualTo(size);
			pageResult.forEach(u -> collected.add(u.getUserId().getEmail()));
			page++;
		} while (!pageResult.isEmpty());

		assertThat(collected).hasSize(6);
		assertThat(new HashSet<>(collected)).isEqualTo(expectedEmails);
	}

	@Test
	void testOperatorIsLockedOutOfEveryAdminEndpoint() {
		UserBoundary operator = createOperator("operator9@example.com");
		String systemID = operator.getUserId().getSystemID();
		String email = "operator9@example.com";

		assertStatus(403, () -> this.adminApi.deleteAllUsers(systemID, email, TestConstants.DEFAULT_PASSWORD));
		assertStatus(403, () -> this.adminApi.deleteAllObjects(systemID, email, TestConstants.DEFAULT_PASSWORD));
		assertStatus(403, () -> this.adminApi.deleteAllCommands(systemID, email, TestConstants.DEFAULT_PASSWORD));
		assertStatus(403, () -> this.adminApi.exportAllUsers(systemID, email, TestConstants.DEFAULT_PASSWORD, 20, 0));
		assertStatus(403, () -> this.adminApi.exportAllCommands(systemID, email, TestConstants.DEFAULT_PASSWORD, 20, 0));
	}

	@Test
	void testEndUserIsLockedOutOfEveryAdminEndpoint() {
		UserBoundary endUser = createEndUser("enduser10@example.com");
		String systemID = endUser.getUserId().getSystemID();
		String email = "enduser10@example.com";

		assertStatus(403, () -> this.adminApi.deleteAllUsers(systemID, email, TestConstants.DEFAULT_PASSWORD));
		assertStatus(403, () -> this.adminApi.deleteAllObjects(systemID, email, TestConstants.DEFAULT_PASSWORD));
		assertStatus(403, () -> this.adminApi.deleteAllCommands(systemID, email, TestConstants.DEFAULT_PASSWORD));
		assertStatus(403, () -> this.adminApi.exportAllUsers(systemID, email, TestConstants.DEFAULT_PASSWORD, 20, 0));
		assertStatus(403, () -> this.adminApi.exportAllCommands(systemID, email, TestConstants.DEFAULT_PASSWORD, 20, 0));
	}

	@Test
	void testAdminOperationsWithWrongPasswordReturn401() {
		assertStatus(401, () -> this.adminApi.deleteAllUsers(this.adminSystemID, TestConstants.ADMIN_EMAIL, "WrongPass1!"));
		assertStatus(401, () -> this.adminApi.deleteAllObjects(this.adminSystemID, TestConstants.ADMIN_EMAIL, "WrongPass1!"));
		assertStatus(401, () -> this.adminApi.deleteAllCommands(this.adminSystemID, TestConstants.ADMIN_EMAIL, "WrongPass1!"));
		assertStatus(401, () -> this.adminApi.exportAllCommands(this.adminSystemID, TestConstants.ADMIN_EMAIL, "WrongPass1!", 20, 0));
	}

	@Test
	void testAdminOperationWithNonExistentAdminReturns401() {
		assertStatus(401, () -> this.adminApi.deleteAllUsers(
				TestConstants.SYSTEM_ID, "ghost-admin@example.com", TestConstants.ADMIN_PASSWORD));
	}
}
