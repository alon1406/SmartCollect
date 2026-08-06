package ambient_invisible_intelligence.converters;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import org.springframework.stereotype.Component;

import ambient_invisible_intelligence.boundaries.ObjectIdBoundary;
import ambient_invisible_intelligence.boundaries.LocationBoundary;
import ambient_invisible_intelligence.boundaries.UserRefBoundary;
import ambient_invisible_intelligence.boundaries.UserIdBoundary;
import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.data.ObjectEntity;
import ambient_invisible_intelligence.data.UserEntity;

@Component
public class ObjectConverter {

	// Convert from DB (Entity) to external representation (Boundary)
	public ObjectBoundary toBoundary(ObjectEntity entity) {
		ObjectBoundary boundary = new ObjectBoundary();
		boundary.setId(entity.getId());
		boundary.setType(entity.getType());
		boundary.setAlias(entity.getAlias());
		boundary.setStatus(entity.getStatus());
		boundary.setActive(entity.getActive());
		boundary.setObjectDetails(entity.getMoreAttributes());
		ObjectIdBoundary objectId = new ObjectIdBoundary();
		objectId.setSystemID(entity.getSystemID());
		String internalId = entity.getId();
		if (internalId != null && internalId.contains("#")) {
			objectId.setObjectId(internalId.split("#")[1]);
		}
		boundary.setObjectId(objectId);
		LocationBoundary location = new LocationBoundary();
		location.setLat(entity.getLat());
		location.setLng(entity.getLng());
		boundary.setLocation(location);
		if (entity.getCreatedBy() != null) {
			UserRefBoundary createdBy = new UserRefBoundary();
			UserIdBoundary createdByUserId = new UserIdBoundary();
			createdByUserId.setSystemID(entity.getCreatedBy().getSystemID());
			createdByUserId.setEmail(entity.getCreatedBy().getEmail());
			createdBy.setUserId(createdByUserId);
			boundary.setCreatedBy(createdBy);
		}
		if (entity.getCreationTimestamp() != null) {
			boundary.setCreationTimestamp(
					ZonedDateTime.ofInstant(
							entity.getCreationTimestamp().toInstant(),
							ZoneId.systemDefault()
					)
			);
		}
		return boundary;
	}

	// Convert from external representation (Boundary) to DB (Entity)
	public ObjectEntity toEntity(ObjectBoundary boundary) {
		ObjectEntity entity = new ObjectEntity();
		entity.setId(boundary.getId());
		entity.setType(boundary.getType());
		entity.setAlias(boundary.getAlias());
		entity.setStatus(boundary.getStatus());
		entity.setActive(boundary.getActive());
		entity.setMoreAttributes(boundary.getObjectDetails());
		if (boundary.getObjectId() != null) {
			entity.setSystemID(boundary.getObjectId().getSystemID());
		}
		if (boundary.getLocation() != null) {
			entity.setLat(boundary.getLocation().getLat());
			entity.setLng(boundary.getLocation().getLng());
		}
		if (boundary.getCreationTimestamp() != null) {
			entity.setCreationTimestamp(
					Date.from(boundary.getCreationTimestamp().toInstant())
			);
		}

		if (boundary.getCreatedBy() != null && boundary.getCreatedBy().getUserId() != null) {
			UserIdBoundary userId = boundary.getCreatedBy().getUserId();
			if (userId.getSystemID() != null && userId.getEmail() != null) {
				UserEntity createdBy = new UserEntity();
				createdBy.setId(userId.getSystemID() + "#" + userId.getEmail());
				entity.setCreatedBy(createdBy);
			}
		}

		return entity;
	}
}
