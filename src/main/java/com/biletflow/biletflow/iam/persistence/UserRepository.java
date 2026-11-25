package com.biletflow.biletflow.iam.persistence;

import com.biletflow.biletflow.iam.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link User} entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    String USERS_BY_LOGIN_CACHE = "usersByLogin";

    String USERS_BY_EMAIL_CACHE = "usersByEmail";
    Optional<User> findOneByActivationKey(String activationKey);

    List<User> findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(Instant dateTime);

    Optional<User> findOneByResetKey(String resetKey);

    Optional<User> findOneByEmailIgnoreCase(String email);

    Optional<User> findOneByLogin(String login);

    @EntityGraph(attributePaths = "authorities")
    @Cacheable(cacheNames = USERS_BY_LOGIN_CACHE, unless = "#result == null")
    Optional<User> findOneWithAuthoritiesByLogin(String login);

    @EntityGraph(attributePaths = "authorities")
    @Cacheable(cacheNames = USERS_BY_EMAIL_CACHE, unless = "#result == null")
    Optional<User> findOneWithAuthoritiesByEmailIgnoreCase(String email);

    Page<User> findAllByIdNotNullAndActivatedIsTrue(Pageable pageable);

    @Query(
        """
        select u from User u
        where locate(lower(:term), lower(u.login)) > 0
           or locate(lower(:term), lower(coalesce(u.email, ''))) > 0
           or locate(lower(:term), lower(trim(concat(coalesce(u.firstName, ''), ' ', coalesce(u.lastName, ''))))) > 0
        """
    )
    Page<User> searchManagedUsers(@Param("term") String term, Pageable pageable);

    @Query(
        "select distinct u from User u join u.authorities a where a.name = :authorityName and u.activated = true and u.email is not null and u.email <> ''"
    )
    List<User> findActivatedUsersWithAuthority(@Param("authorityName") String authorityName);
}
