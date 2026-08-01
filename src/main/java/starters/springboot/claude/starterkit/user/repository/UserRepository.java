package starters.springboot.claude.starterkit.user.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import starters.springboot.claude.starterkit.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
