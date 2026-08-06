package ambient_invisible_intelligence.boundaries;

import java.time.ZonedDateTime;
import java.util.Map;

import org.springframework.data.annotation.Id;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ObjectBoundary {
	@Id
	private String id; // internal composite key, not exposed in JSON

	@JsonProperty("id")
	private ObjectIdBoundary objectId;
	private String type;
	private String alias;
	private String status;
	private Boolean active;
	private ZonedDateTime creationTimestamp;
	private UserRefBoundary createdBy;
	private LocationBoundary location;
	private Map<String, Object> objectDetails;

	public ObjectBoundary() {
	}

	@JsonIgnore
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public ObjectIdBoundary getObjectId() {
		return objectId;
	}

	public void setObjectId(ObjectIdBoundary objectId) {
		this.objectId = objectId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getAlias() {
		return alias;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}
	
	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public ZonedDateTime getCreationTimestamp() {
		return creationTimestamp;
	}

	public void setCreationTimestamp(ZonedDateTime creationTimestamp) {
		this.creationTimestamp = creationTimestamp;
	}

	public LocationBoundary getLocation() {
		return location;
	}

	public void setLocation(LocationBoundary location) {
		this.location = location;
	}

	public UserRefBoundary getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(UserRefBoundary createdBy) {
		this.createdBy = createdBy;
	}

	public Map<String, Object> getObjectDetails() {
		return objectDetails;
	}

	public void setObjectDetails(Map<String, Object> objectDetails) {
		this.objectDetails = objectDetails;
	}

	@Override
	public String toString() {
		return "{"
				+ "id:" + id
				+ ", objectId:" + objectId
				+ ", type:" + type
				+ ", alias:" + alias
				+ ", status:" + status
				+ ", active:" + active
				+ ", creationTimestamp:" + creationTimestamp
				+ ", location:" + location
				+ ", createdBy:" + createdBy
				+ ", objectDetails:" + objectDetails
				+ "}";
	}
}
