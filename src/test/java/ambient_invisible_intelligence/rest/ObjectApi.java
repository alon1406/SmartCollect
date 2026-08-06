package ambient_invisible_intelligence.rest;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.ObjectChildIdBoundary;

public interface ObjectApi {

	@PostExchange(
			contentType = MediaType.APPLICATION_JSON_VALUE,
			accept = {MediaType.APPLICATION_JSON_VALUE})
	ObjectBoundary createObject(
			@RequestParam("userPassword") String userPassword,
			@RequestBody ObjectBoundary object);

	@PutExchange(url = "/{systemID}/{objectId}")
	void updateObject(
			@PathVariable("systemID") String systemID,
			@PathVariable("objectId") String objectId,
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestBody ObjectBoundary update);

	@GetExchange(
			url = "/{systemID}/{objectId}",
			accept = {MediaType.APPLICATION_JSON_VALUE})
	ObjectBoundary getObject(
			@PathVariable("systemID") String systemID,
			@PathVariable("objectId") String objectId,
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword);

	@GetExchange(url = "", accept = {MediaType.APPLICATION_JSON_VALUE})
	List<ObjectBoundary> getAllObjects(
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestParam("size") int size,
			@RequestParam("page") int page);

	@GetExchange(url = "", accept = {MediaType.APPLICATION_JSON_VALUE})
	List<ObjectBoundary> getAllObjectsDeprecated(
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword);

	@PutExchange(url = "/{parentSystemID}/{parentObjectId}/children")
	void bindObjects(
			@PathVariable("parentSystemID") String parentSystemID,
			@PathVariable("parentObjectId") String parentObjectId,
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestBody ObjectChildIdBoundary child);

	@GetExchange(
			url = "/{parentSystemID}/{parentObjectId}/children",
			accept = {MediaType.APPLICATION_JSON_VALUE})
	List<ObjectBoundary> getChildren(
			@PathVariable("parentSystemID") String parentSystemID,
			@PathVariable("parentObjectId") String parentObjectId,
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestParam("size") int size,
			@RequestParam("page") int page);

	@GetExchange(
			url = "/{parentSystemID}/{parentObjectId}/children",
			accept = {MediaType.APPLICATION_JSON_VALUE})
	List<ObjectBoundary> getChildrenDeprecated(
			@PathVariable("parentSystemID") String parentSystemID,
			@PathVariable("parentObjectId") String parentObjectId,
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword);

	@GetExchange(
			url = "/{childSystemID}/{childObjectId}/parents",
			accept = {MediaType.APPLICATION_JSON_VALUE})
	List<ObjectBoundary> getParents(
			@PathVariable("childSystemID") String childSystemID,
			@PathVariable("childObjectId") String childObjectId,
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestParam("size") int size,
			@RequestParam("page") int page);

	@GetExchange(
			url = "/{childSystemID}/{childObjectId}/parents",
			accept = {MediaType.APPLICATION_JSON_VALUE})
	List<ObjectBoundary> getParentsDeprecated(
			@PathVariable("childSystemID") String childSystemID,
			@PathVariable("childObjectId") String childObjectId,
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword);

	@GetExchange(
			url = "/search/byAlias/{alias}",
			accept = {MediaType.APPLICATION_JSON_VALUE})
	List<ObjectBoundary> searchByAlias(
			@PathVariable("alias") String alias,
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestParam("size") int size,
			@RequestParam("page") int page);

	@GetExchange(
			url = "/search/byAliasPattern/{pattern}",
			accept = {MediaType.APPLICATION_JSON_VALUE})
	List<ObjectBoundary> searchByAliasPattern(
			@PathVariable("pattern") String pattern,
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestParam("size") int size,
			@RequestParam("page") int page);

	@GetExchange(
			url = "/search/byType/{type}",
			accept = {MediaType.APPLICATION_JSON_VALUE})
	List<ObjectBoundary> searchByType(
			@PathVariable("type") String type,
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestParam("size") int size,
			@RequestParam("page") int page);

	@GetExchange(
			url = "/search/byStatus/{status}",
			accept = {MediaType.APPLICATION_JSON_VALUE})
	List<ObjectBoundary> searchByStatus(
			@PathVariable("status") String status,
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestParam("size") int size,
			@RequestParam("page") int page);

	@GetExchange(
			url = "/search/byLocation/{lat}/{lng}/{distance}",
			accept = {MediaType.APPLICATION_JSON_VALUE})
	List<ObjectBoundary> searchByLocation(
			@PathVariable("lat") double lat,
			@PathVariable("lng") double lng,
			@PathVariable("distance") double distance,
			@RequestParam(value = "units", required = false) String units,
			@RequestParam("userSystemID") String userSystemID,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestParam("size") int size,
			@RequestParam("page") int page);
}
