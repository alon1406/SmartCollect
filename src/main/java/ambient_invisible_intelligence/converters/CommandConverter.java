package ambient_invisible_intelligence.converters;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import org.springframework.stereotype.Component;

import ambient_invisible_intelligence.boundaries.CommandIdBoundary;
import ambient_invisible_intelligence.boundaries.TargetObjectIdBoundary;
import ambient_invisible_intelligence.boundaries.ObjectIdBoundary;
import ambient_invisible_intelligence.boundaries.UserRefBoundary;
import ambient_invisible_intelligence.boundaries.UserIdBoundary;
import ambient_invisible_intelligence.boundaries.CommandBoundary;
import ambient_invisible_intelligence.data.CommandEntity;

@Component
public class CommandConverter {

	public CommandBoundary toBoundary(CommandEntity entity) {
		CommandBoundary boundary = new CommandBoundary();

		boundary.setInternalId(entity.getId());
		boundary.setCommand(entity.getCommand());
		boundary.setCommandAttributes(entity.getCommandAttributes());

		CommandIdBoundary commandId = new CommandIdBoundary();
		commandId.setSystemID(entity.getSystemID());
		String internalId = entity.getId();
		if (internalId != null && internalId.contains("#")) {
			commandId.setCommandId(internalId.split("#")[1]);
		}
		boundary.setCommandId(commandId);

		if (entity.getTargetObject() != null) {
			TargetObjectIdBoundary target = new TargetObjectIdBoundary();
			ObjectIdBoundary targetObjId = new ObjectIdBoundary();
			targetObjId.setSystemID(entity.getTargetObject().getSystemID());
			String targetInternalId = entity.getTargetObject().getId();
			if (targetInternalId != null && targetInternalId.contains("#")) {
				targetObjId.setObjectId(targetInternalId.split("#")[1]);
			}
			target.setObjectId(targetObjId);
			boundary.setTargetObject(target);
		}

		if (entity.getInvokedBy() != null) {
			UserRefBoundary invokedBy = new UserRefBoundary();
			UserIdBoundary userId = new UserIdBoundary();
			userId.setSystemID(entity.getInvokedBy().getSystemID());
			userId.setEmail(entity.getInvokedBy().getEmail());
			invokedBy.setUserId(userId);
			boundary.setInvokedBy(invokedBy);
		}

		if (entity.getCreationTimestamp() != null) {
			boundary.setInvocationTimestamp(
					ZonedDateTime.ofInstant(
							entity.getCreationTimestamp().toInstant(),
							ZoneId.systemDefault()
					)
			);
		}

		return boundary;
	}

	public CommandEntity toEntity(CommandBoundary boundary) {
		CommandEntity entity = new CommandEntity();
		entity.setId(boundary.getInternalId());
		entity.setCommand(boundary.getCommand());
		entity.setCommandAttributes(boundary.getCommandAttributes());
		if (boundary.getCommandId() != null) {
			entity.setSystemID(boundary.getCommandId().getSystemID());
		}
		if (boundary.getInvocationTimestamp() != null) {
			entity.setCreationTimestamp(
					Date.from(boundary.getInvocationTimestamp().toInstant())
			);
		}
		return entity;
	}
}
