package ambient_invisible_intelligence.boundaries;

import java.time.ZonedDateTime;
import java.util.Map;

import org.springframework.data.annotation.Id;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CommandBoundary {
	@Id
	private String internalId; // internal composite key, not exposed in JSON

	@JsonProperty("id")
	private CommandIdBoundary commandId;
	private String command;
	private TargetObjectIdBoundary targetObject;
	private ZonedDateTime invocationTimestamp;
	private UserRefBoundary invokedBy;
	private Map<String, Object> commandAttributes;

	public CommandBoundary() {
	}

	@JsonIgnore
	public String getInternalId() {
		return internalId;
	}

	public void setInternalId(String internalId) {
		this.internalId = internalId;
	}

	public CommandIdBoundary getCommandId() {
		return commandId;
	}

	public void setCommandId(CommandIdBoundary commandId) {
		this.commandId = commandId;
	}

	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
	}

	public TargetObjectIdBoundary getTargetObject() {
		return targetObject;
	}

	public void setTargetObject(TargetObjectIdBoundary targetObject) {
		this.targetObject = targetObject;
	}

	public ZonedDateTime getInvocationTimestamp() {
		return invocationTimestamp;
	}

	public void setInvocationTimestamp(ZonedDateTime invocationTimestamp) {
		this.invocationTimestamp = invocationTimestamp;
	}

	public UserRefBoundary getInvokedBy() {
		return invokedBy;
	}

	public void setInvokedBy(UserRefBoundary invokedBy) {
		this.invokedBy = invokedBy;
	}

	public Map<String, Object> getCommandAttributes() {
		return commandAttributes;
	}

	public void setCommandAttributes(Map<String, Object> commandAttributes) {
		this.commandAttributes = commandAttributes;
	}

	@Override
	public String toString() {
		return "{"
				+ "commandId:" + commandId
				+ ", command:" + command
				+ ", targetObject:" + targetObject
				+ ", invocationTimestamp:" + invocationTimestamp
				+ ", invokedBy:" + invokedBy
				+ ", commandAttributes:" + commandAttributes
				+ "}";
	}
}
