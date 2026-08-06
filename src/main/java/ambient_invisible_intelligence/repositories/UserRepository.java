package ambient_invisible_intelligence.repositories;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import ambient_invisible_intelligence.data.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, String> {

	List<UserEntity> findAllBy(Pageable pageable);
}
