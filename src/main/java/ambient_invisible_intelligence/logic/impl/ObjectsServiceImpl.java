package ambient_invisible_intelligence.logic.impl;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import ambient_invisible_intelligence.boundaries.ObjectIdBoundary;
import ambient_invisible_intelligence.boundaries.UserIdBoundary;
import ambient_invisible_intelligence.boundaries.LocationBoundary;
import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.converters.ObjectConverter;
import ambient_invisible_intelligence.data.ObjectEntity;
import ambient_invisible_intelligence.data.UserEntity;
import ambient_invisible_intelligence.data.UserRole;
import ambient_invisible_intelligence.errors.BadRequestException;
import ambient_invisible_intelligence.errors.GoneException;
import ambient_invisible_intelligence.errors.NotFoundException;
import ambient_invisible_intelligence.logic.AuthService;
import ambient_invisible_intelligence.logic.ObjectsService;
import ambient_invisible_intelligence.repositories.ObjectRepository;

@Service
public class ObjectsServiceImpl implements ObjectsService {
	private ObjectRepository objectRepository;
	private ObjectConverter objectConverter;
	private AuthService authService;
	private String systemID;
	private Log logger = LogFactory.getLog(ObjectsServiceImpl.class);

	@PersistenceContext
	private EntityManager entityManager;

	public ObjectsServiceImpl(
			ObjectRepository objectRepository,
			ObjectConverter objectConverter,
			AuthService authService,
			@Value("${spring.application.name}") String systemID) {
		this.objectRepository = objectRepository;
		this.objectConverter = objectConverter;
		this.authService = authService;
		this.systemID = systemID;
	}

	@Override
	@Transactional(readOnly = false)
	public ObjectBoundary createObject(ObjectBoundary object, String userPassword) {
		if (object == null) {
			throw new BadRequestException("Object must not be null");
		}

		validateNotBlank(object.getType(), "Object type");
		validateNotBlank(object.getAlias(), "Object alias");
		validateNotBlank(object.getStatus(), "Object status");

		if (object.getActive() == null) {
			throw new BadRequestException("Object active must not be null");
		}
		if (object.getCreatedBy() == null) {
			throw new BadRequestException("Object createdBy must not be null");
		}

		UserIdBoundary createdByUserId = object.getCreatedBy().getUserId();
		if (createdByUserId == null) {
			throw new BadRequestException("Object createdBy userId must not be null");
		}

		String userSystemID = createdByUserId.getSystemID();
		String userEmail = createdByUserId.getEmail();

		// Only OPERATOR may create objects
		this.authService.requireOperator(userSystemID, userEmail, userPassword);

		// Generate a universally unique identifier (UUID) that persists across server restarts
		String generatedObjectId = UUID.randomUUID().toString();
		String internalId = this.systemID + "#" + generatedObjectId;

		ObjectIdBoundary objectIdObj = new ObjectIdBoundary();
		objectIdObj.setObjectId(generatedObjectId);
		objectIdObj.setSystemID(this.systemID);

		// Update the Boundary with server-generated data
		object.setId(internalId);
		object.setObjectId(objectIdObj);
		object.setCreationTimestamp(ZonedDateTime.now());

		if (object.getObjectDetails() == null) {
			object.setObjectDetails(new HashMap<>());
		}

		if (object.getLocation() == null) {
			object.setLocation(new LocationBoundary());
		}

		// Strict layer separation (Convert to Entity -> Save -> Convert to Boundary)
		ObjectEntity entity = this.objectConverter.toEntity(object);
		ObjectEntity savedEntity = this.objectRepository.save(entity);

		return this.objectConverter.toBoundary(savedEntity);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ObjectBoundary> getSpecificObject(String systemID, String objectId,
            String userSystemID, String userEmail, String userPassword) {
		if (systemID == null) {
			throw new BadRequestException("SystemID must not be null");
		}
		if (objectId == null) {
			throw new BadRequestException("ObjectID must not be null");
		}

		UserEntity user = this.authService.requireNonAdmin(userSystemID, userEmail, userPassword);

		String internalId = systemID + "#" + objectId;

		Optional<ObjectEntity> entityOpt = this.objectRepository.findById(internalId);
		if (entityOpt.isEmpty()) {
			return Optional.empty();
		}

		ObjectEntity entity = entityOpt.get();

		if (user.getRole() == UserRole.END_USER && !Boolean.TRUE.equals(entity.getActive())) {
			throw new NotFoundException("Object not found");
		}

		return Optional.of(this.objectConverter.toBoundary(entity));
	}

	@Override
	@Transactional(readOnly = false)
	public void updateObject(String systemID, String objectId, ObjectBoundary update,
            String userSystemID, String userEmail, String userPassword) {
		if (systemID == null) {
			throw new BadRequestException("SystemID must not be null");
		}
		if (objectId == null) {
			throw new BadRequestException("ObjectID must not be null");
		}
		if (update == null) {
			throw new BadRequestException("Update must not be null");
		}

		// Only OPERATOR may update objects
		this.authService.requireOperator(userSystemID, userEmail, userPassword);

		String internalId = systemID + "#" + objectId;

		// Retrieve the existing Entity from the DB
		Optional<ObjectEntity> existingEntityOptional = this.objectRepository.findById(internalId);

		if (existingEntityOptional.isEmpty()) {
			throw new NotFoundException("Object does not exist");
		}

		ObjectEntity entity = existingEntityOptional.get();

		// Update restrictions: update only the allowed fields within the Entity
		// Completely ignore attempts to modify: id, objectId, systemID, creationTimestamp, createdBy

		if (update.getType() != null) {
			validateNotBlank(update.getType(), "Object type");
			entity.setType(update.getType());
		}

		if (update.getAlias() != null) {
			validateNotBlank(update.getAlias(), "Object alias");
			entity.setAlias(update.getAlias());
		}

		if (update.getStatus() != null) {
			validateNotBlank(update.getStatus(), "Object status");
			entity.setStatus(update.getStatus());
		}

		if (update.getActive() != null) {
			entity.setActive(update.getActive());
		}

		if (update.getLocation() != null) {
			entity.setLat(update.getLocation().getLat());
			entity.setLng(update.getLocation().getLng());
		}

		if (update.getObjectDetails() != null) {
			entity.setMoreAttributes(update.getObjectDetails());
		}

		this.objectRepository.save(entity);
	}

	@Override
	@Deprecated
	public List<ObjectBoundary> getAllObjects(String userSystemID, String userEmail, String userPassword) {
		throw new GoneException("Version not supported. Please update to 1.4");
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> getAllObjects(String userSystemID, String userEmail,
            String userPassword, int size, int page) {
		UserEntity user = this.authService.requireNonAdmin(userSystemID, userEmail, userPassword);

		Pageable pageable = PageRequest.of(page, size, Sort.by("id").and(Sort.by("creationTimestamp")));

		if (user.getRole() == UserRole.END_USER) {
			return this.objectRepository.findAllByActive(true, pageable)
					.stream()
					.map(this.objectConverter::toBoundary)
					.peek(this.logger::trace)
					.toList();
		}

		return this.objectRepository.findAllBy(pageable)
				.stream()
				.map(this.objectConverter::toBoundary)
				.peek(this.logger::trace)
				.toList();
	}

	@Override
	@Transactional(readOnly = false)
	public void deleteAllObjects(String userSystemID, String userEmail, String userPassword){
		this.authService.requireAdmin(userSystemID, userEmail, userPassword);
		this.entityManager.createNativeQuery("DELETE FROM object_relations").executeUpdate();
		this.objectRepository.deleteAll();
	}

	@Override
	@Transactional(readOnly = false)
	public void bindObjects(String parentSystemID, String parentObjectId,
            String childSystemID, String childObjectId,
            String userSystemID, String userEmail, String userPassword) {
		if (parentSystemID == null || parentObjectId == null) {
			throw new BadRequestException("Parent systemID and objectId must not be null");
		}
		if (childSystemID == null || childObjectId == null) {
			throw new BadRequestException("Child systemID and objectId must not be null");
		}

		// Only OPERATOR may bind objects
		this.authService.requireOperator(userSystemID, userEmail, userPassword);

		String parentInternalId = parentSystemID + "#" + parentObjectId;
		String childInternalId = childSystemID + "#" + childObjectId;

		ObjectEntity parent = this.objectRepository.findById(parentInternalId)
				.orElseThrow(() -> new NotFoundException("Parent object does not exist"));

		ObjectEntity childEntity = this.objectRepository.findById(childInternalId)
				.orElseThrow(() -> new NotFoundException("Child object does not exist"));

		if (!parent.getChildren().contains(childEntity)) {
			parent.getChildren().add(childEntity);
			this.objectRepository.save(parent);
		}
	}

	@Override
	@Deprecated
	public List<ObjectBoundary> getChildren(String parentSystemID, String parentObjectId,
            String userSystemID, String userEmail, String userPassword) {
		throw new GoneException("Version not supported. Please update to 1.4");
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> getChildren(String parentSystemID, String parentObjectId,
            String userSystemID, String userEmail, String userPassword,
            int size, int page) {
		if (parentSystemID == null || parentObjectId == null) {
			throw new BadRequestException("Parent systemID and objectId must not be null");
		}

		UserEntity user = this.authService.requireNonAdmin(userSystemID, userEmail, userPassword);

		String parentInternalId = parentSystemID + "#" + parentObjectId;

		ObjectEntity parentEntity = this.objectRepository.findById(parentInternalId)
				.orElseThrow(() -> new NotFoundException("Parent object does not exist"));

		if (user.getRole() == UserRole.END_USER && !Boolean.TRUE.equals(parentEntity.getActive())) {
			throw new NotFoundException("Object not found");
		}

		Pageable pageable = PageRequest.of(page, size, Sort.by("id").and(Sort.by("creationTimestamp")));

		if (user.getRole() == UserRole.END_USER) {
			return this.objectRepository.findActiveChildrenByParentId(parentInternalId, pageable)
					.stream()
					.map(this.objectConverter::toBoundary)
					.toList();
		}

		return this.objectRepository.findChildrenByParentId(parentInternalId, pageable)
				.stream()
				.map(this.objectConverter::toBoundary)
				.toList();
	}

	@Override
	@Deprecated
	public List<ObjectBoundary> getParents(String childSystemID, String childObjectId,
            String userSystemID, String userEmail, String userPassword) {
		throw new GoneException("Version not supported. Please update to 1.4");
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> getParents(String childSystemID, String childObjectId,
            String userSystemID, String userEmail, String userPassword,
            int size, int page) {
		if (childSystemID == null || childObjectId == null) {
			throw new BadRequestException("Child systemID and objectId must not be null");
		}

		UserEntity user = this.authService.requireNonAdmin(userSystemID, userEmail, userPassword);

		String childInternalId = childSystemID + "#" + childObjectId;

		Pageable pageable = PageRequest.of(page, size, Sort.by("id").and(Sort.by("creationTimestamp")));

		if (user.getRole() == UserRole.END_USER) {
			ObjectEntity childEntity = this.objectRepository.findById(childInternalId)
					.orElseThrow(() -> new NotFoundException("Object not found"));

			if (!Boolean.TRUE.equals(childEntity.getActive())) {
				throw new NotFoundException("Object not found");
			}

			return this.objectRepository.findAllByChildren_IdAndActive(childInternalId, true, pageable)
					.stream()
					.map(this.objectConverter::toBoundary)
					.toList();
		}

		return this.objectRepository.findAllByChildren_Id(childInternalId, pageable)
				.stream()
				.map(this.objectConverter::toBoundary)
				.toList();
	}



	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> searchByAlias(String alias,
			String userSystemID, String userEmail, String userPassword,
			int size, int page) {
		UserEntity user = this.authService.requireNonAdmin(userSystemID, userEmail, userPassword);

		Pageable pageable = PageRequest.of(page, size, Sort.by("id").and(Sort.by("creationTimestamp")));

		if (user.getRole() == UserRole.END_USER) {
			return this.objectRepository.findAllByAliasAndActive(alias, true, pageable)
					.stream()
					.map(this.objectConverter::toBoundary)
					.toList();
		}

		return this.objectRepository.findAllByAlias(alias, pageable)
				.stream()
				.map(this.objectConverter::toBoundary)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> searchByAliasPattern(String pattern,
			String userSystemID, String userEmail, String userPassword,
			int size, int page) {
		UserEntity user = this.authService.requireNonAdmin(userSystemID, userEmail, userPassword);

		Pageable pageable = PageRequest.of(page, size, Sort.by("id").and(Sort.by("creationTimestamp")));

		if (user.getRole() == UserRole.END_USER) {
			return this.objectRepository.findAllByAliasLikeAndActive("%" + pattern + "%", true, pageable)
					.stream()
					.map(this.objectConverter::toBoundary)
					.toList();
		}

		return this.objectRepository.findAllByAliasLike("%" + pattern + "%", pageable)
				.stream()
				.map(this.objectConverter::toBoundary)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> searchByType(String type,
			String userSystemID, String userEmail, String userPassword,
			int size, int page) {
		UserEntity user = this.authService.requireNonAdmin(userSystemID, userEmail, userPassword);

		Pageable pageable = PageRequest.of(page, size, Sort.by("id").and(Sort.by("creationTimestamp")));

		if (user.getRole() == UserRole.END_USER) {
			return this.objectRepository.findAllByTypeAndActive(type, true, pageable)
					.stream()
					.map(this.objectConverter::toBoundary)
					.toList();
		}

		return this.objectRepository.findAllByType(type, pageable)
				.stream()
				.map(this.objectConverter::toBoundary)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> searchByStatus(String status,
			String userSystemID, String userEmail, String userPassword,
			int size, int page) {
		UserEntity user = this.authService.requireNonAdmin(userSystemID, userEmail, userPassword);

		Pageable pageable = PageRequest.of(page, size, Sort.by("id").and(Sort.by("creationTimestamp")));

		if (user.getRole() == UserRole.END_USER) {
			return this.objectRepository.findAllByStatusAndActive(status, true, pageable)
					.stream()
					.map(this.objectConverter::toBoundary)
					.toList();
		}

		return this.objectRepository.findAllByStatus(status, pageable)
				.stream()
				.map(this.objectConverter::toBoundary)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> searchByLocation(double lat, double lng, double distance, String units,
			String userSystemID, String userEmail, String userPassword,
			int size, int page) {
		UserEntity user = this.authService.requireNonAdmin(userSystemID, userEmail, userPassword);

		double deltaLat;
		double deltaLng;
		String u = (units == null) ? "NEUTRAL" : units.toUpperCase();
		switch (u) {
			case "KM":
				deltaLat = distance / 111.0;
				deltaLng = distance / (111.0 * Math.cos(Math.toRadians(lat)));
				break;
			case "MILES":
				double km = distance * 1.60934;
				deltaLat = km / 111.0;
				deltaLng = km / (111.0 * Math.cos(Math.toRadians(lat)));
				break;
			default:
				deltaLat = distance;
				deltaLng = distance;
				break;
		}
		Pageable pageable = PageRequest.of(page, size, Sort.by("id").and(Sort.by("creationTimestamp")));

		if (user.getRole() == UserRole.END_USER) {
			return this.objectRepository.findActiveByLocationSquare(
					lat - deltaLat, lat + deltaLat,
					lng - deltaLng, lng + deltaLng,
					pageable)
					.stream()
					.map(this.objectConverter::toBoundary)
					.toList();
		}

		return this.objectRepository.findAllByLocationSquare(
				lat - deltaLat, lat + deltaLat,
				lng - deltaLng, lng + deltaLng,
				pageable)
				.stream()
				.map(this.objectConverter::toBoundary)
				.toList();
	}

	private void validateNotBlank(String value, String fieldName) {
		if (value == null || value.trim().isEmpty()) {
			throw new BadRequestException(fieldName + " must not be null or empty");
		}
	}
}
