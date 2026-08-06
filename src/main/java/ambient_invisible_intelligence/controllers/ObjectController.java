package ambient_invisible_intelligence.controllers;


import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.ObjectChildIdBoundary;
import ambient_invisible_intelligence.errors.NotFoundException;
import ambient_invisible_intelligence.logic.ObjectsService;

@RestController
@RequestMapping(path = {"/ambient-invisible-intelligence/objects"}, version = "1.4+")
public class ObjectController {

	private ObjectsService objectService;

	public ObjectController(ObjectsService objectService) {
		this.objectService = objectService;
	}

	@PostMapping(
			consumes = {MediaType.APPLICATION_JSON_VALUE},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public ObjectBoundary createObject(
	        @RequestParam(name = "userPassword", required = true) String userPassword,
	        @RequestBody ObjectBoundary object) {
	    return this.objectService.createObject(object, userPassword);
	}

	@PutMapping(
			path = {"/{systemID}/{objectId}"},
			consumes = {MediaType.APPLICATION_JSON_VALUE})
	public void updateObject(
	        @PathVariable("systemID") String systemID,
	        @PathVariable("objectId") String objectId,
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true)  String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword,
	        @RequestBody ObjectBoundary update) {
	    this.objectService.updateObject(systemID, objectId, update, userSystemID, userEmail, userPassword);
	}

	@GetMapping(
			path = {"/{systemID}/{objectId}"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public ObjectBoundary getObject(
	        @PathVariable("systemID") String systemID,
	        @PathVariable("objectId") String objectId,
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true)  String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword) {
	    return this.objectService.getSpecificObject(systemID, objectId, userSystemID, userEmail, userPassword)
	            .orElseThrow(() -> new NotFoundException("Object does not exist"));
	}

	@GetMapping(
			params = {"size", "page"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public ObjectBoundary[] getAllObjects(
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true)  String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword,
	        @RequestParam(name = "size") int size,
	        @RequestParam(name = "page") int page) {
	    return this.objectService.getAllObjects(userSystemID, userEmail, userPassword, size, page).toArray(new ObjectBoundary[0]);
	}

	@Deprecated
	@GetMapping(
			params = {"!size", "!page"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public ObjectBoundary[] getAllObjectsDeprecated(
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true)  String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword) {
	    return this.objectService.getAllObjects(userSystemID, userEmail, userPassword).toArray(new ObjectBoundary[0]);
	}

	@PutMapping(
			path = {"/{parentSystemID}/{parentObjectId}/children"},
			consumes = {MediaType.APPLICATION_JSON_VALUE})
	public void bindObjects(
	        @PathVariable("parentSystemID") String parentSystemID,
	        @PathVariable("parentObjectId") String parentObjectId,
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true)  String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword,
	        @RequestBody ObjectChildIdBoundary child) {
		String childSystemID = child.getChildId().getSystemID();
		String childObjectId = child.getChildId().getObjectId();
	    this.objectService.bindObjects(parentSystemID, parentObjectId, childSystemID, childObjectId,
	            userSystemID, userEmail, userPassword);
	}
	
	@GetMapping(
			path = {"/{parentSystemID}/{parentObjectId}/children"},
			params = {"size", "page"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public ObjectBoundary[] getChildren(
	        @PathVariable("parentSystemID") String parentSystemID,
	        @PathVariable("parentObjectId") String parentObjectId,
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true)  String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword,
	        @RequestParam(name = "size") int size,
	        @RequestParam(name = "page") int page) {
	    return this.objectService.getChildren(parentSystemID, parentObjectId,
	            userSystemID, userEmail, userPassword, size, page).toArray(new ObjectBoundary[0]);
	}

	@Deprecated
	@GetMapping(
			path = {"/{parentSystemID}/{parentObjectId}/children"},
			params = {"!size", "!page"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public ObjectBoundary[] getChildrenDeprecated(
	        @PathVariable("parentSystemID") String parentSystemID,
	        @PathVariable("parentObjectId") String parentObjectId,
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true)  String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword) {
	    return this.objectService.getChildren(parentSystemID, parentObjectId,
	            userSystemID, userEmail, userPassword).toArray(new ObjectBoundary[0]);
	}

	@GetMapping(
			path = {"/{childSystemID}/{childObjectId}/parents"},
			params = {"size", "page"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public ObjectBoundary[] getParents(
	        @PathVariable("childSystemID") String childSystemID,
	        @PathVariable("childObjectId") String childObjectId,
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true)  String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword,
	        @RequestParam(name = "size") int size,
	        @RequestParam(name = "page") int page) {
	    return this.objectService.getParents(childSystemID, childObjectId,
	            userSystemID, userEmail, userPassword, size, page).toArray(new ObjectBoundary[0]);
	}

	@Deprecated
	@GetMapping(
			path = {"/{childSystemID}/{childObjectId}/parents"},
			params = {"!size", "!page"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public ObjectBoundary[] getParentsDeprecated(
	        @PathVariable("childSystemID") String childSystemID,
	        @PathVariable("childObjectId") String childObjectId,
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true)  String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword) {
	    return this.objectService.getParents(childSystemID, childObjectId,
	            userSystemID, userEmail, userPassword).toArray(new ObjectBoundary[0]);
	}

	@GetMapping(
			path = {"/search/byAlias/{alias}"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public ObjectBoundary[] searchByAlias(
	        @PathVariable("alias") String alias,
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true)  String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword,
	        @RequestParam(name = "size", required = false, defaultValue = "20") int size,
	        @RequestParam(name = "page", required = false, defaultValue = "0") int page) {
	    return this.objectService.searchByAlias(alias, userSystemID, userEmail, userPassword, size, page)
	            .toArray(new ObjectBoundary[0]);
	}

	@GetMapping(
			path = {"/search/byAliasPattern/{pattern}"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public ObjectBoundary[] searchByAliasPattern(
	        @PathVariable("pattern") String pattern,
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true)  String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword,
	        @RequestParam(name = "size", required = false, defaultValue = "20") int size,
	        @RequestParam(name = "page", required = false, defaultValue = "0") int page) {
	    return this.objectService.searchByAliasPattern(pattern, userSystemID, userEmail, userPassword, size, page)
	            .toArray(new ObjectBoundary[0]);
	}

	@GetMapping(
			path = {"/search/byType/{type}"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public ObjectBoundary[] searchByType(
	        @PathVariable("type") String type,
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true)  String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword,
	        @RequestParam(name = "size", required = false, defaultValue = "20") int size,
	        @RequestParam(name = "page", required = false, defaultValue = "0") int page) {
	    return this.objectService.searchByType(type, userSystemID, userEmail, userPassword, size, page)
	            .toArray(new ObjectBoundary[0]);
	}

	@GetMapping(
			path = {"/search/byStatus/{status}"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public ObjectBoundary[] searchByStatus(
	        @PathVariable("status") String status,
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true)  String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword,
	        @RequestParam(name = "size", required = false, defaultValue = "20") int size,
	        @RequestParam(name = "page", required = false, defaultValue = "0") int page) {
	    return this.objectService.searchByStatus(status, userSystemID, userEmail, userPassword, size, page)
	            .toArray(new ObjectBoundary[0]);
	}

	@GetMapping(
			path = {"/search/byLocation/{lat}/{lng}/{distance}"},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public ObjectBoundary[] searchByLocation(
	        @PathVariable("lat") double lat,
	        @PathVariable("lng") double lng,
	        @PathVariable("distance") double distance,
	        @RequestParam(name = "units", required = false) String units,
	        @RequestParam(name = "userSystemID", required = true) String userSystemID,
	        @RequestParam(name = "userEmail", required = true)  String userEmail,
	        @RequestParam(name = "userPassword", required = true) String userPassword,
	        @RequestParam(name = "size", required = false, defaultValue = "20") int size,
	        @RequestParam(name = "page", required = false, defaultValue = "0") int page) {
	    return this.objectService.searchByLocation(lat, lng, distance, units,
	            userSystemID, userEmail, userPassword, size, page).toArray(new ObjectBoundary[0]);
	}
}
