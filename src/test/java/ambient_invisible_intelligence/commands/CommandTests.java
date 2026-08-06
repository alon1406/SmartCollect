package ambient_invisible_intelligence.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import ambient_invisible_intelligence.boundaries.CommandBoundary;
import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.ObjectChildIdBoundary;
import ambient_invisible_intelligence.boundaries.ObjectIdBoundary;
import ambient_invisible_intelligence.boundaries.TargetObjectIdBoundary;
import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.boundaries.UserRefBoundary;
import ambient_invisible_intelligence.support.BaseIntegrationTest;
import ambient_invisible_intelligence.support.TestConstants;

/**
 * Sprint 4 coverage for Command invocation: END_USER-only invocation, the
 * active-object requirement, validation, the BinCollected/BinErrorReported/
 * TruckArrivedAtDepot side effects on the object graph, unknown-command
 * pass-through, admin-only history/delete, and the 410 Gone deprecation.
 */
class CommandTests extends BaseIntegrationTest {

	@Test
	void contextLoads() {
	}

	private ObjectBoundary createObjectWithDetails(UserBoundary operator, String type, String alias, String status,
			boolean active, Map<String, Object> details) {
		ObjectBoundary object = buildObject(operator, type, alias, status, active);
		object.setObjectDetails(details);
		return this.objectApi.createObject(TestConstants.DEFAULT_PASSWORD, object);
	}

	private void bindChild(UserBoundary operator, ObjectBoundary parent, ObjectBoundary child) {
		ObjectChildIdBoundary ref = new ObjectChildIdBoundary();
		ref.setChildId(child.getObjectId());
		this.objectApi.bindObjects(parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, ref);
	}

	private ObjectBoundary reload(UserBoundary operator, ObjectBoundary object) {
		return this.objectApi.getObject(object.getObjectId().getSystemID(), object.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD);
	}

	@Test
	void testInvokeCommandAsEndUserSucceedsAndIsStoredInHistory() {
		UserBoundary operator = createOperator("operator1@example.com");
		UserBoundary endUser = createEndUser("enduser1@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "Device1", "ACTIVE", true);

		List<CommandBoundary> result = this.commandApi.invokeCommand(
				TestConstants.DEFAULT_PASSWORD, buildCommand(endUser, target, "activate", Map.of("key", "value")));

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getCommand()).isEqualTo("activate");
		assertThat(result.get(0).getCommandId().getCommandId()).isNotBlank();

		List<CommandBoundary> history = this.adminApi.exportAllCommands(
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, 20, 0);
		assertThat(history).hasSize(1);
		assertThat(history.get(0).getCommand()).isEqualTo("activate");
	}

	@Test
	void testInvokeCommandAsOperatorForbidden() {
		UserBoundary operator = createOperator("operator2@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "Device2", "ACTIVE", true);

		assertStatus(403, () -> this.commandApi.invokeCommand(
				TestConstants.DEFAULT_PASSWORD, buildCommand(operator, target, "activate", Map.of())));
	}

	@Test
	void testInvokeCommandAsAdminForbidden() {
		UserBoundary operator = createOperator("operator3@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "Device3", "ACTIVE", true);

		assertStatus(403, () -> this.commandApi.invokeCommand(
				TestConstants.ADMIN_PASSWORD, buildCommand(this.adminUser, target, "activate", Map.of())));
	}

	@Test
	void testInvokeCommandWithWrongPasswordFails() {
		UserBoundary operator = createOperator("operator4@example.com");
		UserBoundary endUser = createEndUser("enduser4@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "Device4", "ACTIVE", true);

		assertStatus(401, () -> this.commandApi.invokeCommand(
				"WrongPass1!", buildCommand(endUser, target, "activate", Map.of())));
	}

	@Test
	void testInvokeCommandWithMissingCommandNameFails() {
		UserBoundary operator = createOperator("operator5@example.com");
		UserBoundary endUser = createEndUser("enduser5@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "Device5", "ACTIVE", true);

		CommandBoundary command = buildCommand(endUser, target, null, Map.of());
		assertStatus(400, () -> this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD, command));
	}

	@Test
	void testInvokeCommandWithBlankCommandNameFails() {
		UserBoundary operator = createOperator("operator6@example.com");
		UserBoundary endUser = createEndUser("enduser6@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "Device6", "ACTIVE", true);

		CommandBoundary command = buildCommand(endUser, target, "   ", Map.of());
		assertStatus(400, () -> this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD, command));
	}

	@Test
	void testInvokeCommandWithMissingInvokedByFails() {
		UserBoundary operator = createOperator("operator7@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "Device7", "ACTIVE", true);

		CommandBoundary command = new CommandBoundary();
		command.setCommand("activate");
		TargetObjectIdBoundary targetRef = new TargetObjectIdBoundary();
		targetRef.setObjectId(target.getObjectId());
		command.setTargetObject(targetRef);

		assertStatus(400, () -> this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD, command));
	}

	@Test
	void testInvokeCommandWithMissingTargetObjectFails() {
		UserBoundary endUser = createEndUser("enduser8@example.com");

		CommandBoundary command = new CommandBoundary();
		command.setCommand("activate");
		UserRefBoundary invokedBy = new UserRefBoundary();
		invokedBy.setUserId(endUser.getUserId());
		command.setInvokedBy(invokedBy);

		assertStatus(400, () -> this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD, command));
	}

	@Test
	void testInvokeCommandWithNonExistentInvokedByUserFails() {
		UserBoundary operator = createOperator("operator9b@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "Device9b", "ACTIVE", true);
		UserBoundary ghost = fakeUser(TestConstants.SYSTEM_ID, "ghost-invoker@nowhere.test");

		CommandBoundary command = buildCommand(ghost, target, "activate", Map.of());
		assertStatus(401, () -> this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD, command));
	}

	@Test
	void testInvokeCommandOnNonExistentObjectFails() {
		UserBoundary endUser = createEndUser("enduser9@example.com");
		ObjectBoundary fakeTarget = new ObjectBoundary();
		ObjectIdBoundary fakeId = new ObjectIdBoundary();
		fakeId.setSystemID(TestConstants.SYSTEM_ID);
		fakeId.setObjectId("does-not-exist");
		fakeTarget.setObjectId(fakeId);

		CommandBoundary command = buildCommand(endUser, fakeTarget, "activate", Map.of());
		assertStatus(404, () -> this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD, command));
	}

	@Test
	void testInvokeCommandOnInactiveObjectFails() {
		UserBoundary operator = createOperator("operator10@example.com");
		UserBoundary endUser = createEndUser("enduser10@example.com");
		ObjectBoundary inactiveTarget = createObjectAs(operator, "Device", "Device10", "MAINTENANCE", false);

		CommandBoundary command = buildCommand(endUser, inactiveTarget, "activate", Map.of());
		assertStatus(404, () -> this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD, command));
	}

	@Test
	void testInvokeUnknownCommandNameHasNoSideEffectsButIsStored() {
		UserBoundary operator = createOperator("operator11@example.com");
		UserBoundary endUser = createEndUser("enduser11@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "Device11", "ACTIVE", true);

		this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD,
				buildCommand(endUser, target, "SomeUnknownCommand", Map.of("noop", true)));

		ObjectBoundary reloaded = reload(operator, target);
		assertThat(reloaded.getStatus()).isEqualTo("ACTIVE");
		assertThat(reloaded.getObjectDetails()).isEmpty();
	}

	@Test
	void testBinCollectedResetsFillLevelAndUpdatesRouteAndTruck() {
		UserBoundary operator = createOperator("operator12@example.com");
		UserBoundary endUser = createEndUser("enduser12@example.com");

		ObjectBoundary truck = createObjectAs(operator, "TRUCK", "Truck12", "ACTIVE", true);
		Map<String, Object> routeDetails = new HashMap<>();
		routeDetails.put("assignedTruckId", truck.getObjectId().getObjectId());
		ObjectBoundary route = createObjectWithDetails(operator, "ROUTE", "Route12", "ACTIVE", true, routeDetails);
		ObjectBoundary bin = createObjectAs(operator, "BIN", "Bin12", "ACTIVE", true);
		bindChild(operator, route, bin);

		Map<String, Object> attrs = new HashMap<>();
		attrs.put("collectedAt", "2026-07-04T10:00:00Z");
		attrs.put("collectedWeight", 12.5);
		this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD, buildCommand(endUser, bin, "BinCollected", attrs));

		ObjectBoundary reloadedBin = reload(operator, bin);
		assertThat(reloadedBin.getObjectDetails().get("fillLevel")).isEqualTo(0);
		assertThat(reloadedBin.getObjectDetails().get("lastCollected")).isEqualTo("2026-07-04T10:00:00Z");

		ObjectBoundary reloadedRoute = reload(operator, route);
		@SuppressWarnings("unchecked")
		List<Object> completedBinIds = (List<Object>) reloadedRoute.getObjectDetails().get("completedBinIds");
		assertThat(completedBinIds).contains(bin.getObjectId().getObjectId());

		ObjectBoundary reloadedTruck = reload(operator, truck);
		assertThat(((Number) reloadedTruck.getObjectDetails().get("currentLoad")).doubleValue()).isEqualTo(12.5);
		assertThat(((Number) reloadedTruck.getObjectDetails().get("collectedCount")).longValue()).isEqualTo(1L);
	}

	@Test
	void testInvokeCommandLeavesTargetIdentityAndCreationTimestampUnchanged() {
		UserBoundary operator = createOperator("operator12b@example.com");
		UserBoundary endUser = createEndUser("enduser12b@example.com");

		ObjectBoundary truck = createObjectAs(operator, "TRUCK", "Truck12b", "ACTIVE", true);
		Map<String, Object> routeDetails = new HashMap<>();
		routeDetails.put("assignedTruckId", truck.getObjectId().getObjectId());
		ObjectBoundary route = createObjectWithDetails(operator, "ROUTE", "Route12b", "ACTIVE", true, routeDetails);
		ObjectBoundary bin = createObjectAs(operator, "BIN", "Bin12b", "ACTIVE", true);
		bindChild(operator, route, bin);

		this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD,
				buildCommand(endUser, bin, "BinCollected", Map.of("collectedWeight", 3.0)));

		for (ObjectBoundary original : List.of(truck, route, bin)) {
			ObjectBoundary reloaded = reload(operator, original);
			assertThat(reloaded.getObjectId().getSystemID()).isEqualTo(original.getObjectId().getSystemID());
			assertThat(reloaded.getObjectId().getObjectId()).isEqualTo(original.getObjectId().getObjectId());
			assertThat(reloaded.getCreationTimestamp().toInstant().toEpochMilli())
					.isEqualTo(original.getCreationTimestamp().toInstant().toEpochMilli());
		}
	}

	@Test
	void testBinCollectedFailsWhenBinNotBoundToRoute() {
		UserBoundary operator = createOperator("operator13@example.com");
		UserBoundary endUser = createEndUser("enduser13@example.com");
		ObjectBoundary unboundBin = createObjectAs(operator, "BIN", "UnboundBin13", "ACTIVE", true);

		CommandBoundary command = buildCommand(endUser, unboundBin, "BinCollected", Map.of("collectedWeight", 5.0));
		assertStatus(400, () -> this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD, command));
	}

	@Test
	void testBinErrorReportedSetsMaintenanceAndRemovesFromRoute() {
		UserBoundary operator = createOperator("operator14@example.com");
		UserBoundary endUser = createEndUser("enduser14@example.com");

		ObjectBoundary bin = createObjectAs(operator, "BIN", "Bin14", "ACTIVE", true);
		String binUUID = bin.getObjectId().getObjectId();
		Map<String, Object> routeDetails = new HashMap<>();
		routeDetails.put("binIds", new ArrayList<>(List.of(binUUID, "other-bin-id")));
		routeDetails.put("completedBinIds", new ArrayList<>(List.of(binUUID)));
		ObjectBoundary route = createObjectWithDetails(operator, "ROUTE", "Route14", "ACTIVE", true, routeDetails);
		bindChild(operator, route, bin);

		this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD,
				buildCommand(endUser, bin, "BinErrorReported", Map.of("errorType", "jam")));

		ObjectBoundary reloadedBin = reload(operator, bin);
		assertThat(reloadedBin.getObjectDetails().get("status")).isEqualTo("maintenance");
		assertThat(reloadedBin.getObjectDetails().get("errorStatus")).isEqualTo("jam");
		// The top-level status column (used by search/byStatus) is untouched.
		assertThat(reloadedBin.getStatus()).isEqualTo("ACTIVE");

		ObjectBoundary reloadedRoute = reload(operator, route);
		@SuppressWarnings("unchecked")
		List<Object> binIds = (List<Object>) reloadedRoute.getObjectDetails().get("binIds");
		@SuppressWarnings("unchecked")
		List<Object> completedBinIds = (List<Object>) reloadedRoute.getObjectDetails().get("completedBinIds");
		assertThat(binIds).doesNotContain(binUUID).contains("other-bin-id");
		assertThat(completedBinIds).doesNotContain(binUUID);
	}

	@Test
	void testTruckArrivedAtDepotSetsRouteReturningWhenInvokedOnRoute() {
		UserBoundary operator = createOperator("operator15@example.com");
		UserBoundary endUser = createEndUser("enduser15@example.com");

		ObjectBoundary truck = createObjectAs(operator, "TRUCK", "Truck15", "ACTIVE", true);
		Map<String, Object> routeDetails = new HashMap<>();
		routeDetails.put("assignedTruckId", truck.getObjectId().getObjectId());
		ObjectBoundary route = createObjectWithDetails(operator, "ROUTE", "Route15", "ACTIVE", true, routeDetails);

		this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD,
				buildCommand(endUser, route, "TruckArrivedAtDepot", Map.of("arrivedAt", "2026-07-04T12:00:00Z")));

		ObjectBoundary reloadedRoute = reload(operator, route);
		assertThat(reloadedRoute.getObjectDetails().get("status")).isEqualTo("returning");
		assertThat(reloadedRoute.getObjectDetails().get("completedAt")).isEqualTo("2026-07-04T12:00:00Z");

		ObjectBoundary reloadedTruck = reload(operator, truck);
		assertThat(reloadedTruck.getObjectDetails().get("status")).isEqualTo("returning");
	}

	@Test
	void testTruckArrivedAtDepotSetsTruckReturningWhenInvokedOnTruck() {
		UserBoundary operator = createOperator("operator16@example.com");
		UserBoundary endUser = createEndUser("enduser16@example.com");

		ObjectBoundary truck = createObjectAs(operator, "TRUCK", "Truck16", "ACTIVE", true);
		Map<String, Object> routeDetails = new HashMap<>();
		routeDetails.put("assignedTruckId", truck.getObjectId().getObjectId());
		ObjectBoundary route = createObjectWithDetails(operator, "ROUTE", "Route16", "ACTIVE", true, routeDetails);

		this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD,
				buildCommand(endUser, truck, "TruckArrivedAtDepot", Map.of("arrivedAt", "2026-07-04T13:00:00Z")));

		ObjectBoundary reloadedTruck = reload(operator, truck);
		assertThat(reloadedTruck.getObjectDetails().get("status")).isEqualTo("returning");

		ObjectBoundary reloadedRoute = reload(operator, route);
		assertThat(reloadedRoute.getObjectDetails().get("status")).isEqualTo("returning");
	}

	@Test
	void testGetCommandHistoryAsAdminSucceeds() {
		UserBoundary operator = createOperator("operator17@example.com");
		UserBoundary endUser = createEndUser("enduser17@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "Device17", "ACTIVE", true);
		this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD, buildCommand(endUser, target, "activate", Map.of()));

		List<CommandBoundary> history = this.adminApi.exportAllCommands(
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, 20, 0);

		assertThat(history).hasSize(1);
	}

	@Test
	void testGetCommandHistoryAsNonAdminForbidden() {
		UserBoundary operator = createOperator("operator18@example.com");

		assertStatus(403, () -> this.adminApi.exportAllCommands(
				operator.getUserId().getSystemID(), "operator18@example.com", TestConstants.DEFAULT_PASSWORD, 20, 0));
	}

	@Test
	void testGetCommandHistoryDeprecatedReturns410() {
		assertStatus(410, () -> this.adminApi.exportAllCommandsDeprecated(
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD));
	}

	@Test
	void testDeleteAllCommandsAsAdminSucceeds() {
		UserBoundary operator = createOperator("operator19@example.com");
		UserBoundary endUser = createEndUser("enduser19@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "Device19", "ACTIVE", true);
		this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD, buildCommand(endUser, target, "activate", Map.of()));

		this.adminApi.deleteAllCommands(this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD);

		List<CommandBoundary> history = this.adminApi.exportAllCommands(
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, 20, 0);
		assertThat(history).isEmpty();
	}

	@Test
	void testDeleteAllCommandsAsNonAdminForbidden() {
		UserBoundary operator = createOperator("operator20@example.com");

		assertStatus(403, () -> this.adminApi.deleteAllCommands(
				operator.getUserId().getSystemID(), "operator20@example.com", TestConstants.DEFAULT_PASSWORD));
	}

	@Test
	void testCommandHistoryPagination() {
		UserBoundary operator = createOperator("operator21@example.com");
		UserBoundary endUser = createEndUser("enduser21@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "Device21", "ACTIVE", true);

		Set<String> expectedIds = IntStream.range(0, 5)
				.mapToObj(i -> this.commandApi.invokeCommand(
						TestConstants.DEFAULT_PASSWORD, buildCommand(endUser, target, "cmd" + i, Map.of())).get(0))
				.map(c -> c.getCommandId().getCommandId())
				.collect(Collectors.toSet());

		List<String> collected = new ArrayList<>();
		int page = 0;
		int size = 2;
		List<CommandBoundary> pageResult;
		do {
			pageResult = this.adminApi.exportAllCommands(
					this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, size, page);
			assertThat(pageResult.size()).isLessThanOrEqualTo(size);
			pageResult.forEach(c -> collected.add(c.getCommandId().getCommandId()));
			page++;
		} while (!pageResult.isEmpty());

		assertThat(collected).hasSize(5);
		assertThat(new HashSet<>(collected)).isEqualTo(expectedIds);
	}

	@Test
	void testInvokeCommandAttributesArePreservedInHistory() {
		UserBoundary operator = createOperator("operatorAttrs@example.com");
		UserBoundary endUser = createEndUser("enduserAttrs@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "DeviceAttrs", "ACTIVE", true);

		this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD,
				buildCommand(endUser, target, "activate", Map.of("intensity", "high", "retries", "3")));

		List<CommandBoundary> history = this.adminApi.exportAllCommands(
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, 20, 0);

		assertThat(history).hasSize(1);
		assertThat(history.get(0).getCommandAttributes())
				.containsEntry("intensity", "high")
				.containsEntry("retries", "3");
	}

	@Test
	void testInvokeCommandSetsInvocationTimestamp() {
		UserBoundary operator = createOperator("operatorTs@example.com");
		UserBoundary endUser = createEndUser("enduserTs@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "DeviceTs", "ACTIVE", true);

		List<CommandBoundary> result = this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD,
				buildCommand(endUser, target, "activate", Map.of()));

		assertThat(result.get(0).getInvocationTimestamp()).isNotNull();
	}

	@Test
	void testInvokeCommandGeneratedCommandIdHasSystemID() {
		UserBoundary operator = createOperator("operatorCmdId@example.com");
		UserBoundary endUser = createEndUser("enduserCmdId@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "DeviceCmdId", "ACTIVE", true);

		List<CommandBoundary> result = this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD,
				buildCommand(endUser, target, "activate", Map.of()));

		assertThat(result.get(0).getCommandId().getSystemID()).isEqualTo(TestConstants.SYSTEM_ID);
	}

	@Test
	void testInvokeCommandHistoryPreservesInvokedByAndTargetObject() {
		UserBoundary operator = createOperator("operatorPreserve@example.com");
		UserBoundary endUser = createEndUser("enduserPreserve@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "DevicePreserve", "ACTIVE", true);

		this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD,
				buildCommand(endUser, target, "activate", Map.of()));

		List<CommandBoundary> history = this.adminApi.exportAllCommands(
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, 20, 0);

		assertThat(history).hasSize(1);
		assertThat(history.get(0).getInvokedBy().getUserId().getEmail()).isEqualTo("enduserPreserve@example.com");
		assertThat(history.get(0).getTargetObject().getObjectId().getObjectId())
				.isEqualTo(target.getObjectId().getObjectId());
	}

	@Test
	void testInvokeCommandPreservesNestedAttributeValues() {
		UserBoundary operator = createOperator("operatorNested@example.com");
		UserBoundary endUser = createEndUser("enduserNested@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "DeviceNested", "ACTIVE", true);

		this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD,
				buildCommand(endUser, target, "activate", Map.of("payload", Map.of("inner", "deepValue"))));

		List<CommandBoundary> history = this.adminApi.exportAllCommands(
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, 20, 0);

		@SuppressWarnings("unchecked")
		Map<String, Object> payload = (Map<String, Object>) history.get(0).getCommandAttributes().get("payload");
		assertThat(payload).containsEntry("inner", "deepValue");
	}

	@Test
	void testInvokeCommandTargetFromDifferentSystemIdReturns404() {
		UserBoundary operator = createOperator("operatorDiffSys@example.com");
		UserBoundary endUser = createEndUser("enduserDiffSys@example.com");
		ObjectBoundary target = createObjectAs(operator, "Device", "DeviceDiffSys", "ACTIVE", true);

		CommandBoundary command = buildCommand(endUser, target, "activate", Map.of());
		ObjectIdBoundary forgedTargetId = new ObjectIdBoundary();
		forgedTargetId.setSystemID("other-system");
		forgedTargetId.setObjectId(target.getObjectId().getObjectId());
		TargetObjectIdBoundary targetRef = new TargetObjectIdBoundary();
		targetRef.setObjectId(forgedTargetId);
		command.setTargetObject(targetRef);

		assertStatus(404, () -> this.commandApi.invokeCommand(TestConstants.DEFAULT_PASSWORD, command));
	}
}
