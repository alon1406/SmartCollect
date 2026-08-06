package ambient_invisible_intelligence.repositories;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import ambient_invisible_intelligence.data.CommandEntity;

public interface CommandRepository extends JpaRepository<CommandEntity, String> {

	List<CommandEntity> findAllBy(Pageable pageable);
}
