package ambient_invisible_intelligence.data;

import java.util.Date;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "commands")
public class CommandEntity {

	@Id
	private String id;

	private String systemID;
	private String command;

	@ManyToOne
	@JoinColumn(name = "targetObject", nullable = false)
	private ObjectEntity targetObject;

	private Date creationTimestamp;

	@ManyToOne
	@JoinColumn(name = "invokedBy", nullable = false)
	private UserEntity invokedBy;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> commandAttributes;

	public CommandEntity() {
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSystemID() {
		return systemID;
	}

	public void setSystemID(String systemID) {
		this.systemID = systemID;
	}

	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
	}

	public ObjectEntity getTargetObject() {
		return targetObject;
	}

	public void setTargetObject(ObjectEntity targetObject) {
		this.targetObject = targetObject;
	}

	public Date getCreationTimestamp() {
		return creationTimestamp;
	}

	public void setCreationTimestamp(Date creationTimestamp) {
		this.creationTimestamp = creationTimestamp;
	}

	public UserEntity getInvokedBy() {
		return invokedBy;
	}

	public void setInvokedBy(UserEntity invokedBy) {
		this.invokedBy = invokedBy;
	}

	public Map<String, Object> getCommandAttributes() {
		return commandAttributes;
	}

	public void setCommandAttributes(Map<String, Object> commandAttributes) {
		this.commandAttributes = commandAttributes;
	}
}
