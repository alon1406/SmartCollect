package ambient_invisible_intelligence.data;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "objects")
public class ObjectEntity {

	@Id
	private String id;

	private String systemID;
	private String type;
	private String alias;
	private String status;
	private Boolean active;
	private Double lat;
	private Double lng;
	private Date creationTimestamp;

	@ManyToOne
	@JoinColumn(name = "createdBy", nullable = false)
	private UserEntity createdBy;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> moreAttributes;

	@ManyToMany
	@JoinTable(
		name = "object_relations",
		joinColumns = @JoinColumn(name = "parentObject"),
		inverseJoinColumns = @JoinColumn(name = "childObject")
	)
	private Set<ObjectEntity> children = new HashSet<>();

	@ManyToMany(mappedBy = "children")
	private Set<ObjectEntity> parents = new HashSet<>();

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

	public Double getLat() {
		return lat;
	}

	public void setLat(Double lat) {
		this.lat = lat;
	}

	public Double getLng() {
		return lng;
	}

	public void setLng(Double lng) {
		this.lng = lng;
	}

	public Date getCreationTimestamp() {
		return creationTimestamp;
	}

	public void setCreationTimestamp(Date creationTimestamp) {
		this.creationTimestamp = creationTimestamp;
	}

	public UserEntity getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(UserEntity createdBy) {
		this.createdBy = createdBy;
	}

	public Map<String, Object> getMoreAttributes() {
		return moreAttributes;
	}

	public void setMoreAttributes(Map<String, Object> moreAttributes) {
		this.moreAttributes = moreAttributes;
	}

	public Set<ObjectEntity> getChildren() {
		return children;
	}

	public void setChildren(Set<ObjectEntity> children) {
		this.children = children;
	}

	public Set<ObjectEntity> getParents() {
		return parents;
	}

	public void setParents(Set<ObjectEntity> parents) {
		this.parents = parents;
	}
}
