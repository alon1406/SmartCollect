package ambient_invisible_intelligence;

import java.util.HashMap;
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
		for (int i = 0; i < 5; i++) {
			String plate = (11 + i) + "-" + (100 + i * 7) + "-" + (20 + i);
			String alias = "Truck " + (char) ('A' + i);
			objectsService.createObject(makeTruck(alias, plate), OPERATOR_PASSWORD);
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

			objectsService.createObject(makeBin(alias, lat, lng, type, 1100, address, fillLevel), OPERATOR_PASSWORD);
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
