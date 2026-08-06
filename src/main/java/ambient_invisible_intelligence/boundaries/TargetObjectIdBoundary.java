package ambient_invisible_intelligence.boundaries;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TargetObjectIdBoundary {
    @JsonProperty("id")
    private ObjectIdBoundary objectId;

    public TargetObjectIdBoundary() {}

    public ObjectIdBoundary getObjectId() { return objectId; }
    public void setObjectId(ObjectIdBoundary objectId) { this.objectId = objectId; }
}
