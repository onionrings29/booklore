package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<BookLoreUserEntity, Long> {

    Optional<BookLoreUserEntity> findByUsername(String username);

    Optional<BookLoreUserEntity> findByEmail(String email);

    Optional<BookLoreUserEntity> findById(Long id);

    List<BookLoreUserEntity> findAllByLibraries_Id(Long libraryId);

    /**
     * Lightweight query for authentication - excludes EAGER-loaded libraries and settings
     * Only loads: id, username, name, email, permissions (essential for auth)
     * This significantly reduces DB load and connection hold time during authentication
     */
    @EntityGraph(attributePaths = {"permissions"})
    @Query("SELECT u FROM BookLoreUserEntity u WHERE u.id = :id")
    Optional<BookLoreUserEntity> findByIdForAuthentication(@Param("id") Long id);

    /**
     * Lightweight query for OIDC authentication - excludes EAGER-loaded libraries and settings
     */
    @EntityGraph(attributePaths = {"permissions"})
    @Query("SELECT u FROM BookLoreUserEntity u WHERE u.username = :username")
    Optional<BookLoreUserEntity> findByUsernameForAuthentication(@Param("username") String username);
}
