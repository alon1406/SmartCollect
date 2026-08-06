package ambient_invisible_intelligence.objects;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import ambient_invisible_intelligence.boundaries.LocationBoundary;
import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.ObjectIdBoundary;
import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.boundaries.UserRefBoundary;
import ambient_invisible_intelligence.support.BaseIntegrationTest;
import ambient_invisible_intelligence.support.TestConstants;

/**
 * Sprint 4 coverage for Object create/read/update: field validation, the
 * OPERATOR-only write restriction, immutability of identity/creation fields
 * on update, pagination, and the 410 Gone deprecation of the un-paginated
 * "get all objects" endpoint.
 */
class ObjectCrudTests extends BaseIntegrationTest {

	@Test
	void contextLoads() {
	}

	@Test
	void testCreateObjectAsOperatorSucceeds() {
		UserBoundary operator = createOperator("operator1@example.com");

		ObjectBoundary created = createObjectAs(operator, "Bin", "MainBin", "ACTIVE", true);

		assertThat(created.getObjectId().getSystemID()).isEqualTo(TestConstants.SYSTEM_ID);
		assertThat(created.getObjectId().getObjectId()).isNotBlank();
		assertThat(created.getType()).isEqualTo("Bin");
		assertThat(created.getAlias()).isEqualTo("MainBin");
		assertThat(created.getStatus()).isEqualTo("ACTIVE");
		assertThat(created.getActive()).isTrue();
		assertThat(created.getCreationTimestamp()).isNotNull();
		assertThat(created.getCreatedBy().getUserId().getEmail()).isEqualTo("operator1@example.com");
	}

	@Test
	void testCreateObjectAsEndUserForbidden() {
		UserBoundary endUser = createEndUser("enduser1@example.com");

		assertStatus(403, () -> createObjectAs(endUser, "Bin", "Bin", "ACTIVE", true));
	}

	@Test
	void testCreateObjectAsAdminForbidden() {
		assertStatus(403, () -> this.objectApi.createObject(
				TestConstants.ADMIN_PASSWORD, buildObject(this.adminUser, "Bin", "Bin", "ACTIVE", true)));
	}

	@Test
	void testCreateObjectWithWrongPasswordFails() {
		UserBoundary operator = createOperator("operator2@example.com");
		ObjectBoundary object = buildObject(operator, "Bin", "Bin", "ACTIVE", true);

		assertStatus(401, () -> this.objectApi.createObject("WrongPass1!", object));
	}

	@Test
	void testCreateObjectWithMissingTypeFails() {
		UserBoundary operator = createOperator("operator3@example.com");
		ObjectBoundary object = buildObject(operator, null, "Bin", "ACTIVE", true);

		assertStatus(400, () -> this.objectApi.createObject(TestConstants.DEFAULT_PASSWORD, object));
	}

	@Test
	void testCreateObjectWithBlankAliasFails() {
		UserBoundary operator = createOperator("operator4@example.com");
		ObjectBoundary object = buildObject(operator, "Bin", "   ", "ACTIVE", true);

		assertStatus(400, () -> this.objectApi.createObject(TestConstants.DEFAULT_PASSWORD, object));
	}

	@Test
	void testCreateObjectWithMissingStatusFails() {
		UserBoundary operator = createOperator("operator5@example.com");
		ObjectBoundary object = buildObject(operator, "Bin", "Bin", null, true);

		assertStatus(400, () -> this.objectApi.createObject(TestConstants.DEFAULT_PASSWORD, object));
	}

	@Test
	void testCreateObjectWithMissingActiveFails() {
		UserBoundary operator = createOperator("operator6@example.com");
		ObjectBoundary object = buildObject(operator, "Bin", "Bin", "ACTIVE", null);

		assertStatus(400, () -> this.objectApi.createObject(TestConstants.DEFAULT_PASSWORD, object));
	}

	@Test
	void testCreateObjectWithMissingCreatedByFails() {
		ObjectBoundary object = new ObjectBoundary();
		object.setType("Bin");
		object.setAlias("Bin");
		object.setStatus("ACTIVE");
		object.setActive(true);

		assertStatus(400, () -> this.objectApi.createObject(TestConstants.DEFAULT_PASSWORD, object));
	}

	@Test
	void testCreateObjectWithNonExistentCreatedByUserFails() {
		UserBoundary ghost = fakeUser(TestConstants.SYSTEM_ID, "ghost-creator@nowhere.test");
		ObjectBoundary object = buildObject(ghost, "Bin", "Bin", "ACTIVE", true);

		assertStatus(401, () -> this.objectApi.createObject(TestConstants.DEFAULT_PASSWORD, object));
	}

	@Test
	void testGetSpecificObjectAsOperatorSucceeds() {
		UserBoundary operator = createOperator("operator7@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "Bin", "ACTIVE", true);
		String systemID = created.getObjectId().getSystemID();
		String objectId = created.getObjectId().getObjectId();

		ObjectBoundary fetched = this.objectApi.getObject(systemID, objectId,
				operator.getUserId().getSystemID(), "operator7@example.com", TestConstants.DEFAULT_PASSWORD);

		assertThat(fetched.getAlias()).isEqualTo("Bin");
	}

	@Test
	void testGetSpecificObjectAsEndUserForInactiveObjectReturns404() {
		UserBoundary operator = createOperator("operator8@example.com");
		UserBoundary endUser = createEndUser("enduser8@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "InactiveBin", "MAINTENANCE", false);
		String systemID = created.getObjectId().getSystemID();
		String objectId = created.getObjectId().getObjectId();

		assertStatus(404, () -> this.objectApi.getObject(systemID, objectId,
				endUser.getUserId().getSystemID(), "enduser8@example.com", TestConstants.DEFAULT_PASSWORD));
	}

	@Test
	void testGetSpecificObjectAsAdminForbidden() {
		UserBoundary operator = createOperator("operator9@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "Bin", "ACTIVE", true);
		String systemID = created.getObjectId().getSystemID();
		String objectId = created.getObjectId().getObjectId();

		assertStatus(403, () -> this.objectApi.getObject(systemID, objectId,
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD));
	}

	@Test
	void testGetSpecificObjectWithWrongPasswordFails() {
		UserBoundary operator = createOperator("operator9b@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "Bin", "ACTIVE", true);
		String systemID = created.getObjectId().getSystemID();
		String objectId = created.getObjectId().getObjectId();

		assertStatus(401, () -> this.objectApi.getObject(systemID, objectId,
				operator.getUserId().getSystemID(), "operator9b@example.com", "WrongPass1!"));
	}

	@Test
	void testGetNonExistentObjectReturns404() {
		UserBoundary operator = createOperator("operator10@example.com");

		assertStatus(404, () -> this.objectApi.getObject(TestConstants.SYSTEM_ID, "does-not-exist",
				operator.getUserId().getSystemID(), "operator10@example.com", TestConstants.DEFAULT_PASSWORD));
	}

	@Test
	void testUpdateObjectAsOperatorSucceeds() {
		UserBoundary operator = createOperator("operator11@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "OldAlias", "ACTIVE", true);
		String systemID = created.getObjectId().getSystemID();
		String objectId = created.getObjectId().getObjectId();

		ObjectBoundary update = new ObjectBoundary();
		update.setAlias("NewAlias");
		update.setStatus("MAINTENANCE");
		update.setActive(false);
		LocationBoundary location = new LocationBoundary();
		location.setLat(32.05);
		location.setLng(34.75);
		update.setLocation(location);

		this.objectApi.updateObject(systemID, objectId,
				operator.getUserId().getSystemID(), "operator11@example.com", TestConstants.DEFAULT_PASSWORD, update);

		ObjectBoundary reloaded = this.objectApi.getObject(systemID, objectId,
				operator.getUserId().getSystemID(), "operator11@example.com", TestConstants.DEFAULT_PASSWORD);

		assertThat(reloaded.getAlias()).isEqualTo("NewAlias");
		assertThat(reloaded.getStatus()).isEqualTo("MAINTENANCE");
		assertThat(reloaded.getActive()).isFalse();
		assertThat(reloaded.getLocation().getLat()).isEqualTo(32.05);
		assertThat(reloaded.getLocation().getLng()).isEqualTo(34.75);
	}

	@Test
	void testUpdateObjectCannotChangeCreationTimestampOrIdentity() {
		UserBoundary operator = createOperator("operator12@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "StableAlias", "ACTIVE", true);
		String systemID = created.getObjectId().getSystemID();
		String objectId = created.getObjectId().getObjectId();
		ZonedDateTime originalTimestamp = created.getCreationTimestamp();

		ObjectBoundary update = new ObjectBoundary();
		update.setAlias("ChangedAlias");
		ObjectIdBoundary forgedId = new ObjectIdBoundary();
		forgedId.setSystemID("someone-else");
		forgedId.setObjectId("forged-id");
		update.setObjectId(forgedId);
		update.setCreationTimestamp(ZonedDateTime.now().plusYears(10));

		this.objectApi.updateObject(systemID, objectId,
				operator.getUserId().getSystemID(), "operator12@example.com", TestConstants.DEFAULT_PASSWORD, update);

		ObjectBoundary reloaded = this.objectApi.getObject(systemID, objectId,
				operator.getUserId().getSystemID(), "operator12@example.com", TestConstants.DEFAULT_PASSWORD);

		assertThat(reloaded.getAlias()).isEqualTo("ChangedAlias");
		assertThat(reloaded.getObjectId().getSystemID()).isEqualTo(systemID);
		assertThat(reloaded.getObjectId().getObjectId()).isEqualTo(objectId);
		assertThat(reloaded.getCreationTimestamp().toInstant().toEpochMilli())
				.isEqualTo(originalTimestamp.toInstant().toEpochMilli());
	}

	@Test
	void testUpdateObjectAsEndUserForbidden() {
		UserBoundary operator = createOperator("operator13@example.com");
		UserBoundary endUser = createEndUser("enduser13@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "Bin", "ACTIVE", true);
		String systemID = created.getObjectId().getSystemID();
		String objectId = created.getObjectId().getObjectId();

		ObjectBoundary update = new ObjectBoundary();
		update.setAlias("Hacked");

		assertStatus(403, () -> this.objectApi.updateObject(systemID, objectId,
				endUser.getUserId().getSystemID(), "enduser13@example.com", TestConstants.DEFAULT_PASSWORD, update));
	}

	@Test
	void testUpdateObjectWithWrongPasswordFails() {
		UserBoundary operator = createOperator("operator13b@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "Bin", "ACTIVE", true);
		String systemID = created.getObjectId().getSystemID();
		String objectId = created.getObjectId().getObjectId();

		ObjectBoundary update = new ObjectBoundary();
		update.setAlias("ShouldNotApply");

		assertStatus(401, () -> this.objectApi.updateObject(systemID, objectId,
				operator.getUserId().getSystemID(), "operator13b@example.com", "WrongPass1!", update));
	}

	@Test
	void testUpdateObjectAsAdminForbidden() {
		UserBoundary operator = createOperator("operator13c@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "Bin", "ACTIVE", true);
		String systemID = created.getObjectId().getSystemID();
		String objectId = created.getObjectId().getObjectId();

		ObjectBoundary update = new ObjectBoundary();
		update.setAlias("ShouldNotApply");

		assertStatus(403, () -> this.objectApi.updateObject(systemID, objectId,
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, update));
	}

	@Test
	void testUpdateNonExistentObjectFails() {
		UserBoundary operator = createOperator("operator14@example.com");
		ObjectBoundary update = new ObjectBoundary();
		update.setAlias("DoesNotMatter");

		assertStatus(404, () -> this.objectApi.updateObject(TestConstants.SYSTEM_ID, "does-not-exist",
				operator.getUserId().getSystemID(), "operator14@example.com", TestConstants.DEFAULT_PASSWORD, update));
	}

	@Test
	void testUpdateObjectWithBlankAliasFails() {
		UserBoundary operator = createOperator("operator15@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "Bin", "ACTIVE", true);
		String systemID = created.getObjectId().getSystemID();
		String objectId = created.getObjectId().getObjectId();

		ObjectBoundary update = new ObjectBoundary();
		update.setAlias("   ");

		assertStatus(400, () -> this.objectApi.updateObject(systemID, objectId,
				operator.getUserId().getSystemID(), "operator15@example.com", TestConstants.DEFAULT_PASSWORD, update));
	}

	@Test
	void testGetAllObjectsPaginationReturnsAllCreatedObjectsAcrossPages() {
		UserBoundary operator = createOperator("operator16@example.com");
		List<ObjectBoundary> created = IntStream.range(0, 5)
				.mapToObj(i -> createObjectAs(operator, "Bin", "PagedAlias" + i, "ACTIVE", true))
				.toList();
		Set<String> expectedIds = created.stream()
				.map(o -> o.getObjectId().getObjectId())
				.collect(Collectors.toSet());

		List<String> collected = new ArrayList<>();
		int page = 0;
		int size = 2;
		List<ObjectBoundary> pageResult;
		do {
			pageResult = this.objectApi.getAllObjects(
					operator.getUserId().getSystemID(), "operator16@example.com", TestConstants.DEFAULT_PASSWORD, size, page);
			assertThat(pageResult.size()).isLessThanOrEqualTo(size);
			pageResult.forEach(o -> collected.add(o.getObjectId().getObjectId()));
			page++;
		} while (!pageResult.isEmpty());

		assertThat(collected).hasSize(5);
		assertThat(new HashSet<>(collected)).isEqualTo(expectedIds);
	}

	@Test
	void testGetAllObjectsAsEndUserOnlyReturnsActiveObjects() {
		UserBoundary operator = createOperator("operator17@example.com");
		UserBoundary endUser = createEndUser("enduser17@example.com");
		createObjectAs(operator, "Bin", "ActiveOne", "ACTIVE", true);
		createObjectAs(operator, "Bin", "ActiveTwo", "ACTIVE", true);
		createObjectAs(operator, "Bin", "InactiveOne", "MAINTENANCE", false);

		List<ObjectBoundary> visible = this.objectApi.getAllObjects(
				endUser.getUserId().getSystemID(), "enduser17@example.com", TestConstants.DEFAULT_PASSWORD, 50, 0);

		assertThat(visible).extracting(ObjectBoundary::getAlias)
				.containsExactlyInAnyOrder("ActiveOne", "ActiveTwo");
	}

	@Test
	void testGetAllObjectsDeprecatedReturns410WithValidCredentials() {
		UserBoundary operator = createOperator("operator18@example.com");

		assertStatus(410, () -> this.objectApi.getAllObjectsDeprecated(
				operator.getUserId().getSystemID(), "operator18@example.com", TestConstants.DEFAULT_PASSWORD));
	}

	@Test
	void testGetAllObjectsDeprecatedReturns410EvenWithInvalidCredentials() {
		assertStatus(410, () -> this.objectApi.getAllObjectsDeprecated(
				TestConstants.SYSTEM_ID, "nobody@example.com", "WrongPass1!"));
	}

	@Test
	void testCreateObjectPreservesPrePopulatedObjectDetails() {
		UserBoundary operator = createOperator("operatorDetails@example.com");
		ObjectBoundary object = buildObject(operator, "Bin", "DetailsBin", "ACTIVE", true);
		Map<String, Object> details = new HashMap<>();
		details.put("capacity", "120L");
		object.setObjectDetails(details);
		ObjectBoundary created = this.objectApi.createObject(TestConstants.DEFAULT_PASSWORD, object);

		ObjectBoundary reloaded = this.objectApi.getObject(
				created.getObjectId().getSystemID(), created.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), "operatorDetails@example.com", TestConstants.DEFAULT_PASSWORD);

		assertThat(reloaded.getObjectDetails()).containsEntry("capacity", "120L");
	}

	@Test
	void testCreateObjectPreservesLocation() {
		UserBoundary operator = createOperator("operatorLoc@example.com");
		ObjectBoundary created = createObjectWithLocation(operator, "Bin", "LocBin", "ACTIVE", true, 32.11, 34.81);

		ObjectBoundary reloaded = this.objectApi.getObject(
				created.getObjectId().getSystemID(), created.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), "operatorLoc@example.com", TestConstants.DEFAULT_PASSWORD);

		assertThat(reloaded.getLocation().getLat()).isEqualTo(32.11);
		assertThat(reloaded.getLocation().getLng()).isEqualTo(34.81);
	}

	@Test
	void testCreateSameAliasTwiceGeneratesDifferentObjectIds() {
		UserBoundary operator = createOperator("operatorDup@example.com");
		ObjectBoundary first = createObjectAs(operator, "Bin", "SameAlias", "ACTIVE", true);
		ObjectBoundary second = createObjectAs(operator, "Bin", "SameAlias", "ACTIVE", true);

		assertThat(first.getObjectId().getObjectId()).isNotEqualTo(second.getObjectId().getObjectId());
	}

	@Test
	void testUpdateObjectCannotChangeCreatedBy() {
		UserBoundary operator = createOperator("operatorCreatedBy@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "CreatedByBin", "ACTIVE", true);
		String systemID = created.getObjectId().getSystemID();
		String objectId = created.getObjectId().getObjectId();

		ObjectBoundary update = new ObjectBoundary();
		update.setAlias("Renamed");
		UserRefBoundary forged = new UserRefBoundary();
		forged.setUserId(fakeUser(TestConstants.SYSTEM_ID, "intruder@example.com").getUserId());
		update.setCreatedBy(forged);

		this.objectApi.updateObject(systemID, objectId,
				operator.getUserId().getSystemID(), "operatorCreatedBy@example.com", TestConstants.DEFAULT_PASSWORD, update);

		ObjectBoundary reloaded = this.objectApi.getObject(systemID, objectId,
				operator.getUserId().getSystemID(), "operatorCreatedBy@example.com", TestConstants.DEFAULT_PASSWORD);

		assertThat(reloaded.getAlias()).isEqualTo("Renamed");
		assertThat(reloaded.getCreatedBy().getUserId().getEmail()).isEqualTo("operatorCreatedBy@example.com");
	}

	@Test
	void testUpdateObjectWithBlankTypeFails() {
		UserBoundary operator = createOperator("operatorBlankType@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "BlankTypeBin", "ACTIVE", true);
		String systemID = created.getObjectId().getSystemID();
		String objectId = created.getObjectId().getObjectId();

		ObjectBoundary update = new ObjectBoundary();
		update.setType("   ");
		assertStatus(400, () -> this.objectApi.updateObject(systemID, objectId,
				operator.getUserId().getSystemID(), "operatorBlankType@example.com", TestConstants.DEFAULT_PASSWORD, update));
	}

	@Test
	void testUpdateObjectWithBlankStatusFails() {
		UserBoundary operator = createOperator("operatorBlankStatus@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "BlankStatusBin", "ACTIVE", true);
		String systemID = created.getObjectId().getSystemID();
		String objectId = created.getObjectId().getObjectId();

		ObjectBoundary update = new ObjectBoundary();
		update.setStatus("   ");
		assertStatus(400, () -> this.objectApi.updateObject(systemID, objectId,
				operator.getUserId().getSystemID(), "operatorBlankStatus@example.com", TestConstants.DEFAULT_PASSWORD, update));
	}

	@Test
	void testUpdateObjectPartialOnlyStatusLeavesOtherFieldsUnchanged() {
		UserBoundary operator = createOperator("operatorPartial@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "PartialAlias", "ACTIVE", true);
		String systemID = created.getObjectId().getSystemID();
		String objectId = created.getObjectId().getObjectId();

		ObjectBoundary update = new ObjectBoundary();
		update.setStatus("FULL");
		this.objectApi.updateObject(systemID, objectId,
				operator.getUserId().getSystemID(), "operatorPartial@example.com", TestConstants.DEFAULT_PASSWORD, update);

		ObjectBoundary reloaded = this.objectApi.getObject(systemID, objectId,
				operator.getUserId().getSystemID(), "operatorPartial@example.com", TestConstants.DEFAULT_PASSWORD);

		assertThat(reloaded.getStatus()).isEqualTo("FULL");
		assertThat(reloaded.getAlias()).isEqualTo("PartialAlias");
		assertThat(reloaded.getType()).isEqualTo("Bin");
		assertThat(reloaded.getActive()).isTrue();
	}

	@Test
	void testUpdateObjectByDifferentOperatorNotTheCreatorSucceeds() {
		UserBoundary creator = createOperator("creatorOp@example.com");
		UserBoundary otherOperator = createOperator("otherOp@example.com");
		ObjectBoundary created = createObjectAs(creator, "Bin", "SharedBin", "ACTIVE", true);
		String systemID = created.getObjectId().getSystemID();
		String objectId = created.getObjectId().getObjectId();

		ObjectBoundary update = new ObjectBoundary();
		update.setAlias("UpdatedByOther");
		this.objectApi.updateObject(systemID, objectId,
				otherOperator.getUserId().getSystemID(), "otherOp@example.com", TestConstants.DEFAULT_PASSWORD, update);

		ObjectBoundary reloaded = this.objectApi.getObject(systemID, objectId,
				otherOperator.getUserId().getSystemID(), "otherOp@example.com", TestConstants.DEFAULT_PASSWORD);

		assertThat(reloaded.getAlias()).isEqualTo("UpdatedByOther");
	}

	@Test
	void testGetSpecificObjectAsEndUserActiveObjectSucceeds() {
		UserBoundary operator = createOperator("operatorActiveGet@example.com");
		UserBoundary endUser = createEndUser("enduserActiveGet@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "ActiveGetBin", "ACTIVE", true);

		ObjectBoundary fetched = this.objectApi.getObject(
				created.getObjectId().getSystemID(), created.getObjectId().getObjectId(),
				endUser.getUserId().getSystemID(), "enduserActiveGet@example.com", TestConstants.DEFAULT_PASSWORD);

		assertThat(fetched.getAlias()).isEqualTo("ActiveGetBin");
	}

	@Test
	void testGetSpecificObjectAsOperatorInactiveObjectSucceeds() {
		UserBoundary operator = createOperator("operatorInactiveGet@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "InactiveGetBin", "MAINTENANCE", false);

		ObjectBoundary fetched = this.objectApi.getObject(
				created.getObjectId().getSystemID(), created.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), "operatorInactiveGet@example.com", TestConstants.DEFAULT_PASSWORD);

		assertThat(fetched.getAlias()).isEqualTo("InactiveGetBin");
	}

	@Test
	void testGetAllObjectsAsAdminForbidden() {
		assertStatus(403, () -> this.objectApi.getAllObjects(
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, 20, 0));
	}
}
