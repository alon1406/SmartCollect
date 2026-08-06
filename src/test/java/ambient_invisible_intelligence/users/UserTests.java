package ambient_invisible_intelligence.users;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.boundaries.UserWithPasswordBoundary;
import ambient_invisible_intelligence.support.BaseIntegrationTest;
import ambient_invisible_intelligence.support.TestConstants;

/**
 * Sprint 4 coverage for the Users API: field validation, password rules,
 * login, and the update restrictions on the immutable systemID/email key.
 */
class UserTests extends BaseIntegrationTest {

	@Test
	void contextLoads() {
	}

	@Test
	void testCreateUserSucceedsAndAssignsSystemID() {
		UserBoundary created = createUser("alice@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "Alice", "alice.png");

		assertThat(created.getUserId().getSystemID()).isNotBlank();
		assertThat(created.getUserId().getEmail()).isEqualTo("alice@example.com");
		assertThat(created.getRole()).isEqualTo("END_USER");
		assertThat(created.getUsername()).isEqualTo("Alice");
		assertThat(created.getAvatar()).isEqualTo("alice.png");
	}

	@Test
	void testCreateUserWithMissingUsernameFails() {
		UserWithPasswordBoundary invalid = new UserWithPasswordBoundary(
				"noname@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", null, "avatar.png");
		assertStatus(400, () -> this.userApi.createUser(invalid));
	}

	@Test
	void testCreateUserWithBlankUsernameFails() {
		UserWithPasswordBoundary invalid = new UserWithPasswordBoundary(
				"blankname@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "   ", "avatar.png");
		assertStatus(400, () -> this.userApi.createUser(invalid));
	}

	@Test
	void testCreateUserWithMissingAvatarFails() {
		UserWithPasswordBoundary invalid = new UserWithPasswordBoundary(
				"noavatar@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "NoAvatar", null);
		assertStatus(400, () -> this.userApi.createUser(invalid));
	}

	@Test
	void testCreateUserWithBlankAvatarFails() {
		UserWithPasswordBoundary invalid = new UserWithPasswordBoundary(
				"blankavatar@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "BlankAvatar", "   ");
		assertStatus(400, () -> this.userApi.createUser(invalid));
	}

	@Test
	void testCreateUserWithMissingRoleFails() {
		UserWithPasswordBoundary invalid = new UserWithPasswordBoundary(
				"norole@example.com", TestConstants.DEFAULT_PASSWORD, null, "NoRole", "avatar.png");
		assertStatus(400, () -> this.userApi.createUser(invalid));
	}

	@Test
	void testCreateUserWithInvalidRoleFails() {
		UserWithPasswordBoundary invalid = new UserWithPasswordBoundary(
				"badrole@example.com", TestConstants.DEFAULT_PASSWORD, "SUPERADMIN", "BadRole", "avatar.png");
		assertStatus(400, () -> this.userApi.createUser(invalid));
	}

	@Test
	void testCreateUserWithInvalidEmailFormatFails() {
		UserWithPasswordBoundary invalid = new UserWithPasswordBoundary(
				"not-an-email", TestConstants.DEFAULT_PASSWORD, "END_USER", "BadEmail", "avatar.png");
		assertStatus(400, () -> this.userApi.createUser(invalid));
	}

	@Test
	void testCreateUserWithNullEmailFails() {
		UserWithPasswordBoundary invalid = new UserWithPasswordBoundary(
				null, TestConstants.DEFAULT_PASSWORD, "END_USER", "NullEmail", "avatar.png");
		assertStatus(400, () -> this.userApi.createUser(invalid));
	}

	@Test
	void testCreateUserWithBlankEmailFails() {
		UserWithPasswordBoundary invalid = new UserWithPasswordBoundary(
				"   ", TestConstants.DEFAULT_PASSWORD, "END_USER", "BlankEmail", "avatar.png");
		assertStatus(400, () -> this.userApi.createUser(invalid));
	}

	@Test
	void testCreateUserWithShortPasswordFails() {
		UserWithPasswordBoundary invalid = new UserWithPasswordBoundary(
				"shortpwd@example.com", "a1!", "END_USER", "ShortPwd", "avatar.png");
		assertStatus(400, () -> this.userApi.createUser(invalid));
	}

	@Test
	void testCreateUserWithPasswordMissingDigitFails() {
		UserWithPasswordBoundary invalid = new UserWithPasswordBoundary(
				"nodigit@example.com", "Password!", "END_USER", "NoDigit", "avatar.png");
		assertStatus(400, () -> this.userApi.createUser(invalid));
	}

	@Test
	void testCreateUserWithPasswordMissingSpecialCharFails() {
		UserWithPasswordBoundary invalid = new UserWithPasswordBoundary(
				"nospecial@example.com", "Password1", "END_USER", "NoSpecial", "avatar.png");
		assertStatus(400, () -> this.userApi.createUser(invalid));
	}

	@Test
	void testCreateDuplicateUserFails() {
		createUser("dup@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "Dup", "avatar.png");

		UserWithPasswordBoundary duplicate = new UserWithPasswordBoundary(
				"dup@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "Dup2", "avatar2.png");
		assertStatus(400, () -> this.userApi.createUser(duplicate));
	}

	@Test
	void testLoginWithCorrectCredentialsSucceeds() {
		UserBoundary created = createUser("login@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "Login", "avatar.png");
		String systemID = created.getUserId().getSystemID();

		UserBoundary loggedIn = this.userApi.login(systemID, "login@example.com", TestConstants.DEFAULT_PASSWORD);

		assertThat(loggedIn.getUserId().getEmail()).isEqualTo("login@example.com");
		assertThat(loggedIn.getUsername()).isEqualTo("Login");
	}

	@Test
	void testLoginWithWrongPasswordFails() {
		UserBoundary created = createUser("wrongpwd@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "WrongPwd", "avatar.png");
		String systemID = created.getUserId().getSystemID();

		assertStatus(401, () -> this.userApi.login(systemID, "wrongpwd@example.com", "SomeOther1!"));
	}

	@Test
	void testLoginWithNonExistingUserFails() {
		assertStatus(401, () -> this.userApi.login(TestConstants.SYSTEM_ID, "ghost@example.com", TestConstants.DEFAULT_PASSWORD));
	}

	@Test
	void testUpdateUsernameAndAvatarSucceeds() {
		UserBoundary created = createUser("update@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "OldName", "old.png");
		String systemID = created.getUserId().getSystemID();

		UserWithPasswordBoundary update = new UserWithPasswordBoundary();
		update.setUsername("NewName");
		update.setAvatar("new.png");
		this.userApi.updateUser(systemID, "update@example.com", TestConstants.DEFAULT_PASSWORD, update);

		UserBoundary reloaded = this.userApi.login(systemID, "update@example.com", TestConstants.DEFAULT_PASSWORD);
		assertThat(reloaded.getUsername()).isEqualTo("NewName");
		assertThat(reloaded.getAvatar()).isEqualTo("new.png");
	}

	@Test
	void testUpdateWithWrongPasswordFails() {
		UserBoundary created = createUser("updatewrong@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "Name", "avatar.png");
		String systemID = created.getUserId().getSystemID();

		UserWithPasswordBoundary update = new UserWithPasswordBoundary();
		update.setUsername("ShouldNotApply");
		assertStatus(401, () -> this.userApi.updateUser(systemID, "updatewrong@example.com", "IncorrectPass1!", update));
	}

	@Test
	void testUpdateCannotChangeEmailOrSystemID() {
		UserBoundary created = createUser("immutable@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "Name", "avatar.png");
		String systemID = created.getUserId().getSystemID();

		UserWithPasswordBoundary update = new UserWithPasswordBoundary();
		update.setEmail("hijacked@example.com");
		update.setUsername("StillMe");
		this.userApi.updateUser(systemID, "immutable@example.com", TestConstants.DEFAULT_PASSWORD, update);

		UserBoundary reloaded = this.userApi.login(systemID, "immutable@example.com", TestConstants.DEFAULT_PASSWORD);
		assertThat(reloaded.getUserId().getEmail()).isEqualTo("immutable@example.com");
		assertThat(reloaded.getUsername()).isEqualTo("StillMe");

		assertStatus(401, () -> this.userApi.login(systemID, "hijacked@example.com", TestConstants.DEFAULT_PASSWORD));
	}

	@Test
	void testUpdateWithBlankUsernameFails() {
		UserBoundary created = createUser("blankupdate@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "Name", "avatar.png");
		String systemID = created.getUserId().getSystemID();

		UserWithPasswordBoundary update = new UserWithPasswordBoundary();
		update.setUsername("   ");
		assertStatus(400, () -> this.userApi.updateUser(systemID, "blankupdate@example.com", TestConstants.DEFAULT_PASSWORD, update));
	}

	@Test
	void testUpdateWithInvalidRoleFails() {
		UserBoundary created = createUser("badroleupdate@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "Name", "avatar.png");
		String systemID = created.getUserId().getSystemID();

		UserWithPasswordBoundary update = new UserWithPasswordBoundary();
		update.setRole("NOT_A_ROLE");
		assertStatus(400, () -> this.userApi.updateUser(systemID, "badroleupdate@example.com", TestConstants.DEFAULT_PASSWORD, update));
	}

	@Test
	void testUpdateRoleFromEndUserToOperatorTakesEffect() {
		UserBoundary created = createUser("promote@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "Name", "avatar.png");
		String systemID = created.getUserId().getSystemID();

		UserWithPasswordBoundary update = new UserWithPasswordBoundary();
		update.setRole("OPERATOR");
		this.userApi.updateUser(systemID, "promote@example.com", TestConstants.DEFAULT_PASSWORD, update);

		UserBoundary promoted = this.userApi.login(systemID, "promote@example.com", TestConstants.DEFAULT_PASSWORD);
		assertThat(promoted.getRole()).isEqualTo("OPERATOR");

		// Now promoted user can exercise OPERATOR-only behavior: creating an object.
		assertThat(createObjectAs(promoted, "Bin", "PromotedBin", "ACTIVE", true).getAlias())
				.isEqualTo("PromotedBin");
	}

	@Test
	void testUpdatePasswordChangesLoginCredential() {
		UserBoundary created = createUser("changepwd@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "Name", "avatar.png");
		String systemID = created.getUserId().getSystemID();

		UserWithPasswordBoundary update = new UserWithPasswordBoundary();
		update.setPassword("NewPass1!");
		this.userApi.updateUser(systemID, "changepwd@example.com", TestConstants.DEFAULT_PASSWORD, update);

		assertStatus(401, () -> this.userApi.login(systemID, "changepwd@example.com", TestConstants.DEFAULT_PASSWORD));

		UserBoundary reloaded = this.userApi.login(systemID, "changepwd@example.com", "NewPass1!");
		assertThat(reloaded.getUserId().getEmail()).isEqualTo("changepwd@example.com");
	}

	@Test
	void testUpdateWithShortPasswordFails() {
		UserBoundary created = createUser("updateshortpwd@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "Name", "avatar.png");
		String systemID = created.getUserId().getSystemID();

		UserWithPasswordBoundary update = new UserWithPasswordBoundary();
		update.setPassword("a1!");
		assertStatus(400, () -> this.userApi.updateUser(systemID, "updateshortpwd@example.com", TestConstants.DEFAULT_PASSWORD, update));
	}

	@Test
	void testUpdateWithPasswordMissingDigitFails() {
		UserBoundary created = createUser("updatenodigit@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "Name", "avatar.png");
		String systemID = created.getUserId().getSystemID();

		UserWithPasswordBoundary update = new UserWithPasswordBoundary();
		update.setPassword("Password!");
		assertStatus(400, () -> this.userApi.updateUser(systemID, "updatenodigit@example.com", TestConstants.DEFAULT_PASSWORD, update));
	}

	@Test
	void testUpdateWithPasswordMissingSpecialCharFails() {
		UserBoundary created = createUser("updatenospecial@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "Name", "avatar.png");
		String systemID = created.getUserId().getSystemID();

		UserWithPasswordBoundary update = new UserWithPasswordBoundary();
		update.setPassword("Password1");
		assertStatus(400, () -> this.userApi.updateUser(systemID, "updatenospecial@example.com", TestConstants.DEFAULT_PASSWORD, update));
	}

	@Test
	void testCreateUserSystemIdEqualsConfiguredApplicationName() {
		UserBoundary created = createUser("sysidmatch@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "SysId", "avatar.png");

		assertThat(created.getUserId().getSystemID()).isEqualTo(TestConstants.SYSTEM_ID);
	}

	@Test
	void testCreateUserWithNullPasswordFails() {
		UserWithPasswordBoundary invalid = new UserWithPasswordBoundary(
				"nullpwd@example.com", null, "END_USER", "NullPwd", "avatar.png");
		assertStatus(400, () -> this.userApi.createUser(invalid));
	}

	@Test
	void testCreateUserWithExactlyFiveCharPasswordSucceeds() {
		UserBoundary created = createUser("fivechar@example.com", "aB1!c", "END_USER", "FiveChar", "avatar.png");

		assertThat(created.getUserId().getEmail()).isEqualTo("fivechar@example.com");
	}

	@Test
	void testUpdateWithBlankAvatarFails() {
		UserBoundary created = createUser("blankavatarupdate@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "Name", "avatar.png");
		String systemID = created.getUserId().getSystemID();

		UserWithPasswordBoundary update = new UserWithPasswordBoundary();
		update.setAvatar("   ");
		assertStatus(400, () -> this.userApi.updateUser(systemID, "blankavatarupdate@example.com", TestConstants.DEFAULT_PASSWORD, update));
	}

	@Test
	void testUpdatePartialOnlyUsernameLeavesRoleAndAvatarUnchanged() {
		UserBoundary created = createUser("partialupdate@example.com", TestConstants.DEFAULT_PASSWORD, "OPERATOR", "OldName", "old.png");
		String systemID = created.getUserId().getSystemID();

		UserWithPasswordBoundary update = new UserWithPasswordBoundary();
		update.setUsername("NewName");
		this.userApi.updateUser(systemID, "partialupdate@example.com", TestConstants.DEFAULT_PASSWORD, update);

		UserBoundary reloaded = this.userApi.login(systemID, "partialupdate@example.com", TestConstants.DEFAULT_PASSWORD);
		assertThat(reloaded.getUsername()).isEqualTo("NewName");
		assertThat(reloaded.getRole()).isEqualTo("OPERATOR");
		assertThat(reloaded.getAvatar()).isEqualTo("old.png");
	}

	@Test
	void testUpdateWithWrongSystemIdInPathFails() {
		createUser("wrongsysid@example.com", TestConstants.DEFAULT_PASSWORD, "END_USER", "Name", "avatar.png");

		UserWithPasswordBoundary update = new UserWithPasswordBoundary();
		update.setUsername("ShouldNotApply");
		assertStatus(401, () -> this.userApi.updateUser("some-other-systemid", "wrongsysid@example.com", TestConstants.DEFAULT_PASSWORD, update));
	}
}
