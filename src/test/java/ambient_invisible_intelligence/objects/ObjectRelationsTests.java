package ambient_invisible_intelligence.objects;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.ObjectChildIdBoundary;
import ambient_invisible_intelligence.boundaries.ObjectIdBoundary;
import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.support.BaseIntegrationTest;
import ambient_invisible_intelligence.support.TestConstants;

/**
 * Sprint 4 coverage for the parent/child object graph: binding, pagination
 * over children/parents, the OPERATOR-only bind restriction, END_USER active-
 * only visibility, and the 410 Gone deprecation of the un-paginated variants.
 */
class ObjectRelationsTests extends BaseIntegrationTest {

	@Test
	void contextLoads() {
	}

	private void bind(UserBoundary operator, ObjectBoundary parent, ObjectBoundary child) {
		ObjectChildIdBoundary ref = new ObjectChildIdBoundary();
		ref.setChildId(child.getObjectId());
		this.objectApi.bindObjects(parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, ref);
	}

	@Test
	void testBindObjectsAsOperatorSucceedsAndChildIsReturned() {
		UserBoundary operator = createOperator("operator1@example.com");
		ObjectBoundary parent = createObjectAs(operator, "Route", "Route1", "ACTIVE", true);
		ObjectBoundary child = createObjectAs(operator, "Bin", "Bin1", "ACTIVE", true);

		bind(operator, parent, child);

		List<ObjectBoundary> children = this.objectApi.getChildren(
				parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(children).extracting(ObjectBoundary::getAlias).containsExactly("Bin1");
	}

	@Test
	void testBindObjectsIsIdempotent() {
		UserBoundary operator = createOperator("operator2@example.com");
		ObjectBoundary parent = createObjectAs(operator, "Route", "Route2", "ACTIVE", true);
		ObjectBoundary child = createObjectAs(operator, "Bin", "Bin2", "ACTIVE", true);

		bind(operator, parent, child);
		bind(operator, parent, child);

		List<ObjectBoundary> children = this.objectApi.getChildren(
				parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(children).hasSize(1);
	}

	@Test
	void testBindObjectsAsEndUserForbidden() {
		UserBoundary operator = createOperator("operator3@example.com");
		UserBoundary endUser = createEndUser("enduser3@example.com");
		ObjectBoundary parent = createObjectAs(operator, "Route", "Route3", "ACTIVE", true);
		ObjectBoundary child = createObjectAs(operator, "Bin", "Bin3", "ACTIVE", true);

		ObjectChildIdBoundary ref = new ObjectChildIdBoundary();
		ref.setChildId(child.getObjectId());

		assertStatus(403, () -> this.objectApi.bindObjects(
				parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				endUser.getUserId().getSystemID(), endUser.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, ref));
	}

	@Test
	void testBindObjectsWithWrongPasswordFails() {
		UserBoundary operator = createOperator("operator3b@example.com");
		ObjectBoundary parent = createObjectAs(operator, "Route", "Route3b", "ACTIVE", true);
		ObjectBoundary child = createObjectAs(operator, "Bin", "Bin3b", "ACTIVE", true);

		ObjectChildIdBoundary ref = new ObjectChildIdBoundary();
		ref.setChildId(child.getObjectId());

		assertStatus(401, () -> this.objectApi.bindObjects(
				parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), "operator3b@example.com", "WrongPass1!", ref));
	}

	@Test
	void testBindObjectsAsAdminForbidden() {
		UserBoundary operator = createOperator("operator3c@example.com");
		ObjectBoundary parent = createObjectAs(operator, "Route", "Route3c", "ACTIVE", true);
		ObjectBoundary child = createObjectAs(operator, "Bin", "Bin3c", "ACTIVE", true);

		ObjectChildIdBoundary ref = new ObjectChildIdBoundary();
		ref.setChildId(child.getObjectId());

		assertStatus(403, () -> this.objectApi.bindObjects(
				parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, ref));
	}

	@Test
	void testBindObjectsWithNonExistentParentFails() {
		UserBoundary operator = createOperator("operator4@example.com");
		ObjectBoundary child = createObjectAs(operator, "Bin", "Bin4", "ACTIVE", true);

		ObjectChildIdBoundary ref = new ObjectChildIdBoundary();
		ref.setChildId(child.getObjectId());

		assertStatus(404, () -> this.objectApi.bindObjects(
				TestConstants.SYSTEM_ID, "no-such-parent",
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, ref));
	}

	@Test
	void testBindObjectsWithNonExistentChildFails() {
		UserBoundary operator = createOperator("operator5@example.com");
		ObjectBoundary parent = createObjectAs(operator, "Route", "Route5", "ACTIVE", true);

		ObjectChildIdBoundary ref = new ObjectChildIdBoundary();
		ObjectIdBoundary bogusChild = new ObjectIdBoundary();
		bogusChild.setSystemID(TestConstants.SYSTEM_ID);
		bogusChild.setObjectId("no-such-child");
		ref.setChildId(bogusChild);

		assertStatus(404, () -> this.objectApi.bindObjects(
				parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, ref));
	}

	@Test
	void testGetChildrenAsOperatorIncludesInactiveChildren() {
		UserBoundary operator = createOperator("operator6@example.com");
		ObjectBoundary parent = createObjectAs(operator, "Route", "Route6", "ACTIVE", true);
		ObjectBoundary activeChild = createObjectAs(operator, "Bin", "ActiveChild6", "ACTIVE", true);
		ObjectBoundary inactiveChild = createObjectAs(operator, "Bin", "InactiveChild6", "MAINTENANCE", false);
		bind(operator, parent, activeChild);
		bind(operator, parent, inactiveChild);

		List<ObjectBoundary> children = this.objectApi.getChildren(
				parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(children).extracting(ObjectBoundary::getAlias)
				.containsExactlyInAnyOrder("ActiveChild6", "InactiveChild6");
	}

	@Test
	void testGetChildrenAsEndUserOnlyReturnsActiveChildren() {
		UserBoundary operator = createOperator("operator7@example.com");
		UserBoundary endUser = createEndUser("enduser7@example.com");
		ObjectBoundary parent = createObjectAs(operator, "Route", "Route7", "ACTIVE", true);
		ObjectBoundary activeChild = createObjectAs(operator, "Bin", "ActiveChild7", "ACTIVE", true);
		ObjectBoundary inactiveChild = createObjectAs(operator, "Bin", "InactiveChild7", "MAINTENANCE", false);
		bind(operator, parent, activeChild);
		bind(operator, parent, inactiveChild);

		List<ObjectBoundary> children = this.objectApi.getChildren(
				parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				endUser.getUserId().getSystemID(), endUser.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(children).extracting(ObjectBoundary::getAlias).containsExactly("ActiveChild7");
	}

	@Test
	void testGetChildrenAsEndUserWithInactiveParentReturns404() {
		UserBoundary operator = createOperator("operator8@example.com");
		UserBoundary endUser = createEndUser("enduser8@example.com");
		ObjectBoundary parent = createObjectAs(operator, "Route", "InactiveRoute8", "MAINTENANCE", false);

		assertStatus(404, () -> this.objectApi.getChildren(
				parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				endUser.getUserId().getSystemID(), endUser.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0));
	}

	@Test
	void testGetChildrenDeprecatedReturns410() {
		UserBoundary operator = createOperator("operator9@example.com");
		ObjectBoundary parent = createObjectAs(operator, "Route", "Route9", "ACTIVE", true);

		assertStatus(410, () -> this.objectApi.getChildrenDeprecated(
				parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD));
	}

	@Test
	void testGetParentsAsOperatorSucceeds() {
		UserBoundary operator = createOperator("operator10@example.com");
		ObjectBoundary parent = createObjectAs(operator, "Route", "Route10", "ACTIVE", true);
		ObjectBoundary child = createObjectAs(operator, "Bin", "Bin10", "ACTIVE", true);
		bind(operator, parent, child);

		List<ObjectBoundary> parents = this.objectApi.getParents(
				child.getObjectId().getSystemID(), child.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(parents).extracting(ObjectBoundary::getAlias).containsExactly("Route10");
	}

	@Test
	void testGetParentsAsEndUserOnlyReturnsActiveParents() {
		UserBoundary operator = createOperator("operator11@example.com");
		UserBoundary endUser = createEndUser("enduser11@example.com");
		ObjectBoundary activeParent = createObjectAs(operator, "Route", "ActiveRoute11", "ACTIVE", true);
		ObjectBoundary inactiveParent = createObjectAs(operator, "Route", "InactiveRoute11", "MAINTENANCE", false);
		ObjectBoundary child = createObjectAs(operator, "Bin", "Bin11", "ACTIVE", true);
		bind(operator, activeParent, child);
		bind(operator, inactiveParent, child);

		List<ObjectBoundary> parents = this.objectApi.getParents(
				child.getObjectId().getSystemID(), child.getObjectId().getObjectId(),
				endUser.getUserId().getSystemID(), endUser.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(parents).extracting(ObjectBoundary::getAlias).containsExactly("ActiveRoute11");
	}

	@Test
	void testGetParentsAsEndUserWithInactiveChildReturns404() {
		UserBoundary operator = createOperator("operator12@example.com");
		UserBoundary endUser = createEndUser("enduser12@example.com");
		ObjectBoundary inactiveChild = createObjectAs(operator, "Bin", "InactiveBin12", "MAINTENANCE", false);

		assertStatus(404, () -> this.objectApi.getParents(
				inactiveChild.getObjectId().getSystemID(), inactiveChild.getObjectId().getObjectId(),
				endUser.getUserId().getSystemID(), endUser.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0));
	}

	@Test
	void testGetParentsDeprecatedReturns410() {
		UserBoundary operator = createOperator("operator13@example.com");
		ObjectBoundary child = createObjectAs(operator, "Bin", "Bin13", "ACTIVE", true);

		assertStatus(410, () -> this.objectApi.getParentsDeprecated(
				child.getObjectId().getSystemID(), child.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD));
	}

	@Test
	void testGetChildrenPaginationRespectsSize() {
		UserBoundary operator = createOperator("operator14@example.com");
		ObjectBoundary parent = createObjectAs(operator, "Route", "Route14", "ACTIVE", true);
		for (int i = 0; i < 4; i++) {
			ObjectBoundary child = createObjectAs(operator, "Bin", "Bin14-" + i, "ACTIVE", true);
			bind(operator, parent, child);
		}

		List<ObjectBoundary> firstPage = this.objectApi.getChildren(
				parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 3, 0);
		List<ObjectBoundary> secondPage = this.objectApi.getChildren(
				parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 3, 1);

		assertThat(firstPage).hasSize(3);
		assertThat(secondPage).hasSize(1);
	}

	@Test
	void testGetParentsPaginationRespectsSize() {
		UserBoundary operator = createOperator("operator15@example.com");
		ObjectBoundary child = createObjectAs(operator, "Bin", "Bin15", "ACTIVE", true);
		for (int i = 0; i < 4; i++) {
			ObjectBoundary parent = createObjectAs(operator, "Route", "Route15-" + i, "ACTIVE", true);
			bind(operator, parent, child);
		}

		List<ObjectBoundary> firstPage = this.objectApi.getParents(
				child.getObjectId().getSystemID(), child.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 3, 0);
		List<ObjectBoundary> secondPage = this.objectApi.getParents(
				child.getObjectId().getSystemID(), child.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 3, 1);

		assertThat(firstPage).hasSize(3);
		assertThat(secondPage).hasSize(1);
	}

	@Test
	void testThreeLevelChainGrandparentChildrenDoNotIncludeGrandchild() {
		UserBoundary operator = createOperator("operatorChain1@example.com");
		ObjectBoundary grandparent = createObjectAs(operator, "Route", "Grandparent1", "ACTIVE", true);
		ObjectBoundary parent = createObjectAs(operator, "Bin", "Parent1", "ACTIVE", true);
		ObjectBoundary child = createObjectAs(operator, "Bin", "Child1", "ACTIVE", true);
		bind(operator, grandparent, parent);
		bind(operator, parent, child);

		List<ObjectBoundary> grandparentChildren = this.objectApi.getChildren(
				grandparent.getObjectId().getSystemID(), grandparent.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(grandparentChildren).extracting(ObjectBoundary::getAlias).containsExactly("Parent1");
	}

	@Test
	void testThreeLevelChainMiddleObjectHasBothParentAndChild() {
		UserBoundary operator = createOperator("operatorChain2@example.com");
		ObjectBoundary grandparent = createObjectAs(operator, "Route", "Grandparent2", "ACTIVE", true);
		ObjectBoundary parent = createObjectAs(operator, "Bin", "Parent2", "ACTIVE", true);
		ObjectBoundary child = createObjectAs(operator, "Bin", "Child2", "ACTIVE", true);
		bind(operator, grandparent, parent);
		bind(operator, parent, child);

		List<ObjectBoundary> parentsOfMiddle = this.objectApi.getParents(
				parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);
		List<ObjectBoundary> childrenOfMiddle = this.objectApi.getChildren(
				parent.getObjectId().getSystemID(), parent.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(parentsOfMiddle).extracting(ObjectBoundary::getAlias).containsExactly("Grandparent2");
		assertThat(childrenOfMiddle).extracting(ObjectBoundary::getAlias).containsExactly("Child2");
	}

	@Test
	void testUnboundParentHasEmptyChildrenAndUnboundChildHasEmptyParents() {
		UserBoundary operator = createOperator("operatorUnbound@example.com");
		ObjectBoundary lonely = createObjectAs(operator, "Route", "Lonely", "ACTIVE", true);

		List<ObjectBoundary> children = this.objectApi.getChildren(
				lonely.getObjectId().getSystemID(), lonely.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);
		List<ObjectBoundary> parents = this.objectApi.getParents(
				lonely.getObjectId().getSystemID(), lonely.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(children).isEmpty();
		assertThat(parents).isEmpty();
	}

	@Test
	void testChildBoundUnderTwoParentsReturnsBothParents() {
		UserBoundary operator = createOperator("operatorTwoParents@example.com");
		ObjectBoundary parentA = createObjectAs(operator, "Route", "ParentA", "ACTIVE", true);
		ObjectBoundary parentB = createObjectAs(operator, "Route", "ParentB", "ACTIVE", true);
		ObjectBoundary child = createObjectAs(operator, "Bin", "SharedChild", "ACTIVE", true);
		bind(operator, parentA, child);
		bind(operator, parentB, child);

		List<ObjectBoundary> parents = this.objectApi.getParents(
				child.getObjectId().getSystemID(), child.getObjectId().getObjectId(),
				operator.getUserId().getSystemID(), operator.getUserId().getEmail(), TestConstants.DEFAULT_PASSWORD, 20, 0);

		assertThat(parents).extracting(ObjectBoundary::getAlias).containsExactlyInAnyOrder("ParentA", "ParentB");
	}

	@Test
	void testGetChildrenAsAdminForbidden() {
		assertStatus(403, () -> this.objectApi.getChildren(TestConstants.SYSTEM_ID, "any-parent",
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, 20, 0));
	}

	@Test
	void testGetParentsAsAdminForbidden() {
		assertStatus(403, () -> this.objectApi.getParents(TestConstants.SYSTEM_ID, "any-child",
				this.adminSystemID, TestConstants.ADMIN_EMAIL, TestConstants.ADMIN_PASSWORD, 20, 0));
	}
}
