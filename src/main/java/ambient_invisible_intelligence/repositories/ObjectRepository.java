package ambient_invisible_intelligence.repositories;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ambient_invisible_intelligence.data.ObjectEntity;

public interface ObjectRepository extends JpaRepository<ObjectEntity, String> {

	List<ObjectEntity> findAllBy(Pageable pageable);

	List<ObjectEntity> findAllByAlias(
			@Param("alias") String alias,
			Pageable pageable);

	List<ObjectEntity> findAllByAliasLike(
			@Param("pattern") String pattern,
			Pageable pageable);

	List<ObjectEntity> findAllByType(
			@Param("type") String type,
			Pageable pageable);

	List<ObjectEntity> findAllByStatus(
			@Param("status") String status,
			Pageable pageable);

	@Query("SELECT c FROM ObjectEntity p JOIN p.children c WHERE p.id = :parentId")
	List<ObjectEntity> findChildrenByParentId(
			@Param("parentId") String parentId,
			Pageable pageable);

	List<ObjectEntity> findAllByChildren_Id(
			@Param("childId") String childId,
			Pageable pageable);

	@Query("SELECT o FROM ObjectEntity o WHERE o.lat BETWEEN :minLat AND :maxLat AND o.lng BETWEEN :minLng AND :maxLng")
	List<ObjectEntity> findAllByLocationSquare(
			@Param("minLat") double minLat,
			@Param("maxLat") double maxLat,
			@Param("minLng") double minLng,
			@Param("maxLng") double maxLng,
			Pageable pageable);

	List<ObjectEntity> findAllByActive(Boolean active, Pageable pageable);

	List<ObjectEntity> findAllByAliasAndActive(String alias, Boolean active, Pageable pageable);

	List<ObjectEntity> findAllByAliasLikeAndActive(String pattern, Boolean active, Pageable pageable);

	List<ObjectEntity> findAllByTypeAndActive(String type, Boolean active, Pageable pageable);

	List<ObjectEntity> findAllByStatusAndActive(String status, Boolean active, Pageable pageable);

	@Query("SELECT c FROM ObjectEntity p JOIN p.children c WHERE p.id = :parentId AND c.active = true")
	List<ObjectEntity> findActiveChildrenByParentId(@Param("parentId") String parentId, Pageable pageable);

	List<ObjectEntity> findAllByChildren_IdAndActive(String childId, Boolean active, Pageable pageable);

	@Query("SELECT o FROM ObjectEntity o WHERE o.lat BETWEEN :minLat AND :maxLat AND o.lng BETWEEN :minLng AND :maxLng AND o.active = true")
	List<ObjectEntity> findActiveByLocationSquare(
			@Param("minLat") double minLat,
			@Param("maxLat") double maxLat,
			@Param("minLng") double minLng,
			@Param("maxLng") double maxLng,
			Pageable pageable);
}
