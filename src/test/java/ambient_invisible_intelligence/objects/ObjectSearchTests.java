package ambient_invisible_intelligence.objects;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.support.BaseIntegrationTest;
import ambient_invisible_intelligence.support.TestConstants;

/**
 * Sprint 4 coverage for the new Object search endpoints: byAlias,
 * byAliasPattern, byType, byStatus and byLocation, including pagination,
 * the OPERATOR-vs-END_USER active-only visibility rule, and the square
 * (not circle) shape of the location search per the spec footnote.
 */
class ObjectSearchTests extends BaseIntegrationTest {

	@Test
	void contextLoads() {
	}

	@Test
	void testSearchByAliasExactMatchAsOperatorReturnsMatchingObjectIncludingInactive() {
		UserBoundary operator = createOperator("operator1@example.com");
		createObjectAs(operator, "Bin", "UniqueAlias1", "ACTIVE", true);
		createObjectAs(operator, "Bin", "UniqueAlias1", "MAINTENANCE", false);
		createObjectAs(operator, "Bin", "OtherAlias1", "ACTIVE", true);

		List<ObjectBoundary> results = this.objectApi.searchByAlias("UniqueAlias1",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(results).hasSize(2);
		assertThat(results).allMatch(o -> o.getAlias().equals("UniqueAlias1"));
	}

	@Test
	void testSearchByAliasAsEndUserOnlyReturnsActiveMatches() {
		UserBoundary operator = createOperator("operator2@example.com");
		UserBoundary endUser = createEndUser("enduser2@example.com");
		createObjectAs(operator, "Bin", "SharedAlias2", "ACTIVE", true);
		createObjectAs(operator, "Bin", "SharedAlias2", "MAINTENANCE", false);

		List<ObjectBoundary> results = this.objectApi.searchByAlias("SharedAlias2",
				endUser.getUserId().getSystemID(), endUser.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getActive()).isTrue();
	}

	@Test
	void testSearchByAliasNoMatchReturnsEmptyList() {
		UserBoundary operator = createOperator("operator3@example.com");

		List<ObjectBoundary> results = this.objectApi.searchByAlias("NoSuchAlias",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(results).isEmpty();
	}

	@Test
	void testSearchByAliasPatternMatchesSubstring() {
		UserBoundary operator = createOperator("operator4@example.com");
		createObjectAs(operator, "Bin", "NorthGateSensor", "ACTIVE", true);
		createObjectAs(operator, "Bin", "SouthGateSensor", "ACTIVE", true);
		createObjectAs(operator, "Bin", "Unrelated", "ACTIVE", true);

		List<ObjectBoundary> results = this.objectApi.searchByAliasPattern("Gate",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(results).extracting(ObjectBoundary::getAlias)
				.containsExactlyInAnyOrder("NorthGateSensor", "SouthGateSensor");
	}

	@Test
	void testSearchByAliasPatternAsEndUserOnlyReturnsActiveMatches() {
		UserBoundary operator = createOperator("operator5@example.com");
		UserBoundary endUser = createEndUser("enduser5@example.com");
		createObjectAs(operator, "Bin", "PatternActive5", "ACTIVE", true);
		createObjectAs(operator, "Bin", "PatternInactive5", "MAINTENANCE", false);

		List<ObjectBoundary> results = this.objectApi.searchByAliasPattern("Pattern",
				endUser.getUserId().getSystemID(), endUser.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(results).extracting(ObjectBoundary::getAlias).containsExactly("PatternActive5");
	}

	@Test
	void testSearchByTypeReturnsAllMatchingTypeForOperator() {
		UserBoundary operator = createOperator("operator6@example.com");
		createObjectAs(operator, "TRUCK", "Truck1", "ACTIVE", true);
		createObjectAs(operator, "TRUCK", "Truck2", "MAINTENANCE", false);
		createObjectAs(operator, "BIN", "Bin6", "ACTIVE", true);

		List<ObjectBoundary> results = this.objectApi.searchByType("TRUCK",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(results).extracting(ObjectBoundary::getAlias).containsExactlyInAnyOrder("Truck1", "Truck2");
	}

	@Test
	void testSearchByTypeAsEndUserOnlyReturnsActive() {
		UserBoundary operator = createOperator("operator7@example.com");
		UserBoundary endUser = createEndUser("enduser7@example.com");
		createObjectAs(operator, "TRUCK", "ActiveTruck7", "ACTIVE", true);
		createObjectAs(operator, "TRUCK", "InactiveTruck7", "MAINTENANCE", false);

		List<ObjectBoundary> results = this.objectApi.searchByType("TRUCK",
				endUser.getUserId().getSystemID(), endUser.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(results).extracting(ObjectBoundary::getAlias).containsExactly("ActiveTruck7");
	}

	@Test
	void testSearchByStatusReturnsMatchingObjects() {
		UserBoundary operator = createOperator("operator8@example.com");
		createObjectAs(operator, "Bin", "Full1", "FULL", true);
		createObjectAs(operator, "Bin", "Full2", "FULL", false);
		createObjectAs(operator, "Bin", "Empty1", "EMPTY", true);

		List<ObjectBoundary> results = this.objectApi.searchByStatus("FULL",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(results).extracting(ObjectBoundary::getAlias).containsExactlyInAnyOrder("Full1", "Full2");
	}

	@Test
	void testSearchByStatusAsEndUserOnlyReturnsActive() {
		UserBoundary operator = createOperator("operator9@example.com");
		UserBoundary endUser = createEndUser("enduser9@example.com");
		createObjectAs(operator, "Bin", "ActiveFull9", "FULL", true);
		createObjectAs(operator, "Bin", "InactiveFull9", "FULL", false);

		List<ObjectBoundary> results = this.objectApi.searchByStatus("FULL",
				endUser.getUserId().getSystemID(), endUser.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(results).extracting(ObjectBoundary::getAlias).containsExactly("ActiveFull9");
	}

	@Test
	void testSearchByLocationWithNeutralUnitsReturnsPointsWithinSquare() {
		UserBoundary operator = createOperator("operator10@example.com");
		createObjectWithLocation(operator, "Bin", "InsideSquare10", "ACTIVE", true, 32.5, 34.5);

		List<ObjectBoundary> results = this.objectApi.searchByLocation(32.0, 34.0, 1.0, "NEUTRAL",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(results).extracting(ObjectBoundary::getAlias).contains("InsideSquare10");
	}

	@Test
	void testSearchByLocationExcludesPointsOutsideSquare() {
		UserBoundary operator = createOperator("operator11@example.com");
		createObjectWithLocation(operator, "Bin", "OutsideSquare11", "ACTIVE", true, 32.5, 36.0);

		List<ObjectBoundary> results = this.objectApi.searchByLocation(32.0, 34.0, 1.0, "NEUTRAL",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(results).extracting(ObjectBoundary::getAlias).doesNotContain("OutsideSquare11");
	}

	@Test
	void testSearchByLocationCornerPointWithinSquareButOutsideCircleIsStillReturned() {
		// Per spec: basic location search is within a SQUARE, not a circle.
		// This point is 0.99 away on both axes (inside the 1.0 square) but its
		// Euclidean distance from the center (~1.4) would fall outside a circle
		// of radius 1.0 -- proving the implementation is square-shaped.
		UserBoundary operator = createOperator("operator12@example.com");
		createObjectWithLocation(operator, "Bin", "SquareCorner12", "ACTIVE", true, 32.99, 34.99);

		List<ObjectBoundary> results = this.objectApi.searchByLocation(32.0, 34.0, 1.0, "NEUTRAL",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(results).extracting(ObjectBoundary::getAlias).contains("SquareCorner12");
	}

	@Test
	void testSearchByLocationWithKmUnitsConvertsDistanceCorrectly() {
		// Center and point are pure-latitude offset by 0.5 degrees (~55.5 km),
		// so the cos(lat) longitude term never enters the calculation.
		UserBoundary operator = createOperator("operator16@example.com");
		createObjectWithLocation(operator, "Bin", "KmTarget16", "ACTIVE", true, 32.5, 34.0);

		List<ObjectBoundary> tooClose = this.objectApi.searchByLocation(32.0, 34.0, 50, "KM",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);
		List<ObjectBoundary> farEnough = this.objectApi.searchByLocation(32.0, 34.0, 60, "KM",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(tooClose).extracting(ObjectBoundary::getAlias).doesNotContain("KmTarget16");
		assertThat(farEnough).extracting(ObjectBoundary::getAlias).contains("KmTarget16");
	}

	@Test
	void testSearchByLocationWithMilesUnitsConvertsDistanceCorrectly() {
		// Same ~55.5 km (~34.5 mile) pure-latitude offset as the KM test.
		UserBoundary operator = createOperator("operator17@example.com");
		createObjectWithLocation(operator, "Bin", "MilesTarget17", "ACTIVE", true, 32.5, 34.0);

		List<ObjectBoundary> tooClose = this.objectApi.searchByLocation(32.0, 34.0, 30, "MILES",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);
		List<ObjectBoundary> farEnough = this.objectApi.searchByLocation(32.0, 34.0, 40, "MILES",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(tooClose).extracting(ObjectBoundary::getAlias).doesNotContain("MilesTarget17");
		assertThat(farEnough).extracting(ObjectBoundary::getAlias).contains("MilesTarget17");
	}

	@Test
	void testSearchByLocationAsEndUserOnlyReturnsActive() {
		UserBoundary operator = createOperator("operator13@example.com");
		UserBoundary endUser = createEndUser("enduser13@example.com");
		createObjectWithLocation(operator, "Bin", "ActiveNear13", "ACTIVE", true, 32.1, 34.1);
		createObjectWithLocation(operator, "Bin", "InactiveNear13", "MAINTENANCE", false, 32.1, 34.1);

		List<ObjectBoundary> results = this.objectApi.searchByLocation(32.0, 34.0, 1.0, "NEUTRAL",
				endUser.getUserId().getSystemID(), endUser.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(results).extracting(ObjectBoundary::getAlias).containsExactly("ActiveNear13");
	}

	@Test
	void testSearchByAliasAsAdminForbidden() {
		assertStatus(403, () -> this.objectApi.searchByAlias("Anything",
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, 20, 0));
	}

	@Test
	void testSearchByTypeWithWrongPasswordFails() {
		UserBoundary operator = createOperator("operator14@example.com");

		assertStatus(401, () -> this.objectApi.searchByType("Bin",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), "WrongPass1!", 20, 0));
	}

	@Test
	void testSearchByAliasPatternPaginationRespectsSize() {
		UserBoundary operator = createOperator("operator15@example.com");
		for (int i = 0; i < 5; i++) {
			createObjectAs(operator, "Bin", "PagedPattern15-" + i, "ACTIVE", true);
		}

		List<ObjectBoundary> firstPage = this.objectApi.searchByAliasPattern("PagedPattern15",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 2, 0);

		assertThat(firstPage).hasSize(2);
	}

	@Test
	void testSearchByAliasPatternMatchesPrefixMiddleAndSuffix() {
		UserBoundary operator = createOperator("operatorPositions@example.com");
		createObjectAs(operator, "Bin", "KEYprefix", "ACTIVE", true);
		createObjectAs(operator, "Bin", "midKEYdle", "ACTIVE", true);
		createObjectAs(operator, "Bin", "suffixKEY", "ACTIVE", true);

		List<ObjectBoundary> results = this.objectApi.searchByAliasPattern("KEY",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(results).extracting(ObjectBoundary::getAlias)
				.containsExactlyInAnyOrder("KEYprefix", "midKEYdle", "suffixKEY");
	}

	@Test
	void testSearchByAliasPartialInputDoesNotMatch() {
		UserBoundary operator = createOperator("operatorPartialAlias@example.com");
		createObjectAs(operator, "Bin", "UniqueAliasValue", "ACTIVE", true);

		List<ObjectBoundary> results = this.objectApi.searchByAlias("Unique",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(results).isEmpty();
	}

	@Test
	void testUpdateAliasIsReflectedInSearchByAlias() {
		UserBoundary operator = createOperator("operatorSearchUpdate@example.com");
		ObjectBoundary created = createObjectAs(operator, "Bin", "OldSearchAlias", "ACTIVE", true);

		ObjectBoundary update = new ObjectBoundary();
		update.setAlias("NewSearchAlias");
		this.objectApi.updateObject(created.getObjectId().getSystemID(), created.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), "operatorSearchUpdate@example.com", TestConstants.DEFAULT_PASSWORD, update);

		List<ObjectBoundary> byNew = this.objectApi.searchByAlias("NewSearchAlias",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);
		List<ObjectBoundary> byOld = this.objectApi.searchByAlias("OldSearchAlias",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(byNew).extracting(ObjectBoundary::getAlias).containsExactly("NewSearchAlias");
		assertThat(byOld).isEmpty();
	}

	@Test
	void testSearchByAliasPatternAsAdminForbidden() {
		assertStatus(403, () -> this.objectApi.searchByAliasPattern("Anything",
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, 20, 0));
	}

	@Test
	void testSearchByTypeAsAdminForbidden() {
		assertStatus(403, () -> this.objectApi.searchByType("Bin",
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, 20, 0));
	}

	@Test
	void testSearchByStatusAsAdminForbidden() {
		assertStatus(403, () -> this.objectApi.searchByStatus("ACTIVE",
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, 20, 0));
	}

	@Test
	void testSearchByLocationAsAdminForbidden() {
		assertStatus(403, () -> this.objectApi.searchByLocation(32.0, 34.0, 1.0, "NEUTRAL",
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, 20, 0));
	}
}
