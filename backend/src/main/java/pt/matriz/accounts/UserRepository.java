package pt.matriz.accounts;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Owned by the accounts module; other modules use {@link AccountService}, not this repository. */
interface UserRepository extends JpaRepository<User, Long> {

    /** Email is unique ignoring case (US9). */
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);
}
