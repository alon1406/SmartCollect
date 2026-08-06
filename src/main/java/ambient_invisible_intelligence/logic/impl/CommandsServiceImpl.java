package ambient_invisible_intelligence.logic.impl;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ambient_invisible_intelligence.boundaries.UserIdBoundary;
import ambient_invisible_intelligence.boundaries.ObjectIdBoundary;
import ambient_invisible_intelligence.boundaries.CommandIdBoundary;
import ambient_invisible_intelligence.boundaries.CommandBoundary;
import ambient_invisible_intelligence.converters.CommandConverter;
import ambient_invisible_intelligence.data.CommandEntity;
import ambient_invisible_intelligence.data.ObjectEntity;
import ambient_invisible_intelligence.data.UserEntity;
import ambient_invisible_intelligence.errors.BadRequestException;
import ambient_invisible_intelligence.errors.GoneException;
import ambient_invisible_intelligence.errors.NotFoundException;
import ambient_invisible_intelligence.logic.AuthService;
import ambient_invisible_intelligence.logic.CommandsService;
import ambient_invisible_intelligence.repositories.CommandRepository;
import ambient_invisible_intelligence.repositories.ObjectRepository;

@Service
public class CommandsServiceImpl implements CommandsService {
	private CommandRepository commandRepository;
	private CommandConverter commandConverter;
	private ObjectRepository objectRepository;
	private AuthService authService;
	private String systemID;
	private AtomicLong commandCounter;

	public CommandsServiceImpl(
			CommandRepository commandRepository,
			CommandConverter commandConverter,
			ObjectRepository objectRepository,
			AuthService authService,
			@Value("${spring.application.name}") String systemID) {
		this.commandRepository = commandRepository;
		this.commandConverter = commandConverter;
		this.objectRepository = objectRepository;
		this.authService = authService;
		this.systemID = systemID;
		this.commandCounter = new AtomicLong(1);
	}

	@Override
	@Transactional
	public List<Object> invokeCommand(CommandBoundary command, String userPassword) {
		if (command == null) {
			throw new BadRequestException("Command must not be null");
		}

		validateNotBlank(command.getCommand(), "Command name");

		// Validate invokedBy structure
		if (command.getInvokedBy() == null) {
			throw new BadRequestException("invokedBy must not be null");
		}

		UserIdBoundary userIdObj = command.getInvokedBy().getUserId();
		if (userIdObj == null) {
			throw new BadRequestException("invokedBy.userId must not be null");
		}

		// Validate targetObject structure
		if (command.getTargetObject() == null) {
			throw new BadRequestException("targetObject must not be null");
		}

		ObjectIdBoundary targetObjId = command.getTargetObject().getObjectId();
		if (targetObjId == null) {
			throw new BadRequestException("targetObject.objectId must not be null");
		}

		// Only END_USER may invoke commands (ADMIN and OPERATOR are blocked)
		String userSystemID = userIdObj.getSystemID();
		String userEmail = userIdObj.getEmail();

		UserEntity userEntity = this.authService.requireEndUser(userSystemID, userEmail, userPassword);

		// Build object internal ID and fetch entity
		String objectSystemID = targetObjId.getSystemID();
		String objectId = targetObjId.getObjectId();
		String objectInternalId = objectSystemID + "#" + objectId;

		ObjectEntity objectEntity = this.objectRepository.findById(objectInternalId)
				.orElseThrow(() -> new NotFoundException("Object " + objectInternalId + " does not exist"));

		// END_USER may only invoke commands on active objects
		if (!Boolean.TRUE.equals(objectEntity.getActive())) {
			throw new NotFoundException("Object not found");
		}

		// Apply the side effects of this command on the related objects.
		applyCommandEffects(command.getCommand(), objectEntity, command.getCommandAttributes());

		// Generate command ID and save
		String generatedCommandId = "" + this.commandCounter.getAndIncrement();

		CommandIdBoundary cmdId = new CommandIdBoundary();
		cmdId.setCommandId(generatedCommandId);
		cmdId.setSystemID(this.systemID);

		command.setCommandId(cmdId);

		String internalId = this.systemID + "#" + generatedCommandId;
		command.setInternalId(internalId);

		command.setInvocationTimestamp(ZonedDateTime.now());

		CommandEntity entity = this.commandConverter.toEntity(command);
		entity.setInvokedBy(userEntity);
		entity.setTargetObject(objectEntity);
		this.commandRepository.save(entity);

		return List.of(command);
	}

	@Override
	@Deprecated
	public List<CommandBoundary> getAllCommandsHistory(String userSystemID, String userEmail, String userPassword) {
		throw new GoneException("Version not supported. Please update to 1.4");
	}

	@Override
	public List<CommandBoundary> getAllCommandsHistory(String userSystemID, String userEmail,
	        String userPassword, int size, int page) {
		this.authService.requireAdmin(userSystemID, userEmail, userPassword);

		return this.commandRepository.findAllBy(PageRequest.of(page, size, Sort.by("id").and(Sort.by("creationTimestamp"))))
				.stream()
				.map(this.commandConverter::toBoundary)
				.toList();
	}

	@Override
	public void deleteAllCommands(String userSystemID, String userEmail, String userPassword) {
		this.authService.requireAdmin(userSystemID, userEmail, userPassword);
		this.commandRepository.deleteAll();
	}
	
	
	private void validateNotBlank(String value, String fieldName) {
		if (value == null || value.trim().isEmpty()) {
			throw new BadRequestException(fieldName + " must not be null or empty");
		}
	}

	private void applyCommandEffects(String commandName, ObjectEntity target, Map<String, Object> attrs) {
		switch (commandName) {
			case "BinCollected" -> applyBinCollected(target, attrs);
			case "BinErrorReported" -> applyBinErrorReported(target, attrs);
			case "TruckArrivedAtDepot" -> applyTruckArrivedAtDepot(target, attrs);
			default -> { /* unknown command: no side effects, just logged */ }
		}
	}

	private void applyBinCollected(ObjectEntity bin, Map<String, Object> attrs) {
		Object collectedAt = attrs != null ? attrs.get("collectedAt") : null;
		Object collectedWeightObj = attrs != null ? attrs.get("collectedWeight") : null;
		double collectedWeight = collectedWeightObj instanceof Number n ? n.doubleValue() : 0;

		// Update the bin
		Map<String, Object> binAttrs = new HashMap<>(bin.getMoreAttributes());
		binAttrs.put("fillLevel", 0);
		binAttrs.put("lastCollected", collectedAt);
		binAttrs.put("errorStatus", null);
		bin.setMoreAttributes(binAttrs);
		this.objectRepository.save(bin);

		// Find the bin's route (the route is a parent of the bin)
		ObjectEntity route = findParentByType(bin, "ROUTE");
		if (route == null) {
			throw new BadRequestException("Bin is not bound to any route");
		}
		String binUUID = uuidOf(bin);
		Map<String, Object> routeAttrs = new HashMap<>(route.getMoreAttributes());
		@SuppressWarnings("unchecked")
		List<Object> completedBinIds = new java.util.ArrayList<>(
				(List<Object>) routeAttrs.getOrDefault("completedBinIds", new java.util.ArrayList<>()));
		if (!completedBinIds.contains(binUUID)) {
			completedBinIds.add(binUUID);
		}
		routeAttrs.put("completedBinIds", completedBinIds);
		route.setMoreAttributes(routeAttrs);
		this.objectRepository.save(route);

		// Find the route's truck (by assignedTruckId)
		Object truckIdObj = routeAttrs.get("assignedTruckId");
		if (truckIdObj == null) {
			throw new BadRequestException("Route has no assigned truck");
		}
		ObjectEntity truck = this.objectRepository.findById(this.systemID + "#" + truckIdObj)
				.orElseThrow(() -> new BadRequestException("Assigned truck does not exist"));
		Map<String, Object> truckAttrs = new HashMap<>(truck.getMoreAttributes());
		double currentLoad = numberOf(truckAttrs.get("currentLoad"));
		long collectedCount = (long) numberOf(truckAttrs.get("collectedCount"));
		truckAttrs.put("currentLoad", currentLoad + collectedWeight);
		truckAttrs.put("collectedCount", collectedCount + 1);
		truck.setMoreAttributes(truckAttrs);
		this.objectRepository.save(truck);
	}

	private void applyBinErrorReported(ObjectEntity bin, Map<String, Object> attrs) {
		Object errorType = attrs != null ? attrs.get("errorType") : null;

		Map<String, Object> binAttrs = new HashMap<>(bin.getMoreAttributes());
		binAttrs.put("errorStatus", errorType);
		binAttrs.put("status", "maintenance");
		bin.setMoreAttributes(binAttrs);
		this.objectRepository.save(bin);

		ObjectEntity route = findParentByType(bin, "ROUTE");
		if (route == null) {
			throw new BadRequestException("Bin is not bound to any route");
		}
		String binUUID = uuidOf(bin);
		Map<String, Object> routeAttrs = new HashMap<>(route.getMoreAttributes());
		routeAttrs.put("binIds", removeFromList(routeAttrs.get("binIds"), binUUID));
		routeAttrs.put("completedBinIds", removeFromList(routeAttrs.get("completedBinIds"), binUUID));
		route.setMoreAttributes(routeAttrs);
		this.objectRepository.save(route);
	}

	private void applyTruckArrivedAtDepot(ObjectEntity target, Map<String, Object> attrs) {
		Object arrivedAt = attrs != null ? attrs.get("arrivedAt") : null;

		ObjectEntity route;
		ObjectEntity truck;
		if ("ROUTE".equals(target.getType())) {
			route = target;
			Object truckIdObj = route.getMoreAttributes().get("assignedTruckId");
			truck = truckIdObj == null ? null
					: this.objectRepository.findById(this.systemID + "#" + truckIdObj).orElse(null);
		} else {
			truck = target;
			route = findRouteByTruck(truck);
		}

		if (route != null) {
			Map<String, Object> routeAttrs = new HashMap<>(route.getMoreAttributes());
			routeAttrs.put("status", "returning");
			routeAttrs.put("completedAt", arrivedAt);
			route.setMoreAttributes(routeAttrs);
			this.objectRepository.save(route);
		}

		if (truck != null) {
			Map<String, Object> truckAttrs = new HashMap<>(truck.getMoreAttributes());
			truckAttrs.put("status", "returning");
			truck.setMoreAttributes(truckAttrs);
			this.objectRepository.save(truck);
		}
	}

// ── helpers ──────────────────────────────────────────────────────────────

	private ObjectEntity findParentByType(ObjectEntity child, String type) {
		return child.getParents().stream()
				.filter(p -> type.equals(p.getType()))
				.findFirst()
				.orElse(null);
	}

	private ObjectEntity findRouteByTruck(ObjectEntity truck) {
		String truckUUID = uuidOf(truck);
		return this.objectRepository.findAll().stream()
				.filter(o -> "ROUTE".equals(o.getType()))
				.filter(o -> truckUUID.equals(String.valueOf(o.getMoreAttributes().get("assignedTruckId"))))
				.findFirst()
				.orElse(null);
	}

	private String uuidOf(ObjectEntity entity) {
		String id = entity.getId();
		return id != null && id.contains("#") ? id.split("#")[1] : id;
	}

	@SuppressWarnings("unchecked")
	private List<Object> removeFromList(Object listObj, Object valueToRemove) {
		List<Object> list = new java.util.ArrayList<>(
				listObj instanceof List ? (List<Object>) listObj : List.of());
		list.remove(valueToRemove);
		return list;
	}

	private double numberOf(Object value) {
		return value instanceof Number n ? n.doubleValue() : 0;
	}
}
