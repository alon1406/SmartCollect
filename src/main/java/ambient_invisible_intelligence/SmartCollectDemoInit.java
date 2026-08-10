package ambient_invisible_intelligence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import ambient_invisible_intelligence.boundaries.LocationBoundary;
import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.boundaries.UserIdBoundary;
import ambient_invisible_intelligence.boundaries.UserRefBoundary;
import ambient_invisible_intelligence.logic.CommandsService;
import ambient_invisible_intelligence.logic.ObjectsService;
import ambient_invisible_intelligence.logic.UsersService;

@Component
@Profile("initDemoes")
public class SmartCollectDemoInit implements CommandLineRunner {

	private static final String SYSTEM_ID = "smartcollect";
	private static final String ADMIN_EMAIL = "admin@sc.com";
	private static final String ADMIN_PASSWORD = "Admin1!";
	private static final String OPERATOR_EMAIL = "op1@sc.com";
	private static final String OPERATOR_PASSWORD = "Oper1!7";
	private static final String DRIVER_PASSWORD = "Driver1!";

	private final UsersService usersService;
	private final ObjectsService objectsService;
	private final CommandsService commandsService;

	public SmartCollectDemoInit(UsersService usersService, ObjectsService objectsService,
			CommandsService commandsService) {
		this.usersService = usersService;
		this.objectsService = objectsService;
		this.commandsService = commandsService;
	}

	@Override
	public void run(String... args) throws Exception {

		// Bootstrap admin for cleanup (ignored if already exists)
		try {
			usersService.createUser(makeUser(ADMIN_EMAIL, "ADMIN", "System Admin", "A"), ADMIN_PASSWORD);
		} catch (Exception ignored) {
		}

		// Cleanup in safe order: commands → objects → users
		try {
			commandsService.deleteAllCommands(SYSTEM_ID, ADMIN_EMAIL, ADMIN_PASSWORD);
		} catch (Exception ignored) {
		}
		try {
			objectsService.deleteAllObjects(SYSTEM_ID, ADMIN_EMAIL, ADMIN_PASSWORD);
		} catch (Exception ignored) {
		}
		try {
			usersService.deleteAllUsers(SYSTEM_ID, ADMIN_EMAIL, ADMIN_PASSWORD);
		} catch (Exception ignored) {
		}

		// ── 1. USERS ─────────────────────────────────────────────────────────────

		usersService.createUser(makeUser(ADMIN_EMAIL, "ADMIN", "System Admin", "A"), ADMIN_PASSWORD);
		usersService.createUser(makeUser(OPERATOR_EMAIL, "OPERATOR", "Operator One", "O"), OPERATOR_PASSWORD);

		// 5 drivers — mirrors the DRIVERS array in admin.js seedDemoData()
		String[][] drivers = {
			{ "Dan Cohen",     "dan@sc.com"   },
			{ "Noa Levi",      "noa@sc.com"   },
			{ "Omer Bar",      "omer@sc.com"  },
			{ "Maya Katz",     "maya@sc.com"  },
			{ "Yossi Mizrahi", "yossi@sc.com" }
		};
		for (String[] d : drivers) {
			usersService.createUser(
					makeUser(d[1], "END_USER", d[0], String.valueOf(d[0].charAt(0))),
					DRIVER_PASSWORD);
		}

		// ── 2. OBJECTS ───────────────────────────────────────────────────────────

		// 5 trucks — mirrors the truck loop in admin.js seedDemoData()
		// plate formula: `${11+i}-${100+i*7}-${20+i}`, alias: Truck A..E
		List<ObjectBoundary> trucks = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			String plate = (11 + i) + "-" + (100 + i * 7) + "-" + (20 + i);
			String alias = "Truck " + (char) ('A' + i);
			trucks.add(objectsService.createObject(makeTruck(alias, plate), OPERATOR_PASSWORD));
		}

		// 70 bins — mirrors the STREETS / LOCATIONS loop in admin.js seedDemoData()
		String[] binTypes = { "general", "plastic", "paper", "glass", "organic" };
		String[] streetNames = {
			"Rothschild Blvd", "Dizengoff St",         "Ibn Gabirol St",        "Allenby St",
			"Ben Yehuda St",   "King George St",        "HaYarkon St",           "Bograshov St",
			"Shenkin St",      "Nachalat Binyamin St",  "Florentin St",          "Levinsky St",
			"Yehuda HaLevi St","Montefiore St",         "Ben Gurion Blvd",       "Arlozorov St",
			"Frishman St",     "Gordon St",             "Basel St",              "Weizmann St"
		};
		double[][] streetCoords = {
			{ 32.0660, 34.7720 }, { 32.0800, 34.7742 }, { 32.0830, 34.7830 }, { 32.0680, 34.7715 },
			{ 32.0820, 34.7685 }, { 32.0730, 34.7755 }, { 32.0840, 34.7670 }, { 32.0775, 34.7715 },
			{ 32.0685, 34.7735 }, { 32.0670, 34.7705 }, { 32.0575, 34.7690 }, { 32.0575, 34.7760 },
			{ 32.0680, 34.7770 }, { 32.0665, 34.7800 }, { 32.0865, 34.7720 }, { 32.0870, 34.7800 },
			{ 32.0805, 34.7695 }, { 32.0820, 34.7690 }, { 32.0895, 34.7830 }, { 32.0850, 34.7900 }
		};

		List<String> binIds = new ArrayList<>();
		for (int i = 0; i < 70; i++) {
			int si  = i % 20;
			int num = 1 + ((i * 7) % 180);                         // same formula as seed
			String type    = binTypes[i % 5];                       // same cycle as seed
			String capType = Character.toUpperCase(type.charAt(0)) + type.substring(1);
			String alias   = capType + " Bin " + (i + 1);          // e.g. "General Bin 1"
			String address = streetNames[si] + " " + num + ", Tel Aviv";
			double lat     = streetCoords[si][0];
			double lng     = streetCoords[si][1];
			int fillLevel  = (i * 13 + 17) % 96;                   // deterministic 0-95

			ObjectBoundary bin = objectsService.createObject(
					makeBin(alias, lat, lng, type, 1100, address, fillLevel), OPERATOR_PASSWORD);
			binIds.add(uuidOf(bin));
		}

		// ── 3. ROUTES ────────────────────────────────────────────────────────────
		// Routes are normally built by the operator in the UI. The seeder builds a
		// few up front so a fresh database can demonstrate the driver flow without
		// anyone having to assemble a route by hand first.
		//
		// Each route is left in "planned" state with nothing collected yet, so a
		// driver can run the whole collection sequence from the beginning.
		String[][] routePlan = {
			// alias                 driver email    driver name     truck  firstBin  count
			{ "Route A - Center",    "dan@sc.com",   "Dan Cohen",    "0",   "0",      "8" },
			{ "Route B - North",     "noa@sc.com",   "Noa Levi",     "1",   "8",      "7" },
			{ "Route C - South",     "omer@sc.com",  "Omer Bar",     "2",   "15",     "6" }
		};

		for (String[] plan : routePlan) {
			int truckIndex = Integer.parseInt(plan[3]);
			int firstBin   = Integer.parseInt(plan[4]);
			int binCount   = Integer.parseInt(plan[5]);

			List<String> routeBinIds = new ArrayList<>(binIds.subList(firstBin, firstBin + binCount));
			ObjectBoundary truck = trucks.get(truckIndex);
			String truckId = uuidOf(truck);

			ObjectBoundary route = objectsService.createObject(
					makeRoute(plan[0], routeBinIds, truckId, plan[1], plan[2]), OPERATOR_PASSWORD);
			String routeId = uuidOf(route);

			// Bind every bin as a child of the route. This is what makes the
			// BinCollected command work: applyBinCollected() locates the route
			// through bin.getParents(), not through the binIds list.
			for (String binId : routeBinIds) {
				objectsService.bindObjects(SYSTEM_ID, routeId, SYSTEM_ID, binId,
						SYSTEM_ID, OPERATOR_EMAIL, OPERATOR_PASSWORD);
			}

			// Mirror what handleSaveRoute() in operator.js does after creating a
			// route: the assigned truck moves out of the available pool.
			Map<String, Object> truckDetails = new HashMap<>(truck.getObjectDetails());
			truckDetails.put("status", "on_route");
			truckDetails.put("driverEmail", plan[1]);
			truckDetails.put("driverName", plan[2]);
			truckDetails.put("currentRouteId", routeId);

			ObjectBoundary truckUpdate = new ObjectBoundary();
			truckUpdate.setStatus("on_route");
			truckUpdate.setObjectDetails(truckDetails);
			objectsService.updateObject(SYSTEM_ID, truckId, truckUpdate,
					SYSTEM_ID, OPERATOR_EMAIL, OPERATOR_PASSWORD);
		}
	}

	// ── Helpers ──────────────────────────────────────────────────────────────────

	private UserBoundary makeUser(String email, String role, String username, String avatar) {
		UserBoundary user = new UserBoundary();
		UserIdBoundary uid = new UserIdBoundary();
		uid.setEmail(email);
		user.setUserId(uid);
		user.setRole(role);
		user.setUsername(username);
		user.setAvatar(avatar);
		return user;
	}

	private ObjectBoundary makeTruck(String alias, String plateNumber) {
		ObjectBoundary obj = new ObjectBoundary();
		obj.setType("TRUCK");
		obj.setAlias(alias);
		obj.setStatus("available");
		obj.setActive(true);
		obj.setLocation(location(32.114, 34.796));
		obj.setCreatedBy(createdBy(OPERATOR_EMAIL));
		Map<String, Object> details = new HashMap<>();
		details.put("plateNumber", plateNumber);
		details.put("driverEmail", null);
		details.put("driverName", null);
		details.put("status", "available");
		details.put("currentRouteId", null);
		details.put("collectedCount", 0);
		details.put("totalCapacity", 8000);
		details.put("currentLoad", 0);
		obj.setObjectDetails(details);
		return obj;
	}

	private ObjectBoundary makeBin(String alias, double lat, double lng,
			String binType, int capacity, String address, int fillLevel) {
		ObjectBoundary obj = new ObjectBoundary();
		obj.setType("BIN");
		obj.setAlias(alias);
		obj.setStatus("active");
		obj.setActive(true);
		obj.setLocation(location(lat, lng));
		obj.setCreatedBy(createdBy(OPERATOR_EMAIL));
		Map<String, Object> details = new HashMap<>();
		details.put("fillLevel", fillLevel);
		details.put("binType", binType);
		details.put("capacity", capacity);
		details.put("address", address);
		details.put("lastCollected", null);
		details.put("status", "active");
		details.put("errorStatus", null);
		obj.setObjectDetails(details);
		return obj;
	}

	/** Mirrors api.buildRoute() in objectService.js so seeded routes look exactly
	 *  like routes the operator creates through the UI. */
	private ObjectBoundary makeRoute(String alias, List<String> binIds, String truckId,
			String driverEmail, String driverName) {
		ObjectBoundary obj = new ObjectBoundary();
		obj.setType("ROUTE");
		obj.setAlias(alias);
		obj.setStatus("planned");
		obj.setActive(true);
		obj.setLocation(location(32.114, 34.796));
		obj.setCreatedBy(createdBy(OPERATOR_EMAIL));
		Map<String, Object> details = new HashMap<>();
		details.put("assignedTruckId", truckId);
		details.put("assignedDriverEmail", driverEmail);
		details.put("assignedDriverName", driverName);
		details.put("status", "planned");
		details.put("binIds", new ArrayList<>(binIds));
		details.put("completedBinIds", new ArrayList<>());
		details.put("startedAt", null);
		details.put("completedAt", null);
		obj.setObjectDetails(details);
		return obj;
	}

	/** Extracts the generated UUID from a boundary returned by createObject(). */
	private String uuidOf(ObjectBoundary boundary) {
		return boundary.getObjectId().getObjectId();
	}

	private LocationBoundary location(double lat, double lng) {
		LocationBoundary loc = new LocationBoundary();
		loc.setLat(lat);
		loc.setLng(lng);
		return loc;
	}

	private UserRefBoundary createdBy(String email) {
		UserRefBoundary ref = new UserRefBoundary();
		UserIdBoundary uid = new UserIdBoundary();
		uid.setSystemID(SYSTEM_ID);
		uid.setEmail(email);
		ref.setUserId(uid);
		return ref;
	}
}
