package ambient_invisible_intelligence.logic;

import java.util.List;
import java.util.Optional;

import ambient_invisible_intelligence.boundaries.ObjectBoundary;

public interface ObjectsService {
	public ObjectBoundary createObject(ObjectBoundary object, String userPassword);

    public void updateObject(String systemID, String objectId, ObjectBoundary update,
                      String userSystemID, String userEmail, String userPassword);

    public Optional<ObjectBoundary> getSpecificObject(String systemID, String objectId,
                                               String userSystemID, String userEmail, String userPassword);

    @Deprecated
    public List<ObjectBoundary> getAllObjects(String userSystemID, String userEmail, String userPassword);

    public List<ObjectBoundary> getAllObjects(String userSystemID, String userEmail, String userPassword,
                                      int size, int page);

    public void deleteAllObjects(String userSystemID, String userEmail, String userPassword);

    public void bindObjects(String parentSystemID, String parentObjectId,
                     String childSystemID, String childObjectId,
                     String userSystemID, String userEmail, String userPassword);

    @Deprecated
    public List<ObjectBoundary> getChildren(String parentSystemID, String parentObjectId,
                                     String userSystemID, String userEmail, String userPassword);

    public List<ObjectBoundary> getChildren(String parentSystemID, String parentObjectId,
                                     String userSystemID, String userEmail, String userPassword,
                                     int size, int page);

    @Deprecated
    public List<ObjectBoundary> getParents(String childSystemID, String childObjectId,
                                    String userSystemID, String userEmail, String userPassword);

    public List<ObjectBoundary> getParents(String childSystemID, String childObjectId,
                                    String userSystemID, String userEmail, String userPassword,
                                    int size, int page);

    public List<ObjectBoundary> searchByAlias(String alias,
                                       String userSystemID, String userEmail, String userPassword,
                                       int size, int page);

    public List<ObjectBoundary> searchByAliasPattern(String pattern,
                                              String userSystemID, String userEmail, String userPassword,
                                              int size, int page);

    public List<ObjectBoundary> searchByType(String type,
                                      String userSystemID, String userEmail, String userPassword,
                                      int size, int page);

    public List<ObjectBoundary> searchByStatus(String status,
                                        String userSystemID, String userEmail, String userPassword,
                                        int size, int page);

    public List<ObjectBoundary> searchByLocation(double lat, double lng, double distance, String units,
                                          String userSystemID, String userEmail, String userPassword,
                                          int size, int page);
}
